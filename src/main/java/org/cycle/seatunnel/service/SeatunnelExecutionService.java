package org.cycle.seatunnel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.cycle.seatunnel.entity.SeatunnelExecutionEntity;

public interface SeatunnelExecutionService extends IService<SeatunnelExecutionEntity> {
    SeatunnelExecutionEntity start(String pipelineId);

    boolean stop(String executionId);

    String tailLog(String executionId, int maxLines);
}
