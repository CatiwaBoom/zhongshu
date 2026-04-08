package org.cycle.seatunnel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

@Data
@TableName("ST_DATASYNC_TASK")
public class DataSyncTaskEntity extends BaseEntity {
    private String name;
    private String sourceDsId;
    private String sourceSchema;
    private String sourceTable;
    private String sinkDsId;
    private String sinkSchema;
    private String sinkTable;
    private String saveMode;
    private Integer parallelism;
    private String pipelineId;
    private Integer status;
    private String remark;
}
