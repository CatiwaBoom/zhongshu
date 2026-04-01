package org.cycle.workflow.strategy;

import org.cycle.workflow.entity.TaskInstanceEntity;
import org.cycle.workflow.enums.ApprovalMode;
import org.cycle.workflow.enums.TaskState;

import java.util.List;

/**
 * 或签策略：任一审批人通过即可通过；只有全部待办都结束后才判定驳回完成。
 */
public class OrSignApprovalStrategy implements ApprovalStrategy {

    @Override
    public String mode() {
        return ApprovalMode.OR_SIGN.name();
    }

    @Override
    public boolean nodeApproved(List<TaskInstanceEntity> allTasksOfNode, TaskInstanceEntity currentTask) {
        return true;
    }

    @Override
    public boolean nodeRejected(List<TaskInstanceEntity> allTasksOfNode, TaskInstanceEntity currentTask) {
        for (TaskInstanceEntity task : allTasksOfNode) {
            if (TaskState.PENDING.name().equals(task.getState())) {
                return false;
            }
        }
        return true;
    }
}
