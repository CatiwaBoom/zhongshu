package org.cycle.workflow.chain;

import lombok.Data;
import org.cycle.workflow.dto.TaskActionRequest;
import org.cycle.workflow.entity.ProcessInstanceEntity;
import org.cycle.workflow.entity.TaskInstanceEntity;

/**
 * 责任链共享上下文：在处理链上透传请求、流程实例、任务实例。
 */
@Data
public class ActionContext {
    private TaskActionRequest request;
    private ProcessInstanceEntity instance;
    private TaskInstanceEntity task;
}
