package org.cycle.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.cycle.system.entity.SystemApiEntity;
import org.cycle.system.mapper.SystemApiMapper;
import org.cycle.system.service.SystemApiService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SystemApiServiceImpl extends ServiceImpl<SystemApiMapper, SystemApiEntity> implements SystemApiService {

    @Override
    public List<SystemApiEntity> listBySystemId(String systemId) {
        if (systemId == null || systemId.trim().isEmpty()) return java.util.Collections.emptyList();
        QueryWrapper<SystemApiEntity> qw = new QueryWrapper<>();
        qw.eq("system_id", systemId);
        qw.select("id", "system_id", "api_name", "method", "url", "description", "created_at", "updated_at", "attachment_ids");
        return baseMapper.selectList(qw);
    }
}

