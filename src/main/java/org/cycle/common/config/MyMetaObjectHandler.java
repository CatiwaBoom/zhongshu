package org.cycle.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * MyBatis-Plus 自动填充处理器：为实体的 createdBy/updatedBy/createdAt/updatedAt 自动赋值
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof String) {
                    return (String) principal;
                }
                if (principal != null) {
                    return principal.toString();
                }
                return auth.getName();
            }
        } catch (Exception ignored) {
            // 无安全上下文时使用默认值
        }
        return "system";
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        // 仅在字段为空时填充 createdAt，updatedAt 总是填充为当前时间
        Object created = this.getFieldValByName("createdAt", metaObject);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (created == null) {
            // 使用严格填充以兼容 MyBatis-Plus 的校验
            this.strictInsertFill(metaObject, "createdAt", Timestamp.class, now);
        }
        this.strictInsertFill(metaObject, "updatedAt", Timestamp.class, now);

        // 填充 createdBy/updatedBy
        String user = getCurrentUserId();
        this.strictInsertFill(metaObject, "createdBy", String.class, user);
        this.strictInsertFill(metaObject, "updatedBy", String.class, user);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        this.strictUpdateFill(metaObject, "updatedAt", Timestamp.class, now);

        String user = getCurrentUserId();
        this.strictUpdateFill(metaObject, "updatedBy", String.class, user);
    }
}

