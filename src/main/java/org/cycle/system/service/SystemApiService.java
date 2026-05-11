package org.cycle.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.cycle.system.entity.SystemApiEntity;

import java.util.List;

public interface SystemApiService extends IService<SystemApiEntity> {

    /** 根据 systemId 查询所有接口 */
    List<SystemApiEntity> listBySystemId(String systemId);

}

