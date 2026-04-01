package org.cycle.workflow.state;

import org.cycle.workflow.enums.ActionType;
import org.cycle.workflow.enums.ProcessState;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 流程状态机：集中维护“流程状态 -> 可执行动作”的迁移规则。
 */
public class WorkflowStateMachine {

    // 每个流程状态下允许的动作集合。
    private final Map<ProcessState, java.util.List<ActionType>> transitions = new EnumMap<ProcessState, java.util.List<ActionType>>(ProcessState.class);

    public WorkflowStateMachine() {
        // 草稿态只能发起。
        transitions.put(ProcessState.DRAFT, Collections.singletonList(ActionType.SUBMIT));
        // 运行态允许审批、驳回、移交、撤回、终止。
        transitions.put(ProcessState.RUNNING, Arrays.asList(ActionType.APPROVE, ActionType.REJECT, ActionType.TRANSFER, ActionType.WITHDRAW, ActionType.TERMINATE));
        transitions.put(ProcessState.COMPLETED, Collections.<ActionType>emptyList());
        transitions.put(ProcessState.TERMINATED, Collections.<ActionType>emptyList());
        transitions.put(ProcessState.WITHDRAWN, Collections.<ActionType>emptyList());
    }

    /**
     * 校验动作在当前流程状态下是否合法，不合法直接抛异常。
     */
    public void validate(ProcessState current, ActionType action) {
        java.util.List<ActionType> allowed = transitions.get(current);
        if (allowed == null || !allowed.contains(action)) {
            throw new IllegalStateException("非法状态迁移: state=" + current + ", action=" + action);
        }
    }
}
