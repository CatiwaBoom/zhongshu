package org.cycle.system.dto;

import lombok.Data;
import org.cycle.file.dto.FileObjectVO;

import java.util.List;

/**
 * 外部系统对外展示对象
 */
@Data
public class SystemVO {
    private String id;
    private String name;
    private String description;
    private String address;
    private Integer port;
    private String systemCode;

    /**
     * 附件对象列表（来自 file 模块）
     */
    private List<FileObjectVO> attachments;

    /**
     * 实时检测到的系统状态（前端显示字段，非持久化）
     */
    private Boolean status;
}

