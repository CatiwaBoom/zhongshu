package org.cycle.seatunnel.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.seatunnel.config.SeatunnelProperties;
import org.cycle.seatunnel.entity.SeatunnelExecutionEntity;
import org.cycle.seatunnel.entity.SeatunnelPipelineEntity;
import org.cycle.seatunnel.runtime.SeatunnelCliRunner;
import org.cycle.seatunnel.service.SeatunnelExecutionService;
import org.cycle.seatunnel.service.SeatunnelPipelineService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/seatunnel")
public class SeatunnelController extends BaseController {

    @Resource
    private SeatunnelPipelineService pipelineService;

    @Resource
    private SeatunnelExecutionService executionService;

    @Resource
    private SeatunnelProperties seatunnelProperties;

    @Resource
    private SeatunnelCliRunner cliRunner;

    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        String propHome = safeTrim(seatunnelProperties.getHome());
        String envHome = safeTrim(System.getenv("SEATUNNEL_HOME"));
        String effectiveHome = !propHome.isEmpty() ? propHome : envHome;
        data.put("seatunnel.home", propHome);
        data.put("env.SEATUNNEL_HOME", envHome);
        data.put("effectiveHome", effectiveHome);
        data.put("seatunnel.work-dir", safeTrim(seatunnelProperties.getWorkDir()));
        data.put("seatunnel.exec-mode", safeTrim(seatunnelProperties.getExecMode()));
        data.put("seatunnel.cluster-name", safeTrim(seatunnelProperties.getClusterName()));

        if (!effectiveHome.isEmpty()) {
            File homeDir = new File(effectiveHome);
            data.put("homeExists", homeDir.exists() && homeDir.isDirectory());
            String exe = resolveExecutable(effectiveHome);
            File exeFile = new File(exe);
            data.put("executablePath", exeFile.getAbsolutePath());
            data.put("executableExists", exeFile.exists() && exeFile.isFile());
        } else {
            data.put("homeExists", false);
            data.put("executablePath", "");
            data.put("executableExists", false);
        }

        try {
            cliRunner.validateHome();
            data.put("validate", "OK");
        } catch (Exception e) {
            data.put("validate", safeTrim(e.getMessage()));
        }

        return success(data, "查询成功");
    }

    @GetMapping("/pipeline/list")
    public Result<List<SeatunnelPipelineEntity>> listPipelines() {
        try {
            QueryWrapper<SeatunnelPipelineEntity> qw = new QueryWrapper<>();
            qw.orderByDesc("UPDATED_AT");
            return success(pipelineService.list(qw), "查询成功");
        } catch (Exception e) {
            log.error("查询 pipeline 列表失败", e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/pipeline/{id}")
    public Result<SeatunnelPipelineEntity> getPipeline(@PathVariable("id") String id) {
        try {
            SeatunnelPipelineEntity pipeline = pipelineService.getById(id);
            if (pipeline == null) {
                return fail(404, "任务不存在");
            }
            return success(pipeline, "查询成功");
        } catch (Exception e) {
            log.error("查询 pipeline 失败, id={}", id, e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/pipeline/add")
    public Result<SeatunnelPipelineEntity> addPipeline(@RequestBody SeatunnelPipelineEntity pipeline) {
        try {
            if (pipeline.getStatus() == null) {
                pipeline.setStatus(1);
            }
            if (pipeline.getConfigFormat() == null || pipeline.getConfigFormat().trim().isEmpty()) {
                pipeline.setConfigFormat("hocon");
            }
            if (pipeline.getExecMode() == null || pipeline.getExecMode().trim().isEmpty()) {
                pipeline.setExecMode("local");
            }
            boolean ok = pipelineService.save(pipeline);
            if (!ok) {
                return fail(500, "新增失败");
            }
            return success(pipeline, "新增成功");
        } catch (Exception e) {
            log.error("新增 pipeline 失败", e);
            return fail(500, "新增失败: " + e.getMessage());
        }
    }

    @PostMapping("/pipeline/update/{id}")
    public Result<Void> updatePipeline(@PathVariable("id") String id, @RequestBody SeatunnelPipelineEntity pipeline) {
        try {
            pipeline.setId(id);
            boolean ok = pipelineService.updateById(pipeline);
            if (!ok) {
                return fail(500, "更新失败");
            }
            return success(null, "更新成功");
        } catch (Exception e) {
            log.error("更新 pipeline 失败, id={}", id, e);
            return fail(500, "更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/pipeline/delete/{id}")
    public Result<Void> deletePipeline(@PathVariable("id") String id) {
        try {
            boolean ok = pipelineService.removeById(id);
            if (!ok) {
                return fail(500, "删除失败");
            }
            return success(null, "删除成功");
        } catch (Exception e) {
            log.error("删除 pipeline 失败, id={}", id, e);
            return fail(500, "删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/pipeline/run/{id}")
    public Result<SeatunnelExecutionEntity> runPipeline(@PathVariable("id") String id) {
        try {
            SeatunnelExecutionEntity execution = executionService.start(id);
            return success(execution, "已触发运行");
        } catch (Exception e) {
            log.error("触发 pipeline 运行失败, id={}", id, e);
            return fail(500, "运行失败: " + e.getMessage());
        }
    }

    @GetMapping("/execution/{id}")
    public Result<SeatunnelExecutionEntity> getExecution(@PathVariable("id") String id) {
        try {
            SeatunnelExecutionEntity execution = executionService.getById(id);
            if (execution == null) {
                return fail(404, "运行记录不存在");
            }
            return success(execution, "查询成功");
        } catch (Exception e) {
            log.error("查询 execution 失败, id={}", id, e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/execution/{id}/log")
    public Result<String> tailLog(@PathVariable("id") String id, @RequestParam(value = "lines", defaultValue = "200") int lines) {
        try {
            return success(executionService.tailLog(id, lines), "查询成功");
        } catch (Exception e) {
            log.error("读取日志失败, id={}", id, e);
            return fail(500, "读取日志失败: " + e.getMessage());
        }
    }

    @PostMapping("/execution/stop/{id}")
    public Result<Void> stopExecution(@PathVariable("id") String id) {
        try {
            boolean ok = executionService.stop(id);
            if (!ok) {
                return fail(400, "停止失败或任务不在运行中");
            }
            return success(null, "停止成功");
        } catch (Exception e) {
            log.error("停止 execution 失败, id={}", id, e);
            return fail(500, "停止失败: " + e.getMessage());
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
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
}
