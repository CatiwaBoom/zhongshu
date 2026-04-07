package org.cycle.seatunnel.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.seatunnel.entity.DataSyncTaskEntity;
import org.cycle.seatunnel.entity.SeatunnelExecutionEntity;
import org.cycle.seatunnel.service.DataSyncTaskService;
import org.cycle.seatunnel.service.SeatunnelExecutionService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/seatunnel/datasync")
public class DataSyncTaskController extends BaseController {

    @Resource
    private DataSyncTaskService taskService;

    @Resource
    private SeatunnelExecutionService executionService;

    @GetMapping("/list")
    public Result<List<DataSyncTaskEntity>> list() {
        try {
            QueryWrapper<DataSyncTaskEntity> qw = new QueryWrapper<>();
            qw.orderByDesc("UPDATED_AT");
            return success(taskService.list(qw), "查询成功");
        } catch (Exception e) {
            log.error("查询 datasync 列表失败", e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<DataSyncTaskEntity> get(@PathVariable("id") String id) {
        try {
            DataSyncTaskEntity task = taskService.getById(id);
            if (task == null) {
                return fail(404, "任务不存在");
            }
            return success(task, "查询成功");
        } catch (Exception e) {
            log.error("查询 datasync 失败, id={}", id, e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/add")
    public Result<DataSyncTaskEntity> add(@RequestBody DataSyncTaskEntity task) {
        try {
            DataSyncTaskEntity created = taskService.createTask(task);
            return success(created, "新增成功");
        } catch (Exception e) {
            log.error("新增 datasync 失败", e);
            return fail(500, "新增失败: " + e.getMessage());
        }
    }

    @PostMapping("/update/{id}")
    public Result<DataSyncTaskEntity> update(@PathVariable("id") String id, @RequestBody DataSyncTaskEntity task) {
        try {
            DataSyncTaskEntity updated = taskService.updateTask(id, task);
            return success(updated, "更新成功");
        } catch (Exception e) {
            log.error("更新 datasync 失败, id={}", id, e);
            return fail(500, "更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") String id) {
        try {
            boolean ok = taskService.removeById(id);
            if (!ok) {
                return fail(500, "删除失败");
            }
            return success(null, "删除成功");
        } catch (Exception e) {
            log.error("删除 datasync 失败, id={}", id, e);
            return fail(500, "删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/config")
    public Result<String> config(@PathVariable("id") String id) {
        try {
            String conf = taskService.generateConfig(id);
            return success(maskPassword(conf), "查询成功");
        } catch (Exception e) {
            log.error("生成配置失败, id={}", id, e);
            return fail(500, "生成配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/run/{id}")
    public Result<SeatunnelExecutionEntity> run(@PathVariable("id") String id) {
        try {
            DataSyncTaskEntity task = taskService.getById(id);
            if (task == null) {
                return fail(404, "任务不存在");
            }
            String pipelineId = safeTrim(task.getPipelineId());
            if (pipelineId.isEmpty()) {
                return fail(400, "任务未生成 pipeline");
            }
            taskService.refreshPipelineConfig(id);
            SeatunnelExecutionEntity execution = executionService.start(pipelineId);
            return success(execution, "已触发运行");
        } catch (Exception e) {
            log.error("触发 datasync 运行失败, id={}", id, e);
            return fail(500, "运行失败: " + e.getMessage());
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String maskPassword(String conf) {
        if (conf == null || conf.isEmpty()) {
            return conf;
        }
        return conf.replaceAll("(?i)(password\\s*=\\s*\")([^\"]*)(\")", "$1******$3");
    }
}

