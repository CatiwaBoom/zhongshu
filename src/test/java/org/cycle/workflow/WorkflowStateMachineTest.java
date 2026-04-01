package org.cycle.workflow;

import org.cycle.workflow.enums.ActionType;
import org.cycle.workflow.enums.ProcessState;
import org.cycle.workflow.state.WorkflowStateMachine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkflowStateMachineTest {

    @Test
    void runningAllowsApprove() {
        WorkflowStateMachine machine = new WorkflowStateMachine();
        Assertions.assertDoesNotThrow(() -> machine.validate(ProcessState.RUNNING, ActionType.APPROVE));
    }

    @Test
    void completedRejectsApprove() {
        WorkflowStateMachine machine = new WorkflowStateMachine();
        Assertions.assertThrows(IllegalStateException.class,
                () -> machine.validate(ProcessState.COMPLETED, ActionType.APPROVE));
    }
}

