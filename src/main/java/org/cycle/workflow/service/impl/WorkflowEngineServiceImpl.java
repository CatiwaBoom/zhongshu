package org.cycle.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.cycle.workflow.chain.ActionChain;
import org.cycle.workflow.chain.ActionContext;
import org.cycle.workflow.dto.NodeDefDTO;
import org.cycle.workflow.dto.PublishDefinitionRequest;
import org.cycle.workflow.dto.StartProcessRequest;
import org.cycle.workflow.dto.TaskActionRequest;
import org.cycle.workflow.dto.WorkflowActionResult;
import org.cycle.workflow.entity.ActionLogEntity;
import org.cycle.workflow.entity.IdempotentRecordEntity;
import org.cycle.workflow.entity.ProcessDefinitionEntity;
import org.cycle.workflow.entity.ProcessInstanceEntity;
import org.cycle.workflow.entity.ProcessNodeDefinitionEntity;
import org.cycle.workflow.entity.TaskInstanceEntity;
import org.cycle.workflow.enums.ActionType;
import org.cycle.workflow.enums.ProcessState;
import org.cycle.workflow.enums.TaskState;
import org.cycle.workflow.enums.VoteResult;
import org.cycle.workflow.mapper.ActionLogMapper;
import org.cycle.workflow.mapper.IdempotentRecordMapper;
import org.cycle.workflow.mapper.ProcessDefinitionMapper;
import org.cycle.workflow.mapper.CodeSeqMapper;
import org.cycle.workflow.entity.CodeSeqEntity;
import org.cycle.workflow.mapper.ProcessInstanceMapper;
import org.cycle.workflow.mapper.ProcessNodeDefinitionMapper;
import org.cycle.workflow.mapper.TaskInstanceMapper;
import org.cycle.workflow.service.WorkflowEngineService;
import org.cycle.workflow.state.WorkflowStateMachine;
import org.cycle.workflow.strategy.ApprovalStrategy;
import org.cycle.workflow.strategy.ApprovalStrategyRegistry;
import org.cycle.workflow.util.WorkflowIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程引擎核心服务实现。
 * 包含流程定义发布、实例发起、任务动作处理与节点流转。
 */
@Slf4j
@Service
public class WorkflowEngineServiceImpl implements WorkflowEngineService {

    @Resource
    private ProcessDefinitionMapper processDefinitionMapper;
    @Resource
    private CodeSeqMapper codeSeqMapper;
    @Resource
    private ProcessNodeDefinitionMapper processNodeDefinitionMapper;
    @Resource
    private ProcessInstanceMapper processInstanceMapper;
    @Resource
    private TaskInstanceMapper taskInstanceMapper;
    @Resource
    private ActionLogMapper actionLogMapper;
    @Resource
    private IdempotentRecordMapper idempotentRecordMapper;
    @Resource
    private ApprovalStrategyRegistry approvalStrategyRegistry;

    // 状态迁移统一由状态机校验，避免逻辑散落在各个分支里。
    private final WorkflowStateMachine stateMachine = new WorkflowStateMachine();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String publishDefinition(PublishDefinitionRequest request) {
        // 发布新版本前先校验参数，若未提供 code 则在服务端生成唯一 code
        validatePublishRequest(request);
        String code = request.getCode();
        if (isBlank(code)) {
            code = getNextDefinitionCode();
            request.setCode(code);
        }

        int nextVersion = queryNextVersion(code);

        UpdateWrapper<ProcessDefinitionEntity> clearLatest = new UpdateWrapper<ProcessDefinitionEntity>();
        clearLatest.eq("code", code).set("is_latest", 0);
        processDefinitionMapper.update(null, clearLatest);

        ProcessDefinitionEntity definition = new ProcessDefinitionEntity();
        definition.setId(WorkflowIds.nextId());
        definition.setCode(request.getCode());
        definition.setName(request.getName());
        definition.setVersionNo(nextVersion);
        definition.setStatus(1);
        definition.setIsLatest(1);
        definition.setRemark(request.getRemark());
        definition.setCreatedBy(request.getOperator());
        definition.setUpdatedBy(request.getOperator());
        processDefinitionMapper.insert(definition);

        for (int i = 0; i < request.getNodes().size(); i++) {
            NodeDefDTO node = request.getNodes().get(i);
            ProcessNodeDefinitionEntity nodeDef = new ProcessNodeDefinitionEntity();
            nodeDef.setId(WorkflowIds.nextId());
            nodeDef.setDefId(definition.getId());
            nodeDef.setNodeKey(node.getNodeKey());
            nodeDef.setNodeName(node.getNodeName());
            nodeDef.setSortNo(i + 1);
            nodeDef.setApprovalMode(node.getApprovalMode());
            nodeDef.setAssigneeExpr(String.join(",", node.getAssignees()));
            nodeDef.setCreatedBy(request.getOperator());
            nodeDef.setUpdatedBy(request.getOperator());
            processNodeDefinitionMapper.insert(nodeDef);
        }
        return definition.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowActionResult startProcess(StartProcessRequest request) {
        // 发起动作走幂等保护，防止网络重试导致重复创建流程实例。
        String actionType = ActionType.SUBMIT.name();
        IdempotentRecordEntity done = queryIdempotent(request.getRequestId(), actionType);
        if (done != null) {
            ProcessInstanceEntity cached = processInstanceMapper.selectById(done.getInstanceId());
            return toResult(cached, "幂等命中，直接返回");
        }

        ProcessDefinitionEntity definition = queryLatestDefinition(request.getDefinitionCode());
        if (definition == null) {
            throw new IllegalArgumentException("流程定义不存在: " + request.getDefinitionCode());
        }

        List<ProcessNodeDefinitionEntity> nodes = queryDefinitionNodes(definition.getId());
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("流程定义未配置审批节点");
        }

        ProcessNodeDefinitionEntity firstNode = nodes.get(0);
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId(WorkflowIds.nextId());
        instance.setDefId(definition.getId());
        instance.setDefCode(definition.getCode());
        instance.setDefVersion(definition.getVersionNo());
        instance.setBizType(request.getBizType());
        instance.setBizId(request.getBizId());
        instance.setState(ProcessState.RUNNING.name());
        instance.setCurrentNodeKey(firstNode.getNodeKey());
        instance.setStarter(request.getStarter());
        instance.setVariablesJson(request.getVariablesJson());
        instance.setRevision(0);
        instance.setCreatedBy(request.getStarter());
        instance.setUpdatedBy(request.getStarter());
        processInstanceMapper.insert(instance);

        // 使用定义首节点生成待办任务，驱动流程进入运行态。
        createPendingTasks(instance.getId(), firstNode, request.getStarter());
        logAction(instance.getId(), null, ActionType.SUBMIT.name(), request.getStarter(), "发起流程");
        saveIdempotent(request.getRequestId(), actionType, instance.getId(), "started");

        return toResult(instance, "流程发起成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowActionResult handleTaskAction(TaskActionRequest request) {
        // 统一动作分发入口：先幂等、再按动作路由到具体处理函数。
        ActionType actionType = ActionType.valueOf(request.getAction().toUpperCase());
        IdempotentRecordEntity done = queryIdempotent(request.getRequestId(), actionType.name());
        if (done != null) {
            ProcessInstanceEntity cached = processInstanceMapper.selectById(done.getInstanceId());
            return toResult(cached, "幂等命中，直接返回");
        }

        Map<ActionType, Function<TaskActionRequest, WorkflowActionResult>> dispatch = new HashMap<ActionType, Function<TaskActionRequest, WorkflowActionResult>>();
        dispatch.put(ActionType.APPROVE, this::doApprove);
        dispatch.put(ActionType.REJECT, this::doReject);
        dispatch.put(ActionType.TRANSFER, this::doTransfer);
        dispatch.put(ActionType.WITHDRAW, this::doWithdraw);
        dispatch.put(ActionType.TERMINATE, this::doTerminate);

        Function<TaskActionRequest, WorkflowActionResult> executor = dispatch.get(actionType);
        if (executor == null) {
            throw new IllegalArgumentException("不支持的动作: " + actionType);
        }

        WorkflowActionResult result = executor.apply(request);
        saveIdempotent(request.getRequestId(), actionType.name(), request.getInstanceId(), result.getMessage());
        return result;
    }

    @Override
    public ProcessInstanceEntity getInstance(String instanceId) {
        return processInstanceMapper.selectById(instanceId);
    }

    @Override
    public String getNextDefinitionCode() {
        // 使用数据库序列表进行原子加 1 操作，避免并发冲突
        java.util.Date d = new java.util.Date();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(d);
        String prefix = "WF_" + dateStr + "_";

        // 先尝试原子更新：UPDATE ... SET LAST_SEQ = LAST_SEQ + 1 WHERE SEQ_DATE = ?
        int updated = codeSeqMapper.increment(dateStr);
        if (updated == 0) {
            // 若没有记录则尝试插入一条初始记录（LAST_SEQ=1），并视情况重试 increment
            try {
                codeSeqMapper.insertNew(dateStr);
            } catch (Exception ignore) {
                // 并发插入可能会导致唯一主键冲突，忽略并继续
            }
            // 再次 increment，若仍为0则说明插入失败且更新也失败，退回到安全的查询方式
            updated = codeSeqMapper.increment(dateStr);
            if (updated == 0) {
                // 回退到查询最大已有编码（极端情况）
                QueryWrapper<ProcessDefinitionEntity> wrapper = new QueryWrapper<ProcessDefinitionEntity>();
                wrapper.likeRight("code", prefix).orderByDesc("code");
                List<ProcessDefinitionEntity> defs = processDefinitionMapper.selectList(wrapper);
                int maxSeq = 0;
                if (defs != null && !defs.isEmpty()) {
                    for (ProcessDefinitionEntity def : defs) {
                        String code = def.getCode();
                        if (code != null && code.startsWith(prefix)) {
                            String tail = code.substring(prefix.length());
                            try {
                                int n = Integer.parseInt(tail);
                                if (n > maxSeq) maxSeq = n;
                            } catch (NumberFormatException ignore2) {
                            }
                        }
                    }
                }
                int next = maxSeq + 1;
                return prefix + String.format("%03d", next);
            }
        }

        // 当 updated > 0 时，最后的序号为当前 LAST_SEQ 的值，需要查询出最新值
        CodeSeqEntity seq = codeSeqMapper.selectByDate(dateStr);
        int last = seq != null && seq.getLastSeq() != null ? seq.getLastSeq() : 0;
        return prefix + String.format("%03d", last);
    }

    private WorkflowActionResult doApprove(TaskActionRequest request) {
        ActionContext context = loadActionContext(request);
        runChain(context, Arrays.asList(
                this::validateInstanceRunning,
                this::validateTaskPending,
                this::validateOperatorOwnTask
        ));

        updateTask(context.getTask(), TaskState.APPROVED, VoteResult.PASS, request.getOperator());
        ProcessNodeDefinitionEntity nodeDef = queryNodeByKey(context.getInstance().getDefId(), context.getTask().getNodeKey());
        List<TaskInstanceEntity> nodeTasks = queryNodeTasks(context.getInstance().getId(), context.getTask().getNodeKey());

        ApprovalStrategy strategy = approvalStrategyRegistry.get(nodeDef.getApprovalMode());
        if (strategy.nodeApproved(nodeTasks, context.getTask())) {
            cancelOtherPendingTasks(context.getTask().getId(), nodeTasks, request.getOperator());
            moveToNextNodeOrComplete(context.getInstance(), context.getTask().getNodeKey(), request.getOperator());
        }

        logAction(context.getInstance().getId(), context.getTask().getId(), ActionType.APPROVE.name(), request.getOperator(), request.getComment());
        ProcessInstanceEntity latest = processInstanceMapper.selectById(context.getInstance().getId());
        return toResult(latest, "审批通过");
    }

    private WorkflowActionResult doReject(TaskActionRequest request) {
        ActionContext context = loadActionContext(request);
        runChain(context, Arrays.asList(
                this::validateInstanceRunning,
                this::validateTaskPending,
                this::validateOperatorOwnTask
        ));

        updateTask(context.getTask(), TaskState.REJECTED, VoteResult.REJECT, request.getOperator());
        terminateInstance(context.getInstance(), request.getOperator(), "节点驳回");
        logAction(context.getInstance().getId(), context.getTask().getId(), ActionType.REJECT.name(), request.getOperator(), request.getComment());

        ProcessInstanceEntity latest = processInstanceMapper.selectById(context.getInstance().getId());
        return toResult(latest, "流程已驳回");
    }

    private WorkflowActionResult doTransfer(TaskActionRequest request) {
        if (request.getTargetUser() == null || request.getTargetUser().trim().isEmpty()) {
            throw new IllegalArgumentException("移交目标人不能为空");
        }

        ActionContext context = loadActionContext(request);
        runChain(context, Arrays.asList(
                this::validateInstanceRunning,
                this::validateTaskPending,
                this::validateOperatorOwnTask
        ));

        TaskInstanceEntity task = context.getTask();
        task.setDelegatedFrom(task.getAssignee());
        task.setAssignee(request.getTargetUser());
        task.setUpdatedBy(request.getOperator());
        taskInstanceMapper.updateById(task);

        logAction(context.getInstance().getId(), task.getId(), ActionType.TRANSFER.name(), request.getOperator(), request.getComment());
        ProcessInstanceEntity latest = processInstanceMapper.selectById(context.getInstance().getId());
        return toResult(latest, "任务移交成功");
    }

    private WorkflowActionResult doWithdraw(TaskActionRequest request) {
        ProcessInstanceEntity instance = mustGetInstance(request.getInstanceId());
        validateInstanceRunningOnly(instance);
        if (!Objects.equals(instance.getStarter(), request.getOperator())) {
            throw new IllegalStateException("仅流程发起人可以撤回");
        }

        QueryWrapper<TaskInstanceEntity> handledQuery = new QueryWrapper<TaskInstanceEntity>();
        handledQuery.eq("instance_id", instance.getId()).ne("state", TaskState.PENDING.name());
        Long handledCount = taskInstanceMapper.selectCount(handledQuery);
        if (handledCount != null && handledCount > 0) {
            throw new IllegalStateException("已有任务处理，无法撤回");
        }

        UpdateWrapper<ProcessInstanceEntity> updateInst = new UpdateWrapper<ProcessInstanceEntity>();
        updateInst.eq("id", instance.getId())
                .eq("revision", instance.getRevision())
                .set("state", ProcessState.WITHDRAWN.name())
                .set("revision", instance.getRevision() + 1)
                .set("updated_by", request.getOperator());
        int instUpdated = processInstanceMapper.update(null, updateInst);
        if (instUpdated == 0) {
            throw new IllegalStateException("流程状态已变化，请刷新后重试");
        }

        cancelPendingByInstance(instance.getId(), request.getOperator());
        logAction(instance.getId(), null, ActionType.WITHDRAW.name(), request.getOperator(), request.getComment());

        ProcessInstanceEntity latest = processInstanceMapper.selectById(instance.getId());
        return toResult(latest, "流程撤回成功");
    }

    private WorkflowActionResult doTerminate(TaskActionRequest request) {
        ProcessInstanceEntity instance = mustGetInstance(request.getInstanceId());
        validateInstanceRunningOnly(instance);

        terminateInstance(instance, request.getOperator(), request.getComment());
        logAction(instance.getId(), request.getTaskId(), ActionType.TERMINATE.name(), request.getOperator(), request.getComment());

        ProcessInstanceEntity latest = processInstanceMapper.selectById(instance.getId());
        return toResult(latest, "流程终止成功");
    }

    private void terminateInstance(ProcessInstanceEntity instance, String operator, String reason) {
        UpdateWrapper<ProcessInstanceEntity> updateInst = new UpdateWrapper<ProcessInstanceEntity>();
        updateInst.eq("id", instance.getId())
                .eq("revision", instance.getRevision())
                .set("state", ProcessState.TERMINATED.name())
                .set("revision", instance.getRevision() + 1)
                .set("updated_by", operator);
        int updated = processInstanceMapper.update(null, updateInst);
        if (updated == 0) {
            throw new IllegalStateException("流程状态已变化，终止失败");
        }
        cancelPendingByInstance(instance.getId(), operator);
        log.info("流程已终止, instanceId={}, reason={}", instance.getId(), reason);
    }

    private void moveToNextNodeOrComplete(ProcessInstanceEntity instance, String currentNodeKey, String operator) {
        // 串行节点模型：若当前已是最后一个节点则结束，否则推进到下一节点并创建待办。
        List<ProcessNodeDefinitionEntity> nodes = queryDefinitionNodes(instance.getDefId());
        int currentIndex = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (Objects.equals(nodes.get(i).getNodeKey(), currentNodeKey)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex < 0) {
            throw new IllegalStateException("当前节点不在定义内: " + currentNodeKey);
        }

        if (currentIndex == nodes.size() - 1) {
            UpdateWrapper<ProcessInstanceEntity> update = new UpdateWrapper<ProcessInstanceEntity>();
            update.eq("id", instance.getId())
                    .eq("revision", instance.getRevision())
                    .set("state", ProcessState.COMPLETED.name())
                    .set("current_node_key", null)
                    .set("revision", instance.getRevision() + 1)
                    .set("updated_by", operator);
            int updated = processInstanceMapper.update(null, update);
            if (updated == 0) {
                throw new IllegalStateException("流程并发更新冲突");
            }
            return;
        }

        ProcessNodeDefinitionEntity nextNode = nodes.get(currentIndex + 1);
        UpdateWrapper<ProcessInstanceEntity> update = new UpdateWrapper<ProcessInstanceEntity>();
        update.eq("id", instance.getId())
                .eq("revision", instance.getRevision())
                .set("current_node_key", nextNode.getNodeKey())
                .set("revision", instance.getRevision() + 1)
                .set("updated_by", operator);
        int updated = processInstanceMapper.update(null, update);
        if (updated == 0) {
            throw new IllegalStateException("流程并发更新冲突");
        }

        createPendingTasks(instance.getId(), nextNode, operator);
    }

    private ActionContext loadActionContext(TaskActionRequest request) {
        ActionContext context = new ActionContext();
        context.setRequest(request);
        context.setInstance(mustGetInstance(request.getInstanceId()));
        context.setTask(mustGetTask(request.getTaskId()));
        if (!Objects.equals(context.getTask().getInstanceId(), request.getInstanceId())) {
            throw new IllegalArgumentException("任务不属于该流程实例");
        }
        return context;
    }

    private void runChain(ActionContext context, List<Consumer<ActionContext>> handlers) {
        ActionChain chain = new ActionChain(handlers);
        chain.next(context);
    }

    private void validateInstanceRunning(ActionContext context) {
        ProcessState current = ProcessState.valueOf(context.getInstance().getState());
        stateMachine.validate(current, ActionType.valueOf(context.getRequest().getAction().toUpperCase()));
        validateInstanceRunningOnly(context.getInstance());
    }

    private void validateInstanceRunningOnly(ProcessInstanceEntity instance) {
        if (!ProcessState.RUNNING.name().equals(instance.getState())) {
            throw new IllegalStateException("流程非运行中状态: " + instance.getState());
        }
    }

    private void validateTaskPending(ActionContext context) {
        if (!TaskState.PENDING.name().equals(context.getTask().getState())) {
            throw new IllegalStateException("任务已处理，不能重复操作");
        }
    }

    private void validateOperatorOwnTask(ActionContext context) {
        if (!Objects.equals(context.getTask().getAssignee(), context.getRequest().getOperator())) {
            throw new IllegalStateException("仅任务处理人可执行该操作");
        }
    }

    private void updateTask(TaskInstanceEntity task, TaskState state, VoteResult voteResult, String operator) {
        UpdateWrapper<TaskInstanceEntity> update = new UpdateWrapper<TaskInstanceEntity>();
        update.eq("id", task.getId())
                .eq("revision", task.getRevision())
                .set("state", state.name())
                .set("vote_result", voteResult.name())
                .set("completed_at", new Timestamp(System.currentTimeMillis()))
                .set("revision", task.getRevision() + 1)
                .set("updated_by", operator);
        int updated = taskInstanceMapper.update(null, update);
        if (updated == 0) {
            throw new IllegalStateException("任务状态已变化，请刷新后重试");
        }
    }

    private void cancelOtherPendingTasks(String currentTaskId, List<TaskInstanceEntity> tasks, String operator) {
        for (TaskInstanceEntity task : tasks) {
            if (Objects.equals(task.getId(), currentTaskId)) {
                continue;
            }
            if (TaskState.PENDING.name().equals(task.getState())) {
                UpdateWrapper<TaskInstanceEntity> update = new UpdateWrapper<TaskInstanceEntity>();
                update.eq("id", task.getId()).set("state", TaskState.CANCELLED.name()).set("updated_by", operator);
                taskInstanceMapper.update(null, update);
            }
        }
    }

    private void cancelPendingByInstance(String instanceId, String operator) {
        UpdateWrapper<TaskInstanceEntity> update = new UpdateWrapper<TaskInstanceEntity>();
        update.eq("instance_id", instanceId)
                .eq("state", TaskState.PENDING.name())
                .set("state", TaskState.CANCELLED.name())
                .set("updated_by", operator);
        taskInstanceMapper.update(null, update);
    }

    private ProcessDefinitionEntity queryLatestDefinition(String code) {
        QueryWrapper<ProcessDefinitionEntity> wrapper = new QueryWrapper<ProcessDefinitionEntity>();
        wrapper.eq("code", code)
                .eq("status", 1)
                .eq("is_latest", 1)
                .orderByDesc("version_no")
                .last("FETCH FIRST 1 ROWS ONLY");
        return processDefinitionMapper.selectOne(wrapper);
    }

    private List<ProcessNodeDefinitionEntity> queryDefinitionNodes(String defId) {
        QueryWrapper<ProcessNodeDefinitionEntity> wrapper = new QueryWrapper<ProcessNodeDefinitionEntity>();
        wrapper.eq("def_id", defId).orderByAsc("sort_no");
        List<ProcessNodeDefinitionEntity> list = processNodeDefinitionMapper.selectList(wrapper);
        return list == null ? new ArrayList<ProcessNodeDefinitionEntity>() : list;
    }

    private List<TaskInstanceEntity> queryNodeTasks(String instanceId, String nodeKey) {
        QueryWrapper<TaskInstanceEntity> wrapper = new QueryWrapper<TaskInstanceEntity>();
        wrapper.eq("instance_id", instanceId).eq("node_key", nodeKey);
        List<TaskInstanceEntity> list = taskInstanceMapper.selectList(wrapper);
        return list == null ? new ArrayList<TaskInstanceEntity>() : list;
    }

    private ProcessNodeDefinitionEntity queryNodeByKey(String defId, String nodeKey) {
        QueryWrapper<ProcessNodeDefinitionEntity> wrapper = new QueryWrapper<ProcessNodeDefinitionEntity>();
        wrapper.eq("def_id", defId).eq("node_key", nodeKey);
        ProcessNodeDefinitionEntity node = processNodeDefinitionMapper.selectOne(wrapper);
        if (node == null) {
            throw new IllegalStateException("节点定义不存在: " + nodeKey);
        }
        return node;
    }

    private void createPendingTasks(String instanceId, ProcessNodeDefinitionEntity node, String operator) {
        List<String> assignees = Arrays.stream(node.getAssigneeExpr().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (assignees.isEmpty()) {
            throw new IllegalArgumentException("节点未配置审批人: " + node.getNodeKey());
        }

        for (String assignee : assignees) {
            TaskInstanceEntity task = new TaskInstanceEntity();
            task.setId(WorkflowIds.nextId());
            task.setInstanceId(instanceId);
            task.setNodeKey(node.getNodeKey());
            task.setAssignee(assignee);
            task.setState(TaskState.PENDING.name());
            task.setRevision(0);
            task.setCreatedBy(operator);
            task.setUpdatedBy(operator);
            taskInstanceMapper.insert(task);
        }
    }

    private void logAction(String instanceId, String taskId, String actionType, String operator, String comment) {
        ActionLogEntity logEntity = new ActionLogEntity();
        logEntity.setId(WorkflowIds.nextId());
        logEntity.setInstanceId(instanceId);
        logEntity.setTaskId(taskId);
        logEntity.setActionType(actionType);
        logEntity.setOperator(operator);
        logEntity.setActionComment(comment);
        logEntity.setSnapshotJson("{}");
        logEntity.setCreatedBy(operator);
        logEntity.setUpdatedBy(operator);
        actionLogMapper.insert(logEntity);
    }

    private void saveIdempotent(String requestId, String actionType, String instanceId, String response) {
        // 请求未带 requestId 时不做幂等落库，兼容存量调用方。
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        IdempotentRecordEntity record = new IdempotentRecordEntity();
        record.setId(WorkflowIds.nextId());
        record.setRequestId(requestId);
        record.setActionType(actionType);
        record.setInstanceId(instanceId);
        record.setResponseJson(response);
        record.setCreatedBy("system");
        record.setUpdatedBy("system");
        idempotentRecordMapper.insert(record);
    }

    private IdempotentRecordEntity queryIdempotent(String requestId, String actionType) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<IdempotentRecordEntity> wrapper = new QueryWrapper<IdempotentRecordEntity>();
        wrapper.eq("request_id", requestId).eq("action_type", actionType);
        return idempotentRecordMapper.selectOne(wrapper);
    }

    private ProcessInstanceEntity mustGetInstance(String instanceId) {
        ProcessInstanceEntity instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("流程实例不存在: " + instanceId);
        }
        return instance;
    }

    private TaskInstanceEntity mustGetTask(String taskId) {
        TaskInstanceEntity task = taskInstanceMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    private int queryNextVersion(String code) {
        QueryWrapper<ProcessDefinitionEntity> wrapper = new QueryWrapper<ProcessDefinitionEntity>();
        wrapper.eq("code", code).orderByDesc("version_no");
        List<ProcessDefinitionEntity> defs = processDefinitionMapper.selectList(wrapper);
        if (defs == null || defs.isEmpty()) {
            return 1;
        }
        defs.sort(Comparator.comparing(ProcessDefinitionEntity::getVersionNo).reversed());
        return defs.get(0).getVersionNo() + 1;
    }

    private void validatePublishRequest(PublishDefinitionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("发布参数不能为空");
        }
        if (isBlank(request.getName())) {
            throw new IllegalArgumentException("流程名称不能为空");
        }
        if (request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new IllegalArgumentException("至少要配置一个审批节点");
        }
        for (NodeDefDTO node : request.getNodes()) {
            if (isBlank(node.getNodeKey()) || isBlank(node.getNodeName()) || isBlank(node.getApprovalMode())) {
                throw new IllegalArgumentException("节点配置不完整");
            }
            if (node.getAssignees() == null || node.getAssignees().isEmpty()) {
                throw new IllegalArgumentException("节点审批人不能为空");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private WorkflowActionResult toResult(ProcessInstanceEntity instance, String message) {
        return new WorkflowActionResult(instance.getId(), instance.getState(), instance.getCurrentNodeKey(), message);
    }
}
