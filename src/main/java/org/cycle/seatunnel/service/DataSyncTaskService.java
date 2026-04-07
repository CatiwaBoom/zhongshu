package org.cycle.seatunnel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.cycle.seatunnel.entity.DataSyncTaskEntity;

public interface DataSyncTaskService extends IService<DataSyncTaskEntity> {
    DataSyncTaskEntity createTask(DataSyncTaskEntity task);

    DataSyncTaskEntity updateTask(String id, DataSyncTaskEntity task);

    String generateConfig(String taskId);

    void refreshPipelineConfig(String taskId);
}
