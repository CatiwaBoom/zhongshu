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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 流程引擎对外接口：发布定义、发起流程、处理任务动作、查询实例。
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
public class WorkflowController extends BaseController {

    @Resource
    private WorkflowEngineService workflowEngineService;

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
