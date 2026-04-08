package org.cycle.seatunnel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

@Data
@TableName("ST_PIPELINE")
public class SeatunnelPipelineEntity extends BaseEntity {
    private String name;
    private String configFormat;
    private String configContent;
    private String execMode;
    private String clusterName;
    private Integer status;
    private String remark;
}
