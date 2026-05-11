package org.cycle.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

/**
 * 外部系统注册实体
 * 表名：sys_external_system
 * 注意：系统状态不持久化到数据库（通过telnet/socket检测实时返回给前端）
 */
@Data
@TableName("sys_external_system")
public class SystemEntity extends BaseEntity implements Serializable {

    /**
     * 系统名称
     */
    private String name;

    /**
     * 系统描述
     */
    private String description;

    /**
     * 地址（IP或域名）
     */
    private String address;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 系统编码（业务使用）
     */
    private String systemCode;

    /**
     * 附件ID列表（逗号分隔的文件对象ID），用于关联 file 模块的文件对象
     * 前端可通过 file 模块的接口上传并将文件ID传回保存到此字段
     */
    private String attachmentIds;
}

