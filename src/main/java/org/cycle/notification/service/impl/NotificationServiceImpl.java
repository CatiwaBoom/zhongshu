package org.cycle.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.notification.dto.NotificationItemVO;
import org.cycle.notification.dto.NotificationListResponse;
import org.cycle.notification.dto.NotificationPushEvent;
import org.cycle.notification.dto.NotificationSendRequest;
import org.cycle.notification.entity.NotificationEntity;
import org.cycle.notification.mapper.NotificationMapper;
import org.cycle.notification.service.NotificationService;
import org.cycle.user.entity.UserEntity;
import org.cycle.user.mapper.UserMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, NotificationEntity> implements NotificationService {

    public static final String REDIS_CHANNEL = "notification:push";

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void sendToUsers(String senderId, NotificationSendRequest request) {
        List<String> targetUserIds = resolveTargetUserIds(senderId, request);
        if (targetUserIds.isEmpty()) {
            return;
        }

        String title = request.getTitle().trim();
        String content = request.getContent().trim();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        for (String userId : targetUserIds) {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setUserId(userId);
            entity.setTitle(title);
            entity.setContent(content);
            entity.setBizType(request.getBizType());
            entity.setBizId(request.getBizId());
            entity.setIsRead(0);
            entity.setCreatedBy(senderId);
            entity.setUpdatedBy(senderId);
            boolean saved = this.save(entity);
            if (!saved) {
                log.warn("站内信入库失败，userId={}，title={}", userId, title);
                continue;
            }

            // 这里实时回算未读数，保证前端红点与数据库状态一致
            long unreadCount = countUnread(userId);
            NotificationPushEvent pushEvent = new NotificationPushEvent();
            pushEvent.setUserId(userId);
            pushEvent.setMessageId(entity.getId());
            pushEvent.setTitle(title);
            pushEvent.setContent(content);
            pushEvent.setBizType(entity.getBizType());
            pushEvent.setBizId(entity.getBizId());
            pushEvent.setCreatedAt(now);
            pushEvent.setUnreadCount(unreadCount);

            publishPushEvent(pushEvent);
        }
    }

    @Override
    public NotificationListResponse listMine(String userId, long page, long size, String keyword, Integer isRead, String bizType) {
        Page<NotificationEntity> pager = new Page<>(Math.max(1, page), Math.max(1, size));
        QueryWrapper<NotificationEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);

        if (isRead != null && (isRead == 0 || isRead == 1)) {
            qw.eq("is_read", isRead);
        }
        if (StringUtils.hasText(bizType)) {
            qw.eq("biz_type", bizType.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 标题与内容都支持模糊搜索，便于收件箱快速检索
            qw.and(w -> w.like("title", kw).or().like("content", kw));
        }

        qw.orderByDesc("created_at");

        Page<NotificationEntity> pageData = this.page(pager, qw);

        NotificationListResponse response = new NotificationListResponse();
        response.setCurrent(pageData.getCurrent());
        response.setSize(pageData.getSize());
        response.setTotal(pageData.getTotal());
        response.setRecords(pageData.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return response;
    }

    @Override
    public long countUnread(String userId) {
        QueryWrapper<NotificationEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("is_read", 0);
        return this.count(qw);
    }

    @Override
    public boolean markRead(String userId, String notificationId) {
        UpdateWrapper<NotificationEntity> uw = new UpdateWrapper<>();
        uw.eq("id", notificationId)
                .eq("user_id", userId)
                .eq("is_read", 0)
                .set("is_read", 1)
                .set("read_at", new Timestamp(System.currentTimeMillis()));
        return this.update(uw);
    }

    @Override
    public int markAllRead(String userId) {
        UpdateWrapper<NotificationEntity> uw = new UpdateWrapper<>();
        uw.eq("user_id", userId)
                .eq("is_read", 0)
                .set("is_read", 1)
                .set("read_at", new Timestamp(System.currentTimeMillis()));
        this.update(uw);
        return 1;
    }

    @Override
    public List<String> resolveTargetUserIds(String senderId, NotificationSendRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }

        if (Boolean.TRUE.equals(request.getSendAll())) {
            QueryWrapper<UserEntity> qw = new QueryWrapper<>();
            qw.eq("status", 1).select("id");
            List<UserEntity> users = userMapper.selectList(qw);
            return users.stream().map(UserEntity::getId).filter(StringUtils::hasText).collect(Collectors.toList());
        }

        List<String> reqUserIds = request.getTargetUserIds();
        if (reqUserIds == null || reqUserIds.isEmpty()) {
            return StringUtils.hasText(senderId) ? Collections.singletonList(senderId) : Collections.emptyList();
        }

        // 去重并过滤空值，避免插入重复站内信
        Set<String> uniq = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String userId : reqUserIds) {
            if (!StringUtils.hasText(userId) || uniq.contains(userId)) {
                continue;
            }
            uniq.add(userId);
            result.add(userId);
        }
        return result;
    }

    private NotificationItemVO toVO(NotificationEntity entity) {
        NotificationItemVO vo = new NotificationItemVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setIsRead(entity.getIsRead());
        vo.setReadAt(entity.getReadAt());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private void publishPushEvent(NotificationPushEvent pushEvent) {
        try {
            String payload = objectMapper.writeValueAsString(pushEvent);
            stringRedisTemplate.convertAndSend(REDIS_CHANNEL, payload);
        } catch (DataAccessException redisEx) {
            // Redis 异常不影响主流程，消息已落库，客户端可通过轮询列表兜底看到消息
            log.error("Redis发布站内信失败", redisEx);
        } catch (Exception e) {
            log.error("序列化站内信事件失败", e);
        }
    }
}
