package org.cycle.model.ddl;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DDL 生成器管理器：根据 dbType 选择合适的生成器
 */
@Component
public class BusinessModelDdlGeneratorManager {

    private final List<BusinessModelDdlGenerator> generators;

    public BusinessModelDdlGeneratorManager(List<BusinessModelDdlGenerator> generators) {
        this.generators = generators;
    }

    /**
     * 根据 dbType 获取生成器；若未找到则返回第一个可用实现作为兜底
     */
    public BusinessModelDdlGenerator getGenerator(String dbType) {
        if (dbType != null) {
            for (BusinessModelDdlGenerator g : generators) {
                if (dbType.equalsIgnoreCase(g.getDbType())) return g;
            }
        }
        return generators.isEmpty() ? null : generators.get(0);
    }
}

