package org.cycle.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class SystemApiDto {

    private String id;

    @NotBlank(message = "systemId 不能为空")
    private String systemId;

    @NotBlank(message = "apiName 不能为空")
    private String apiName;

    @NotBlank(message = "method 不能为空")
    private String method;

    @NotBlank(message = "url 不能为空")
    private String url;

    private String description;

    private String requestExample;

    private String responseExample;

    private String reqFieldComment;

    private String resFieldComment;

    /** 附件ID列表（单个或逗号分隔，引用文件对象ID） */
    private String attachmentIds;
}

