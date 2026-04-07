package org.cycle.dataSource.dto;

import lombok.Data;

@Data
public class ColumnMetaDTO {
    private String name;
    private String typeName;
    private Integer dataType;
    private Integer columnSize;
    private Integer decimalDigits;
    private Integer nullable;
    private Integer ordinalPosition;
    private Boolean primaryKey;
}
