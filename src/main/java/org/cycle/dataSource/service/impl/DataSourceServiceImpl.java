package org.cycle.dataSource.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.cycle.dataSource.entity.DataSourceEntity;
import org.cycle.dataSource.mapper.DataSourceMapper;
import org.cycle.dataSource.service.DataSourceService;
import org.cycle.dataSource.util.JdbcDriverLoader;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.cycle.dataSource.util.DataSourceUtils.*;

@Slf4j
@Service
public class DataSourceServiceImpl extends ServiceImpl<DataSourceMapper, DataSourceEntity> implements DataSourceService {

    @Override
    public boolean testConnect(String id) {
        DataSourceEntity ds = getById(id);
        if (ds == null) {
            log.warn("测试连接失败：数据源不存在，id={}", id);
            return false;
        }

        String driver = safeTrim(ds.getDriverClassName());
        String url = safeTrim(ds.getUrl());
        String username = safeTrim(ds.getUsername());
        String password = ds.getPassword();

        if (isBlank(url)) {
            log.warn("测试连接失败：URL为空，id={}", id);
            markConnectivity(ds, 2);
            return false;
        }

        try {
            // 1\) 优先使用配置驱动；2\) 未配置则根据URL推断
            String resolvedDriver = !isBlank(driver) ? driver : inferDriverByUrl(url);
            if (!isBlank(resolvedDriver)) {
                JdbcDriverLoader.loadDriver(resolvedDriver, org.cycle.dataSource.util.DriverPathUtils.getDriversDir());
            } else {
                log.warn("未识别到可用驱动，继续尝试由JDBC自动加载，id={}, url={}", id, maskUrl(url));
            }

            try (Connection ignored = DriverManager.getConnection(url, username, password)) {
                markConnectivity(ds, 1);
                return true;
            }
        } catch (Exception e) {
            log.error("测试数据源连接失败，id={}, driver={}, url={}", id, driver, maskUrl(url), e);
            markConnectivity(ds, 2);
            return false;
        }
    }

    /**
     * 更新数据源连通性状态，1\-连接成功，2\-连接失败
     * @param ds 数据源实体
     * @param connectivity 连通性状态
     */
    private void markConnectivity(DataSourceEntity ds, int connectivity) {
        try {
            DataSourceEntity update = new DataSourceEntity();
            update.setId(ds.getId());
            update.setConnectivity(connectivity);
            updateById(update);
        } catch (Exception e) {
            log.warn("更新连通性状态失败，id={}, connectivity={}", ds.getId(), connectivity, e);
        }
    }
}