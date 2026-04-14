package org.cycle.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统菜单实体，映射到表 SYS_MENU
 */
@Data
@TableName("sys_menu")
public class MenuEntity extends BaseEntity implements Serializable {
    /** 菜单标题 */
    private String title;

    /** 菜单路径（前端路由） */
    private String path;

    /** 父菜单 ID */
    private String parentId;

    /** 图标名称 */
    private String icon;

    /** 排序值 */
    private Integer orderNum;

    /** 子菜单（非表字段） */
    @TableField(exist = false)
    private List<MenuEntity> children = new ArrayList<>();

    public List<MenuEntity> getChildren() {
        return children;
    }

    public void setChildren(List<MenuEntity> children) {
        this.children = children;
    }
}

