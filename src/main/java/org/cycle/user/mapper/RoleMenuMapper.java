package org.cycle.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.cycle.user.entity.RoleMenuEntity;

import java.util.List;

@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenuEntity> {

    @Delete("DELETE FROM SYS_ROLE_MENU WHERE ROLE_ID = #{roleId}")
    int deleteByRoleId(@Param("roleId") String roleId);

    // 批量插入通常使用 MyBatis 的 foreach 在 XML 中实现更高效；当前提供单条插入方法，调用方可在循环中调用以实现批量逻辑
    @Insert("INSERT INTO SYS_ROLE_MENU (ID, ROLE_ID, MENU_ID, CREATED_AT) VALUES (#{id}, #{roleId}, #{menuId}, CURRENT_TIMESTAMP)")
    int insertOne(RoleMenuEntity entity);
}

