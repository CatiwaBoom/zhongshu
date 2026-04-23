package org.cycle.system.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 外部系统新增/更新请求 DTO
 */
@Data
public class SystemDto {

    private String id;

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "地址不能为空")
    private String address;

    @NotNull(message = "端口不能为空")
    private Integer port;

    @NotBlank(message = "系统编码不能为空")
    private String systemCode;

    /**
     * 附件ID列表，前端上传后回传的 fileId 列表
     */
    private List<String> attachmentIds;
}

