package org.cycle.workflow.strategy;

import org.cycle.workflow.entity.TaskInstanceEntity;
import org.cycle.workflow.enums.ApprovalMode;

import java.util.List;

/**
 * 基础审批策略：单任务通过即节点通过，驳回即节点驳回。
 */
public class BasicApprovalStrategy implements ApprovalStrategy {

    @Override
    public String mode() {
        return ApprovalMode.BASIC.name();
    }

    @Override
    public boolean nodeApproved(List<TaskInstanceEntity> allTasksOfNode, TaskInstanceEntity currentTask) {
        return true;
    }

    @Override
    public boolean nodeRejected(List<TaskInstanceEntity> allTasksOfNode, TaskInstanceEntity currentTask) {
        return true;
    }
}
