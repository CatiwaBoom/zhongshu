package org.cycle.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
 
import java.sql.Timestamp;

/**
 * MyBatis-Plus 自动填充处理器
 *
 * 在插入/更新时自动填充 createdBy / updatedBy 和 createdAt / updatedAt 字段
 * 根据当前 SecurityContext 中的 Authentication 读取当前用户 id（insert/update 时）
 */
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

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
            // 在没有安全上下文（如后台任务）时返回默认值
        }
        return "system";
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        String user = getCurrentUserId();
        // 填充创建/更新人
        this.strictInsertFill(metaObject, "createdBy", String.class, user);
        this.strictInsertFill(metaObject, "updatedBy", String.class, user);

        // 填充创建/更新时间（使用 Timestamp 与 BaseEntity 字段类型匹配）
        Timestamp now = new Timestamp(System.currentTimeMillis());
        this.strictInsertFill(metaObject, "createdAt", Timestamp.class, now);
        this.strictInsertFill(metaObject, "updatedAt", Timestamp.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String user = getCurrentUserId();
        this.strictUpdateFill(metaObject, "updatedBy", String.class, user);
        this.strictUpdateFill(metaObject, "updatedAt", Timestamp.class, new Timestamp(System.currentTimeMillis()));
    }
}

