package org.cycle.workflow.strategy;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 审批策略注册器：根据节点配置的审批模式，返回对应策略实现。
 */
@Component
public class ApprovalStrategyRegistry {

    // key=审批模式（如 BASIC/OR_SIGN/COUNTER_SIGN），value=策略实现。
    private final Map<String, ApprovalStrategy> strategyMap = new HashMap<String, ApprovalStrategy>();

    public ApprovalStrategyRegistry() {
        // 这里集中注册默认策略，后续新增模式只需增加实现并放入列表。
        Arrays.asList(
                new BasicApprovalStrategy(),
                new OrSignApprovalStrategy(),
                new CounterSignApprovalStrategy()
        ).forEach(strategy -> strategyMap.put(strategy.mode(), strategy));
    }

    /**
     * 按审批模式获取策略，不支持的模式直接抛错，避免静默降级。
     */
    public ApprovalStrategy get(String mode) {
        ApprovalStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的审批模式: " + mode);
        }
        return strategy;
    }
}
