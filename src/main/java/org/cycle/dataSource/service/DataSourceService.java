package org.cycle.dataSource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.cycle.dataSource.entity.DataSourceEntity;

public interface DataSourceService extends IService<DataSourceEntity> {
    /**
     * 测试数据源连接
     * @param id 数据源ID
     * @return 是否连接成功
     */
    boolean testConnect(String id);
}