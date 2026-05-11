package org.cycle.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.cycle.system.entity.SystemEntity;

import java.util.List;

/**
 * 外部系统服务接口
 */
public interface SystemService extends IService<SystemEntity> {

    /**
     * 测试地址+端口是否可连通（实时检查，不持久化）
     *
     * @param address 地址（IP或域名）
     * @param port    端口号
     * @param timeoutMillis 连接超时时间（毫秒）
     * @return true 表示可连通，false 表示不可连通
     */
    boolean checkStatus(String address, Integer port, int timeoutMillis);

    /**
     * 根据多个 id 批量查询实体
     */
    List<SystemEntity> listByIds(List<String> ids);
}

