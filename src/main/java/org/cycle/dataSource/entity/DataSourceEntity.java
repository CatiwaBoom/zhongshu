// java
package org.cycle.dataSource.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

@Data
@TableName("data_source")
public class DataSourceEntity extends BaseEntity {
    /**
     * 数据源名称
     */
    private String name;
    /**
     * 驱动类名
     */
    private String driverClassName;
    /**
     * 连接URL
     */
    private String url;
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;
    /**
     * 状态：1\-启用，0\-禁用
     */
    private Integer status;

    /**
     * 连通性：1\-链接，2\-断开
     */
    private Integer connectivity;
}