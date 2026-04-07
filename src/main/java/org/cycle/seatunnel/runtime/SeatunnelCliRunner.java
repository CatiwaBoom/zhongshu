package org.cycle.seatunnel.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.seatunnel.config.SeatunnelProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatunnelCliRunner {

    private final SeatunnelProperties properties;

    public static class CommandResult {
        private final int exitCode;
        private final String output;

        public CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getOutput() {
            return output;
        }
    }

    public Process start(Path configPath, Path logPath, String execMode, String clusterName) throws Exception {
        String home = validateHomeAndGet();

        String executable = resolveExecutable(home);
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--config");
        command.add(configPath.toAbsolutePath().toString());

        String mode = safeTrim(execMode);
        if (!mode.isEmpty()) {
            command.add("-e");
            command.add(mode);
        }

        String cn = safeTrim(clusterName);
        if ("cluster".equalsIgnoreCase(mode)) {
            if (!cn.isEmpty()) {
                command.add("-cn");
                command.add(cn);
            }
        }

        File logFile = logPath.toFile();
        File homeDir = new File(home);
        if (!homeDir.exists() || !homeDir.isDirectory()) {
            throw new IllegalStateException("seatunnel.home not found or not a directory: " + home);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(homeDir);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        pb.environment().put("SEATUNNEL_HOME", home);

        log.info("Start SeaTunnel, execMode={}, clusterName={}, configPath={}, logPath={}", mode, cnMask(cn), configPath, logPath);
        return pb.start();
    }

    public CommandResult submitAndWait(Path configPath, Path logPath, String clusterName, String jobName, Path hazelcastClientConfigPath, Consumer<String> onLine) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--config");
        args.add(configPath.toAbsolutePath().toString());
        args.add("-e");
        args.add("cluster");
        String cn = safeTrim(clusterName);
        if (!cn.isEmpty()) {
            args.add("-cn");
            args.add(cn);
        }
        String name = safeTrim(jobName);
        if (!name.isEmpty()) {
            args.add("-n");
            args.add(name);
        }

        Map<String, String> env = null;
        if (hazelcastClientConfigPath != null) {
            env = java.util.Collections.singletonMap("HAZELCAST_CLIENT_CONFIG", hazelcastClientConfigPath.toAbsolutePath().toString());
        }
        return run(args, logPath, env, onLine);
    }

    public CommandResult submitToCluster(Path configPath, Path logPath, String clusterName, String jobName) throws Exception {
        validateHomeAndGet();
        List<String> args = new ArrayList<>();
        args.add("--config");
        args.add(configPath.toAbsolutePath().toString());
        args.add("-e");
        args.add("cluster");
        String cn = safeTrim(clusterName);
        if (!cn.isEmpty()) {
            args.add("-cn");
            args.add(cn);
        }
        String name = safeTrim(jobName);
        if (!name.isEmpty()) {
            args.add("-n");
            args.add(name);
        }
        args.add("--async");
        return run(args, logPath, null, null);
    }

    public CommandResult queryJob(String jobId, Path logPath, String clusterName) throws Exception {
        validateHomeAndGet();
        List<String> args = new ArrayList<>();
        args.add("-j");
        args.add(jobId);
        String cn = safeTrim(clusterName);
        if (!cn.isEmpty()) {
            args.add("-cn");
            args.add(cn);
        }
        return run(args, logPath, null, null);
    }

    public CommandResult cancelJob(String jobId, Path logPath, String clusterName) throws Exception {
        validateHomeAndGet();
        List<String> args = new ArrayList<>();
        args.add("-can");
        args.add(jobId);
        String cn = safeTrim(clusterName);
        if (!cn.isEmpty()) {
            args.add("-cn");
            args.add(cn);
        }
        return run(args, logPath, null, null);
    }

    private CommandResult run(List<String> args, Path logPath, Map<String, String> extraEnv, Consumer<String> onLine) throws Exception {
        String home = validateHomeAndGet();

        File homeDir = new File(home);
        File executableFile = new File(resolveExecutable(home));
        if (!executableFile.exists() || !executableFile.isFile()) {
            throw new IllegalStateException("SeaTunnel executable not found: " + executableFile.getAbsolutePath());
        }

        String executable = resolveExecutable(home);
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(homeDir);
        pb.redirectErrorStream(true);
        pb.environment().put("SEATUNNEL_HOME", home);
        if (extraEnv != null && !extraEnv.isEmpty()) {
            pb.environment().putAll(extraEnv);
        }

        Process process = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = logPath == null ? null :
                     new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(logPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
                if (onLine != null) {
                    onLine.accept(line);
                }
                if (writer != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            if (writer != null) {
                writer.flush();
            }
        } finally {
            int exit = process.waitFor();
            return new CommandResult(exit, out.toString());
        }
    }

    public void validateHome() {
        validateHomeAndGet();
    }

    private String validateHomeAndGet() {
        String home = safeTrim(properties.getHome());
        if (home.isEmpty()) {
            home = safeTrim(System.getenv("SEATUNNEL_HOME"));
        }
        if (home.isEmpty()) {
            throw new IllegalStateException("seatunnel.home is blank, please set environment variable SEATUNNEL_HOME or config key seatunnel.home");
        }
        File homeDir = new File(home);
        if (!homeDir.exists() || !homeDir.isDirectory()) {
            throw new IllegalStateException("seatunnel.home not found or not a directory: " + home);
        }
        File executableFile = new File(resolveExecutable(home));
        if (!executableFile.exists() || !executableFile.isFile()) {
            throw new IllegalStateException("SeaTunnel executable not found: " + executableFile.getAbsolutePath());
        }
        return home;
    }

    private String resolveExecutable(String home) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String sep = File.separator;
        if (os.contains("win")) {
            String cmd = home + sep + "bin" + sep + "seatunnel.cmd";
            if (new File(cmd).exists()) {
                return cmd;
            }
            return home + sep + "bin" + sep + "seatunnel.bat";
        }
        return home + sep + "bin" + sep + "seatunnel.sh";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String cnMask(String clusterName) {
        String cn = safeTrim(clusterName);
        if (cn.isEmpty()) {
            return "";
        }
        if (cn.length() <= 3) {
            return "***";
        }
        return cn.substring(0, 2) + "***";
    }
}
