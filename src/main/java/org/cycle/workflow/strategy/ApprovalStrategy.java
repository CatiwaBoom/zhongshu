package org.cycle.workflow.strategy;

import org.cycle.workflow.entity.TaskInstanceEntity;

import java.util.List;

/**
 * 节点审批策略接口。
 */
public interface ApprovalStrategy {

    /**
     * 返回策略对应的审批模式。
     */
    String mode();

    /**
     * 当前动作为“通过”时，判断节点是否达成通过条件。
     */
    boolean nodeApproved(List<TaskInstanceEntity> allTasksOfNode, TaskInstanceEntity currentTask);

    /**
     * 当前动作为“驳回”时，判断节点是否应当结束。
     */
    boolean nodeRejected(List<TaskInstanceEntity> allTasksOfNode, TaskInstanceEntity currentTask);
}
