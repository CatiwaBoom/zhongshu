package org.cycle.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.cycle.workflow.dto.PublishWorkflowDefinitionRequest;
import org.cycle.workflow.dto.SaveWorkflowDesignRequest;
import org.cycle.workflow.dto.UpdateWorkflowDefinitionRequest;
import org.cycle.workflow.dto.WorkflowDesignResponse;
import org.cycle.workflow.entity.WorkflowDefinitionDesignEntity;
import org.cycle.workflow.entity.WorkflowDefinitionEntity;
import org.cycle.workflow.mapper.WorkflowDefinitionDesignMapper;
import org.cycle.workflow.mapper.WorkflowDefinitionMapper;
import org.cycle.workflow.service.WorkflowDefinitionService;
import org.cycle.workflow.util.WorkflowDesignJsonHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowDefinitionServiceImpl extends ServiceImpl<WorkflowDefinitionMapper, WorkflowDefinitionEntity>
        implements WorkflowDefinitionService {

    private final WorkflowDefinitionDesignMapper workflowDefinitionDesignMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<WorkflowDefinitionEntity> listDefinitions(String keyword) {
        QueryWrapper<WorkflowDefinitionEntity> qw = new QueryWrapper<>();
        String safeKeyword = safeTrim(keyword);
        if (!safeKeyword.isEmpty()) {
            qw.and(w -> w.like("NAME", safeKeyword).or().like("CODE", safeKeyword));
        }
        qw.orderByDesc("UPDATED_AT");
        return list(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinitionEntity publish(PublishWorkflowDefinitionRequest request) {
        String name = safeTrim(request.getName());
        if (name.isEmpty()) {
            throw new IllegalArgumentException("流程名称不能为空");
        }
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setCode(resolveCode(request.getCode()));
        entity.setName(name);
        entity.setDescription(emptyToNull(request.getDescription()));
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setVersionNo(1);
        save(entity);

        WorkflowDefinitionDesignEntity design = new WorkflowDefinitionDesignEntity();
        design.setDefinitionId(entity.getId());
        design.setDesignJson(WorkflowDesignJsonHelper.defaultDesignJson());
        design.setVersionNo(1);
        workflowDefinitionDesignMapper.insert(design);
        return entity;
    }

    @Override
    public WorkflowDefinitionEntity updateDefinition(String id, UpdateWorkflowDefinitionRequest request) {
        WorkflowDefinitionEntity existing = getById(id);
        if (existing == null) {
            throw new IllegalArgumentException("流程定义不存在");
        }
        String name = safeTrim(request.getName());
        if (name.isEmpty()) {
            throw new IllegalArgumentException("流程名称不能为空");
        }
        WorkflowDefinitionEntity update = new WorkflowDefinitionEntity();
        update.setId(id);
        update.setName(name);
        update.setDescription(emptyToNull(request.getDescription()));
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        updateById(update);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeDefinition(String id) {
        QueryWrapper<WorkflowDefinitionDesignEntity> designQw = new QueryWrapper<>();
        designQw.eq("DEFINITION_ID", id);
        workflowDefinitionDesignMapper.delete(designQw);
        return removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDesignResponse saveDesign(String definitionId, SaveWorkflowDesignRequest request) {
        requireDefinition(definitionId);
        String json = WorkflowDesignJsonHelper.toJsonString(objectMapper, request == null ? null : request.getDesignJson());
        WorkflowDefinitionDesignEntity design = getOrCreateDesign(definitionId);
        design.setDesignJson(WorkflowDesignJsonHelper.normalizeDesignJson(json));
        design.setVersionNo((design.getVersionNo() == null ? 0 : design.getVersionNo()) + 1);
        if (design.getId() == null || design.getId().trim().isEmpty()) {
            workflowDefinitionDesignMapper.insert(design);
        } else {
            workflowDefinitionDesignMapper.updateById(design);
        }
        return buildDesignResponse(design);
    }

    @Override
    public WorkflowDesignResponse getDesign(String definitionId) {
        requireDefinition(definitionId);
        WorkflowDefinitionDesignEntity design = getOrCreateDesign(definitionId);
        return buildDesignResponse(design);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDesignResponse deleteNode(String definitionId, String nodeId) {
        requireDefinition(definitionId);
        String node = safeTrim(nodeId);
        if (node.isEmpty()) {
            throw new IllegalArgumentException("节点ID不能为空");
        }
        WorkflowDefinitionDesignEntity design = getOrCreateDesign(definitionId);
        String updatedJson = WorkflowDesignJsonHelper.removeNode(design.getDesignJson(), node, objectMapper);
        design.setDesignJson(updatedJson);
        design.setVersionNo((design.getVersionNo() == null ? 0 : design.getVersionNo()) + 1);
        workflowDefinitionDesignMapper.updateById(design);
        return buildDesignResponse(design);
    }

    @Override
    public String nextCode() {
        return "WF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private void requireDefinition(String definitionId) {
        WorkflowDefinitionEntity definition = getById(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("流程定义不存在");
        }
    }

    private WorkflowDefinitionDesignEntity getOrCreateDesign(String definitionId) {
        QueryWrapper<WorkflowDefinitionDesignEntity> qw = new QueryWrapper<>();
        qw.eq("DEFINITION_ID", definitionId).orderByDesc("UPDATED_AT");
        List<WorkflowDefinitionDesignEntity> rows = workflowDefinitionDesignMapper.selectList(qw);
        WorkflowDefinitionDesignEntity design = rows.isEmpty() ? null : rows.get(0);
        if (design != null) {
            design.setDesignJson(WorkflowDesignJsonHelper.normalizeDesignJson(design.getDesignJson()));
            if (design.getVersionNo() == null) {
                design.setVersionNo(1);
            }
            return design;
        }

        WorkflowDefinitionDesignEntity created = new WorkflowDefinitionDesignEntity();
        created.setDefinitionId(definitionId);
        created.setDesignJson(WorkflowDesignJsonHelper.defaultDesignJson());
        created.setVersionNo(1);
        workflowDefinitionDesignMapper.insert(created);
        return created;
    }

    private WorkflowDesignResponse buildDesignResponse(WorkflowDefinitionDesignEntity design) {
        WorkflowDesignResponse response = new WorkflowDesignResponse();
        response.setDefinitionId(design.getDefinitionId());
        response.setVersionNo(design.getVersionNo());
        try {
            response.setDesignJson(objectMapper.readTree(WorkflowDesignJsonHelper.normalizeDesignJson(design.getDesignJson())));
        } catch (Exception e) {
            response.setDesignJson(objectMapper.createObjectNode());
        }
        return response;
    }

    private String resolveCode(String code) {
        String c = safeTrim(code);
        return c.isEmpty() ? nextCode() : c;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        String text = safeTrim(value);
        return text.isEmpty() ? null : text;
    }
}

