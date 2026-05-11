package org.cycle.system.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.cycle.system.entity.SystemEntity;
import org.cycle.system.mapper.SystemMapper;
import org.cycle.system.service.SystemService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

@Slf4j
@Service
public class SystemServiceImpl extends ServiceImpl<SystemMapper, SystemEntity> implements SystemService {

    @Override
    public boolean checkStatus(String address, Integer port, int timeoutMillis) {
        if (address == null || address.trim().isEmpty() || port == null) return false;
        // 使用 try-with-resources 自动关闭 Socket，避免资源泄露
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), timeoutMillis);
            return true;
        } catch (Exception e) {
            log.debug("checkStatus failed for {}:{} -> {}", address, port, e.getMessage());
            return false;
        }
    }

    @Override
    public List<SystemEntity> listByIds(List<String> ids) {
        // 调用父类实现以避免递归
        return super.listByIds(ids);
    }
}


