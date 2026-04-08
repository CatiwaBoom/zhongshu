package org.cycle.seatunnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.seatunnel.config.SeatunnelProperties;
import org.cycle.seatunnel.entity.SeatunnelExecutionEntity;
import org.cycle.seatunnel.entity.SeatunnelPipelineEntity;
import org.cycle.seatunnel.enums.SeatunnelExecutionStatus;
import org.cycle.seatunnel.mapper.SeatunnelExecutionMapper;
import org.cycle.seatunnel.runtime.SeatunnelCliRunner;
import org.cycle.seatunnel.service.SeatunnelExecutionService;
import org.cycle.seatunnel.service.SeatunnelPipelineService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatunnelExecutionServiceImpl extends ServiceImpl<SeatunnelExecutionMapper, SeatunnelExecutionEntity> implements SeatunnelExecutionService {

    private static final Pattern JOB_ID_PATTERN = Pattern.compile("(?i)(?:job\\s*id|jobid)\\s*[:=]\\s*([0-9a-zA-Z\\-]+)");
    private static final Pattern STATUS_PATTERN = Pattern.compile("(?i)\\b(RUNNING|FINISHED|FAILED|CANCELED|CANCELLED)\\b");

    private final SeatunnelPipelineService pipelineService;
    private final SeatunnelCliRunner cliRunner;
    private final SeatunnelProperties properties;

    @Resource(name = "seatunnelExecutor")
    private Executor executor;

    private final Map<String, Process> runningProcess = new ConcurrentHashMap<>();

    @Override
    public SeatunnelExecutionEntity start(String pipelineId) {
        SeatunnelPipelineEntity pipeline = pipelineService.getById(pipelineId);
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline not found: " + pipelineId);
        }
        if (pipeline.getStatus() != null && pipeline.getStatus() == 0) {
            throw new IllegalStateException("pipeline is disabled: " + pipelineId);
        }

        String content = safeTrim(pipeline.getConfigContent());
        if (content.isEmpty()) {
            throw new IllegalArgumentException("pipeline configContent is blank");
        }

        cliRunner.validateHome();

        String executionId = IdWorker.getIdStr();
        String format = normalizeFormat(pipeline.getConfigFormat());
        Path runDir = resolveRunDir(pipelineId, executionId);
        Path configPath = runDir.resolve("job." + ("json".equals(format) ? "json" : "conf"));
        Path logPath = runDir.resolve("seatunnel.log");

        try {
            Files.createDirectories(runDir);
            Files.write(configPath, content.getBytes(StandardCharsets.UTF_8));
            if (!Files.exists(logPath)) {
                Files.createFile(logPath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("prepare run files failed: " + e.getMessage(), e);
        }

        SeatunnelExecutionEntity execution = new SeatunnelExecutionEntity();
        execution.setId(executionId);
        execution.setPipelineId(pipelineId);
        execution.setStatus(SeatunnelExecutionStatus.RUNNING.name());
        execution.setStartedAt(Timestamp.from(Instant.now()));
        execution.setLogPath(logPath.toAbsolutePath().toString());
        execution.setConfigPath(configPath.toAbsolutePath().toString());
        save(execution);

        CompletableFuture.runAsync(() -> runAsync(executionId), executor);
        return execution;
    }

    private void runAsync(String executionId) {
        SeatunnelExecutionEntity execution = getById(executionId);
        if (execution == null) {
            return;
        }

        SeatunnelPipelineEntity pipeline = pipelineService.getById(execution.getPipelineId());
        if (pipeline == null) {
            markFailed(executionId, "pipeline not found: " + execution.getPipelineId(), null);
            return;
        }

        Path configPath = Paths.get(execution.getConfigPath());
        Path logPath = Paths.get(execution.getLogPath());
        String execMode = pickExecMode(pipeline);
        String clusterName = pickClusterName(pipeline);

        if ("cluster".equalsIgnoreCase(execMode)) {
            runOnCluster(executionId, pipeline, configPath, logPath, clusterName);
            return;
        }

        Process process = null;
        try {
            process = cliRunner.start(configPath, logPath, execMode, clusterName);
            runningProcess.put(executionId, process);
            int exit = process.waitFor();
            String jobId = parseJobId(logPath);
            if (exit == 0) {
                SeatunnelExecutionEntity update = new SeatunnelExecutionEntity();
                update.setId(executionId);
                update.setStatus(SeatunnelExecutionStatus.SUCCESS.name());
                update.setExitCode(exit);
                update.setFinishedAt(Timestamp.from(Instant.now()));
                update.setSeatunnelJobId(jobId);
                updateById(update);
            } else {
                markFailed(executionId, "seatunnel exited with code " + exit, jobId);
                SeatunnelExecutionEntity update = new SeatunnelExecutionEntity();
                update.setId(executionId);
                update.setExitCode(exit);
                update.setFinishedAt(Timestamp.from(Instant.now()));
                update.setSeatunnelJobId(jobId);
                updateById(update);
            }
        } catch (Exception e) {
            markFailed(executionId, safeTrim(e.getMessage()).isEmpty() ? "run failed" : e.getMessage(), null);
            SeatunnelExecutionEntity update = new SeatunnelExecutionEntity();
            update.setId(executionId);
            update.setFinishedAt(Timestamp.from(Instant.now()));
            updateById(update);
            log.error("SeaTunnel run failed, executionId={}", executionId, e);
        } finally {
            if (process != null) {
                runningProcess.remove(executionId);
            }
        }
    }

    private void runOnCluster(String executionId, SeatunnelPipelineEntity pipeline, Path configPath, Path logPath, String clusterName) {
        try {
            Path hazelcastClientConfig = writeHazelcastClientConfig(resolveRunDir(pipeline.getId(), executionId), pickClusterName(pipeline), pickClientAddress(), logPath);

            final String[] jobIdHolder = new String[]{null};
            SeatunnelCliRunner.CommandResult result = cliRunner.submitAndWait(
                    configPath,
                    logPath,
                    clusterName,
                    pipeline.getName(),
                    hazelcastClientConfig,
                    line -> {
                        if (jobIdHolder[0] != null) {
                            return;
                        }
                        String jobId = extractJobId(line);
                        if (jobId != null) {
                            jobIdHolder[0] = jobId;
                            SeatunnelExecutionEntity u = new SeatunnelExecutionEntity();
                            u.setId(executionId);
                            u.setSeatunnelJobId(jobId);
                            updateById(u);
                        }
                    }
            );

            String jobId = jobIdHolder[0];
            if (jobId == null) {
                jobId = extractJobId(result.getOutput());
            }
            if (jobId == null) {
                jobId = parseJobId(logPath);
            }

            SeatunnelExecutionEntity finish = new SeatunnelExecutionEntity();
            finish.setId(executionId);
            finish.setExitCode(result.getExitCode());
            finish.setSeatunnelJobId(jobId);
            finish.setFinishedAt(Timestamp.from(Instant.now()));

            SeatunnelExecutionEntity current = getById(executionId);
            if (current != null && SeatunnelExecutionStatus.CANCELLED.name().equalsIgnoreCase(current.getStatus())) {
                updateById(finish);
                return;
            }

            if (result.getExitCode() == 0) {
                finish.setStatus(SeatunnelExecutionStatus.SUCCESS.name());
                updateById(finish);
                return;
            }

            finish.setStatus(SeatunnelExecutionStatus.FAILED.name());
            finish.setErrorMessage("seatunnel job failed, exitCode=" + result.getExitCode());
            updateById(finish);
        } catch (Exception e) {
            markFailed(executionId, safeTrim(e.getMessage()).isEmpty() ? "submit failed" : e.getMessage(), null);
            SeatunnelExecutionEntity finish = new SeatunnelExecutionEntity();
            finish.setId(executionId);
            finish.setFinishedAt(Timestamp.from(Instant.now()));
            updateById(finish);
            log.error("SeaTunnel cluster submit failed, executionId={}", executionId, e);
        }
    }

    private void pollClusterStatus(String executionId, String jobId, Path logPath, String clusterName) {
        while (true) {
            SeatunnelExecutionEntity execution = getById(executionId);
            if (execution == null) {
                return;
            }
            if (SeatunnelExecutionStatus.CANCELLED.name().equalsIgnoreCase(execution.getStatus())) {
                return;
            }
            if (!SeatunnelExecutionStatus.RUNNING.name().equalsIgnoreCase(execution.getStatus())) {
                return;
            }

            try {
                SeatunnelCliRunner.CommandResult status = cliRunner.queryJob(jobId, logPath, clusterName);
                String st = extractStatus(status.getOutput());
                if (st == null) {
                    Thread.sleep(3000);
                    continue;
                }

                String upper = st.toUpperCase();
                if ("RUNNING".equals(upper)) {
                    Thread.sleep(3000);
                    continue;
                }

                SeatunnelExecutionEntity finish = new SeatunnelExecutionEntity();
                finish.setId(executionId);
                finish.setFinishedAt(Timestamp.from(Instant.now()));
                finish.setExitCode(status.getExitCode());

                if ("FINISHED".equals(upper)) {
                    finish.setStatus(SeatunnelExecutionStatus.SUCCESS.name());
                    updateById(finish);
                    return;
                }

                if ("CANCELED".equals(upper) || "CANCELLED".equals(upper)) {
                    finish.setStatus(SeatunnelExecutionStatus.CANCELLED.name());
                    updateById(finish);
                    return;
                }

                if ("FAILED".equals(upper)) {
                    finish.setStatus(SeatunnelExecutionStatus.FAILED.name());
                    finish.setErrorMessage("cluster job failed");
                    updateById(finish);
                    return;
                }
            } catch (Exception e) {
                log.warn("query cluster job status failed, executionId={}, jobId={}", executionId, jobId, e);
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                return;
            }
        }
    }

    @Override
    public boolean stop(String executionId) {
        SeatunnelExecutionEntity execution = getById(executionId);
        if (execution == null) {
            return false;
        }
        if (!SeatunnelExecutionStatus.RUNNING.name().equalsIgnoreCase(execution.getStatus())) {
            return false;
        }

        Process process = runningProcess.get(executionId);
        String jobId = safeTrim(execution.getSeatunnelJobId());
        SeatunnelPipelineEntity pipeline = pipelineService.getById(execution.getPipelineId());
        if (pipeline == null) {
            return false;
        }
        String execMode = pickExecMode(pipeline);

        if ("cluster".equalsIgnoreCase(execMode)) {
            if (jobId.isEmpty()) {
                jobId = parseJobId(Paths.get(execution.getLogPath()));
            }
            if (!jobId.isEmpty()) {
                Path logPath = Paths.get(execution.getLogPath());
                String clusterName = pickClusterName(pipeline);
                try {
                    cliRunner.cancelJob(jobId, logPath, clusterName);
                } catch (Exception e) {
                    log.error("cancel cluster job failed, executionId={}, jobId={}", executionId, jobId, e);
                }
            }
        }

        if (process != null) {
            try {
                process.destroy();
                process.destroyForcibly();
            } catch (Exception ignored) {
            }
        }

        SeatunnelExecutionEntity update = new SeatunnelExecutionEntity();
        update.setId(executionId);
        update.setStatus(SeatunnelExecutionStatus.CANCELLED.name());
        update.setFinishedAt(Timestamp.from(Instant.now()));
        updateById(update);
        runningProcess.remove(executionId);
        return true;
    }

    @Override
    public String tailLog(String executionId, int maxLines) {
        SeatunnelExecutionEntity execution = getById(executionId);
        if (execution == null) {
            return "";
        }
        String path = safeTrim(execution.getLogPath());
        if (path.isEmpty()) {
            return "";
        }
        Path logPath = Paths.get(path);
        if (!Files.exists(logPath)) {
            return "";
        }
        int lines = Math.max(1, Math.min(maxLines, 2000));
        Deque<String> buf = new ArrayDeque<>(lines + 1);
        try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (buf.size() == lines) {
                    buf.removeFirst();
                }
                buf.addLast(line);
            }
        } catch (IOException e) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : buf) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private void markFailed(String executionId, String error, String jobId) {
        SeatunnelExecutionEntity update = new SeatunnelExecutionEntity();
        update.setId(executionId);
        update.setStatus(SeatunnelExecutionStatus.FAILED.name());
        update.setErrorMessage(truncate(error, 1000));
        update.setSeatunnelJobId(jobId);
        updateById(update);
    }

    private Path resolveRunDir(String pipelineId, String executionId) {
        String base = safeTrim(properties.getWorkDir());
        if (base.isEmpty()) {
            base = Paths.get(System.getProperty("user.dir"), "data", "seatunnel").toString();
        }
        return Paths.get(base, "runs", pipelineId, executionId);
    }

    private String pickExecMode(SeatunnelPipelineEntity pipeline) {
        String mode = safeTrim(pipeline.getExecMode());
        if (!mode.isEmpty()) {
            return mode;
        }
        return safeTrim(properties.getExecMode());
    }

    private String pickClusterName(SeatunnelPipelineEntity pipeline) {
        String cn = safeTrim(pipeline.getClusterName());
        if (!cn.isEmpty()) {
            return cn;
        }
        return safeTrim(properties.getClusterName());
    }

    private String pickClientAddress() {
        String addr = safeTrim(properties.getClientAddress());
        if (!addr.isEmpty()) {
            return addr;
        }
        return "localhost:5801";
    }

    private String normalizeFormat(String format) {
        String f = safeTrim(format).toLowerCase();
        if ("json".equals(f)) {
            return "json";
        }
        return "hocon";
    }

    private String parseJobId(Path logPath) {
        if (!Files.exists(logPath)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
            String line;
            int limit = 2000;
            while ((line = reader.readLine()) != null && limit-- > 0) {
                Matcher m = JOB_ID_PATTERN.matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private String extractJobId(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        Matcher m = JOB_ID_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractStatus(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        Matcher m = STATUS_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private Path writeHazelcastClientConfig(Path runDir, String clusterName, String address, Path logPath) throws IOException {
        Files.createDirectories(runDir);
        Path path = runDir.resolve("hazelcast-client.yaml");
        String yaml = "hazelcast-client:\n" +
                "  cluster-name: " + clusterName + "\n" +
                "  properties:\n" +
                "    hazelcast.logging.type: log4j2\n" +
                "  connection-strategy:\n" +
                "    connection-retry:\n" +
                "      cluster-connect-timeout-millis: 3000\n" +
                "  network:\n" +
                "    cluster-members:\n" +
                "      - " + address + "\n";
        Files.write(path, yaml.getBytes(StandardCharsets.UTF_8));
        if (logPath != null) {
            Files.write(logPath, ("HAZELCAST_CLIENT_CONFIG=" + path.toAbsolutePath() + "\n").getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
        return path;
    }
}
