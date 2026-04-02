package org.cycle.workflow.controller;

import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.workflow.dto.PublishDefinitionRequest;
import org.cycle.workflow.dto.StartProcessRequest;
import org.cycle.workflow.dto.TaskActionRequest;
import org.cycle.workflow.dto.WorkflowActionResult;
import org.cycle.workflow.entity.ProcessInstanceEntity;
import org.cycle.workflow.service.WorkflowEngineService;
import org.cycle.workflow.entity.ProcessDefinitionEntity;
import org.cycle.workflow.mapper.ProcessDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;
import java.util.HashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 流程引擎对外接口：发布定义、发起流程、处理任务动作、查询实例。
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
public class WorkflowController extends BaseController {

    @Resource
    private WorkflowEngineService workflowEngineService;

    @Resource
    private ProcessDefinitionMapper processDefinitionMapper;

    /**
     * 发布流程定义，新版本会自动递增并标记为最新版本。
     */
    @PostMapping("/definition/publish")
    public Result<String> publishDefinition(@RequestBody PublishDefinitionRequest request) {
        try {
            return success(workflowEngineService.publishDefinition(request), "流程定义发布成功");
        } catch (Exception e) {
            log.error("发布流程定义失败", e);
            return fail(500, "发布流程定义失败: " + e.getMessage());
        }
    }

    /**
     * 发起流程实例，按定义首节点生成待办任务。
     */
    @PostMapping("/instance/start")
    public Result<WorkflowActionResult> start(@RequestBody StartProcessRequest request) {
        try {
            return success(workflowEngineService.startProcess(request), "流程发起成功");
        } catch (Exception e) {
            log.error("发起流程失败", e);
            return fail(500, "发起流程失败: " + e.getMessage());
        }
    }

    /**
     * 执行任务动作：APPROVE/REJECT/TRANSFER/WITHDRAW/TERMINATE。
     */
    @PostMapping("/task/action")
    public Result<WorkflowActionResult> action(@RequestBody TaskActionRequest request) {
        try {
            return success(workflowEngineService.handleTaskAction(request), "流程动作执行成功");
        } catch (Exception e) {
            log.error("执行流程动作失败", e);
            return fail(500, "执行流程动作失败: " + e.getMessage());
        }
    }

    /**
     * 按实例ID查询当前流程状态与所在节点。
     */
    @GetMapping("/instance/{id}")
    public Result<ProcessInstanceEntity> getInstance(@PathVariable("id") String id) {
        try {
            ProcessInstanceEntity instance = workflowEngineService.getInstance(id);
            if (instance == null) {
                return fail(404, "流程实例不存在");
            }
            return success(instance, "查询成功");
        } catch (Exception e) {
            log.error("查询流程实例失败", e);
            return fail(500, "查询流程实例失败: " + e.getMessage());
        }
    }

    /**
     * 查询流程定义列表（支持按关键字、状态、是否仅最新过滤）
     */
    @GetMapping("/definitions")
    public Result<Map<String, Object>> listDefinitions(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "latestOnly", required = false) Integer latestOnly,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        try {
            QueryWrapper<ProcessDefinitionEntity> wrapper = new QueryWrapper<>();
            if (status != null) {
                wrapper.eq("status", status);
            }
            if (latestOnly != null) {
                wrapper.eq("is_latest", latestOnly);
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim();
                wrapper.and(w -> w.like("code", kw).or().like("name", kw).or().like("remark", kw));
            }
            wrapper.orderByDesc("created_at");

            Page<ProcessDefinitionEntity> pg = new Page<>(page, size);
            Page<ProcessDefinitionEntity> result = processDefinitionMapper.selectPage(pg, wrapper);

            Map<String, Object> resp = new HashMap<>();
            resp.put("total", result.getTotal());
            resp.put("records", result.getRecords());
            return success(resp, "查询成功");
        } catch (Exception e) {
            log.error("查询流程定义失败", e);
            return fail(500, "查询流程定义失败: " + e.getMessage());
        }
    }

    /**
     * 获取下一个流程定义编码（前端可用于预览）。
     */
    @GetMapping("/definition/next-code")
    public Result<String> getNextDefinitionCode() {
        try {
            String code = workflowEngineService.getNextDefinitionCode();
            return success(code, "获取编码成功");
        } catch (Exception e) {
            log.error("获取下一个流程编码失败", e);
            return fail(500, "获取下一个流程编码失败: " + e.getMessage());
        }
    }
}
