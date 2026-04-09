package org.cycle.workflow.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.workflow.dto.PublishWorkflowDefinitionRequest;
import org.cycle.workflow.dto.SaveWorkflowDesignRequest;
import org.cycle.workflow.dto.UpdateWorkflowDefinitionRequest;
import org.cycle.workflow.dto.WorkflowDesignResponse;
import org.cycle.workflow.entity.WorkflowDefinitionEntity;
import org.cycle.workflow.service.WorkflowDefinitionService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WorkflowDefinitionController extends BaseController {

    private final WorkflowDefinitionService workflowDefinitionService;

    @GetMapping("/workflow/definitions")
    public Result<Page<WorkflowDefinitionEntity>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        try {
            // 与用户管理页统一：列表接口直接返回 Page，前端读取 records/total
            return success(workflowDefinitionService.listDefinitions(keyword, page, size), "查询成功");
        } catch (Exception e) {
            log.error("查询流程定义失败", e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/workflow/definition/next-code")
    public Result<Map<String, String>> nextCode() {
        Map<String, String> data = new HashMap<>();
        data.put("code", workflowDefinitionService.nextCode());
        return success(data, "查询成功");
    }

    @PostMapping("/workflow/definition/publish")
    public Result<WorkflowDefinitionEntity> publish(@RequestBody PublishWorkflowDefinitionRequest request) {
        try {
            return success(workflowDefinitionService.publish(request), "发布成功");
        } catch (Exception e) {
            log.error("发布流程定义失败", e);
            return fail(500, "发布失败: " + e.getMessage());
        }
    }

    @PutMapping("/workflow/definition/{id}")
    public Result<WorkflowDefinitionEntity> update(@PathVariable("id") String id,
                                                   @RequestBody UpdateWorkflowDefinitionRequest request) {
        try {
            return success(workflowDefinitionService.updateDefinition(id, request), "更新成功");
        } catch (Exception e) {
            log.error("更新流程定义失败, id={}", id, e);
            return fail(500, "更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/workflow/definition/{id}")
    public Result<Void> delete(@PathVariable("id") String id) {
        try {
            boolean ok = workflowDefinitionService.removeDefinition(id);
            if (!ok) {
                return fail(500, "删除失败");
            }
            return success(null, "删除成功");
        } catch (Exception e) {
            log.error("删除流程定义失败, id={}", id, e);
            return fail(500, "删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/workflow/definition/{id}/design")
    public Result<WorkflowDesignResponse> saveDesign(@PathVariable("id") String id,
                                                     @RequestBody SaveWorkflowDesignRequest request) {
        try {
            return success(workflowDefinitionService.saveDesign(id, request), "保存成功");
        } catch (Exception e) {
            log.error("保存流程设计失败, id={}", id, e);
            return fail(500, "保存失败: " + e.getMessage());
        }
    }

    @GetMapping("/workflow/definition/{id}/design")
    public Result<WorkflowDesignResponse> getDesign(@PathVariable("id") String id) {
        try {
            return success(workflowDefinitionService.getDesign(id), "查询成功");
        } catch (Exception e) {
            log.error("查询流程设计失败, id={}", id, e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/workflow/definition/{id}/node/{nodeId}")
    public Result<WorkflowDesignResponse> deleteNode(@PathVariable("id") String id,
                                                     @PathVariable("nodeId") String nodeId) {
        try {
            return success(workflowDefinitionService.deleteNode(id, nodeId), "删除节点成功");
        } catch (Exception e) {
            log.error("删除流程节点失败, id={}, nodeId={}", id, nodeId, e);
            return fail(500, "删除节点失败: " + e.getMessage());
        }
    }
}

