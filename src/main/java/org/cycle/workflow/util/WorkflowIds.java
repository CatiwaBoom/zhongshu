package org.cycle.workflow.util;

import java.util.UUID;

/**
 * 流程引擎ID生成工具。
 */
public final class WorkflowIds {

    private WorkflowIds() {
    }

    /**
     * 生成32位无横线UUID。
     */
    public static String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
