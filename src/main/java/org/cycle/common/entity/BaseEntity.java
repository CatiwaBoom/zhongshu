package org.cycle.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.Timestamp;

/**
 * 基础实体类（适配MyBatis-Plus + 序列化）
 */
@Slf4j
@Getter
@Setter
public abstract class BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    // 改用线程安全的DateTimeFormatter（替代SimpleDateFormat）
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 主键ID - 适配MyBatis-Plus主键注解
     */
    @TableId // MyBatis-Plus主键注解，默认雪花算法，可根据需求调整type
    private String id;

    /**
     * 创建人 - MyBatis-Plus自动填充（可选）
     */
    @TableField(fill = FieldFill.INSERT) // 插入时自动填充
    private String createdBy;

    /**
     * 更新人 - MyBatis-Plus自动填充（可选）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE) // 插入/更新时自动填充
    private String updatedBy;

    /**
     * 创建时间 - MyBatis-Plus自动填充（可选）
     */
    @TableField(fill = FieldFill.INSERT)
    private Timestamp createdAt;

    /**
     * 更新时间 - MyBatis-Plus自动填充（可选）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Timestamp updatedAt;

    // ========== 修正序列化逻辑（原逻辑重复读写字段，需简化） ==========
    private void writeObject(ObjectOutputStream out) throws IOException {
        // defaultWriteObject() 已序列化所有非transient字段，无需重复写id/createdBy等
        out.defaultWriteObject();
        // 仅需处理时间字段的格式化（若有特殊需求，否则可直接用默认序列化）
        out.writeObject(formatTimestamp(createdAt));
        out.writeObject(formatTimestamp(updatedAt));
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // defaultReadObject() 已反序列化所有非transient字段
        in.defaultReadObject();
        // 仅需处理时间字段的解析（与writeObject对应）
        this.createdAt = parseTimestamp((String) in.readObject());
        this.updatedAt = parseTimestamp((String) in.readObject());
    }

    // ========== 时间格式化/解析（线程安全版） ==========
    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        // 转换为LocalDateTime后格式化，线程安全
        return timestamp.toLocalDateTime().format(DATETIME_FORMATTER);
    }

    private Timestamp parseTimestamp(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(value, DATETIME_FORMATTER);
            return Timestamp.valueOf(localDateTime);
        } catch (Exception e) {
            log.error("时间戳解析失败, pattern=yyyy-MM-dd HH:mm:ss, value={}", value, e);
            throw new IOException("无效日期格式: " + value, e);
        }
    }
}