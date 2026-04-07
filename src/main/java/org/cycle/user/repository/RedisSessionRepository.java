package org.cycle.user.repository;

import org.cycle.user.entity.RedisSession;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Spring Data Redis repository，用于对 Redis 中的 `RedisSession` 进行增删改查。
 * 使用 CrudRepository 提供的基本方法即可满足常见操作。
 */
public interface RedisSessionRepository extends CrudRepository<RedisSession, String> {
	// 找到某个用户的所有会话，便于实现“单活会话”策略（创建新会话时撤销旧会话）
	List<RedisSession> findAllByUserId(String userId);
}

