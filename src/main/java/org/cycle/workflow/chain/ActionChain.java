package org.cycle.workflow.chain;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 轻量责任链：按顺序执行处理器，处理器间共享同一个上下文。
 */
public class ActionChain {

    private final Iterator<Consumer<ActionContext>> iterator;

    public ActionChain(List<Consumer<ActionContext>> handlers) {
        this.iterator = handlers.iterator();
    }

    /**
     * 递归执行下一个处理器，直到链尾。
     */
    public void next(ActionContext context) {
        if (iterator.hasNext()) {
            iterator.next().accept(context);
            next(context);
        }
    }
}
