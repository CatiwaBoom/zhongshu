package org.cycle.workflow.strategy;

import org.cycle.workflow.entity.TaskInstanceEntity;
import org.cycle.workflow.enums.ApprovalMode;
import org.cycle.workflow.enums.TaskState;

import java.util.List;

/**
 * 会签策略：同一节点下全部任务都通过才算节点通过。
 */
public class CounterSignApprovalStrategy implements ApprovalStrategy {

    @Override
    public String mode() {
        return ApprovalMode.COUNTER_SIGN.name();
    }

    @Override
    public boolean nodeApproved(List<TaskInstanceEntity> allTasksOfNode, TaskInstanceEntity currentTask) {
        for (TaskInstanceEntity task : allTasksOfNode) {
            if (!TaskState.APPROVED.name().equals(task.getState())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean nodeRejected(List<TaskInstanceEntity> allTasksOfNode, TaskInstanceEntity currentTask) {
        return true;
    }
}
