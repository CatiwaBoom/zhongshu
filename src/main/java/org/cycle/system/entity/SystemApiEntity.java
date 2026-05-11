package org.cycle.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

/**
 * 系统接口实体
 * 表名：sys_system_api
 */
@Data
@TableName("sys_system_api")
public class SystemApiEntity extends BaseEntity implements Serializable {

    /** 关联系统ID */
    private String systemId;

    /** 接口名称 */
    private String apiName;

    /** 请求类型 GET/POST */
    private String method;

    /** 接口地址 */
    private String url;

    /** 接口详情 */
    private String description;

    /** 请求示例 */
    private String requestExample;

    /** 响应示例 */
    private String responseExample;

    /** 请求字段注释 */
    private String reqFieldComment;

    /** 响应字段注释 */
    private String resFieldComment;

    /** 附件ID列表（逗号分隔，引用 sys_file_object.id） */
    private String attachmentIds;
}

