package org.cycle.model.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.cycle.model.entity.BusinessModelEntity;
import org.cycle.model.mapper.BusinessModelMapper;
import org.cycle.model.service.BusinessModelService;

/**
 * 业务模型服务实现类
 */
@Service
public class BusinessModelServiceImpl extends ServiceImpl<BusinessModelMapper, BusinessModelEntity> implements BusinessModelService {

}

