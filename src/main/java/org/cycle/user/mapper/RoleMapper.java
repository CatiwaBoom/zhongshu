package org.cycle.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cycle.user.entity.RoleEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

	@Select("SELECT r.* FROM SYS_ROLE r JOIN SYS_USER_ROLE ur ON r.ID = ur.ROLE_ID WHERE ur.USER_ID = #{userId}")
	List<RoleEntity> selectRolesByUserId(String userId);

	// 只查询角色编码字段（code），减少数据传输与映射开销
	@Select("SELECT r.code FROM SYS_ROLE r JOIN SYS_USER_ROLE ur ON r.ID = ur.ROLE_ID WHERE ur.USER_ID = #{userId}")
	List<String> selectRoleCodesByUserId(String userId);

	@Select("SELECT ur.USER_ID FROM SYS_USER_ROLE ur WHERE ur.ROLE_ID = #{roleId}")
	List<String> selectUserIdsByRoleId(String roleId);

	@Delete("DELETE FROM SYS_USER_ROLE WHERE ROLE_ID = #{roleId}")
	void deleteUserRolesByRoleId(String roleId);

	@Insert("INSERT INTO SYS_USER_ROLE(ID, USER_ID, ROLE_ID) VALUES(#{id}, #{userId}, #{roleId})")
	void insertUserRole(@Param("id") String id, @Param("userId") String userId, @Param("roleId") String roleId);

}

