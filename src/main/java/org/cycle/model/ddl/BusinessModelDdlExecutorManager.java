package org.cycle.model.ddl;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DDL 执行器管理器：按 dbType 选择执行器
 */
@Component
public class BusinessModelDdlExecutorManager {

    private final List<BusinessModelDdlExecutor> executors;

    public BusinessModelDdlExecutorManager(List<BusinessModelDdlExecutor> executors) {
        this.executors = executors;
    }

    public BusinessModelDdlExecutor getExecutor(String dbType) {
        if (dbType != null) {
            for (BusinessModelDdlExecutor e : executors) {
                if (dbType.equalsIgnoreCase(e.getDbType())) return e;
            }
        }
        // 兜底：返回第一个通用执行器（dbType='*'）或列表第一个
        for (BusinessModelDdlExecutor e : executors) {
            if ("*".equals(e.getDbType())) return e;
        }
        return executors.isEmpty() ? null : executors.get(0);
    }
}

