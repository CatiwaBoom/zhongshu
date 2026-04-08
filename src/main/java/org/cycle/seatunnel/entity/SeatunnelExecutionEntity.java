package org.cycle.seatunnel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.sql.Timestamp;

@Data
@TableName("ST_EXECUTION")
public class SeatunnelExecutionEntity extends BaseEntity {
    private String pipelineId;
    private String status;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private Integer exitCode;
    private String logPath;
    private String configPath;
    private String seatunnelJobId;
    private String errorMessage;
}
