package org.cycle.model.ddl;

import org.cycle.model.entity.BusinessFieldEntity;
import org.cycle.model.entity.BusinessModelEntity;

import java.util.List;

/**
 * 业务模型 DDL 生成器接口（策略模式）
 * 不同数据库实现此接口以生成对应的建表语句
 */
public interface BusinessModelDdlGenerator {
    /**
     * 生成建表语句
     * @param model 模型元信息
     * @param fields 模型字段列表
     * @return 建表 SQL 字符串
     */
    String generateCreateTable(BusinessModelEntity model, List<BusinessFieldEntity> fields);

    /**
     * 返回此生成器对应的数据库类型标识（例如：mysql, dm, oracle）
     */
    String getDbType();
}

