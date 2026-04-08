package org.cycle.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * MyBatis-Plus自动填充处理器：为实体的 createdAt/updatedAt 字段自动赋值
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 仅在字段为空时填充 createdAt，updatedAt 总是填充为当前时间
        Object created = this.getFieldValByName("createdAt", metaObject);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (created == null) {
            this.setFieldValByName("createdAt", now, metaObject);
        }
        this.setFieldValByName("updatedAt", now, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        this.setFieldValByName("updatedAt", now, metaObject);
    }
}

