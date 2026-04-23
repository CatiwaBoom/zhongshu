package org.cycle.model.ddl;

import org.cycle.model.entity.BusinessFieldEntity;
import org.cycle.model.entity.BusinessModelEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 风格的 DDL 生成器实现
 */
@Component
public class MysqlBusinessModelDdlGenerator implements BusinessModelDdlGenerator {

    @Override
    public String generateCreateTable(BusinessModelEntity model, List<BusinessFieldEntity> fields) {
        StringBuilder ddl = new StringBuilder();
        String tableName = model.getTableName() == null ? "" : model.getTableName();
        ddl.append("CREATE TABLE `").append(tableName).append("` (\n");
        List<String> pkCols = new ArrayList<>();
        List<String> extraIndexes = new ArrayList<>();
        // 为了能够在生成主键后避免重复索引，记录每列是否为主键
        for (BusinessFieldEntity f : fields) {
            String colName = f.getColumnName() == null ? "" : f.getColumnName();
            ddl.append("  `").append(colName).append("` ");
            ddl.append(mapToSqlType(f.getDataType(), f.getLength()));
            if (f.getIsNullable() == null || f.getIsNullable() == 0) ddl.append(" NOT NULL");
            // 默认值（尽量保留原样，如果看起来像函数或CURRENT_TIMESTAMP则不加引号）
            if (f.getDefaultValue() != null && !f.getDefaultValue().isEmpty()) {
                String dv = f.getDefaultValue().trim();
                String dvUpper = dv.toUpperCase();
                if ("CURRENT_TIMESTAMP".equals(dvUpper) || dvUpper.startsWith("CURRENT_TIMESTAMP")) {
                    ddl.append(" DEFAULT ").append(dv);
                } else {
                    ddl.append(" DEFAULT '").append(dv.replace("'", "''")).append("'");
                }
            }
            if (f.getFieldComment() != null && !f.getFieldComment().isEmpty()) ddl.append(" COMMENT '").append(f.getFieldComment().replace("'", "''")).append("'");
            ddl.append(",\n");
            boolean isPk = (f.getIsPrimary() != null && f.getIsPrimary() == 1);
            if (isPk) pkCols.add("`" + colName + "`");
            // 如果是主键列，则不再为其额外创建唯一/普通索引，避免重复
            if (!isPk) {
                if (f.getIsUnique() != null && f.getIsUnique() == 1) {
                    extraIndexes.add("CREATE UNIQUE INDEX `UQ_" + tableName + "_" + colName + "` ON `" + tableName + "`(`" + colName + "`);");
                } else if (f.getIsIndexed() != null && f.getIsIndexed() == 1) {
                    extraIndexes.add("CREATE INDEX `IDX_" + tableName + "_" + colName + "` ON `" + tableName + "`(`" + colName + "`);");
                }
            }
        }
        if (!pkCols.isEmpty()) {
            ddl.append("  PRIMARY KEY (").append(String.join(", ", pkCols)).append(")\n");
        } else {
            int len = ddl.length();
            if (len >= 2 && ddl.substring(len - 2).equals(",\n")) {
                ddl.delete(len - 2, len);
                ddl.append('\n');
            }
        }
        // 表级注释（MySQL 在 CREATE TABLE 末尾支持 COMMENT）
        String tableCommentSuffix = "";
        if (model.getDescription() != null && !model.getDescription().isEmpty()) {
            tableCommentSuffix = " COMMENT '" + model.getDescription().replace("'", "''") + "'";
        }
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4").append(tableCommentSuffix).append(";\n");
        // 追加索引语句
        for (String idx : extraIndexes) {
            ddl.append(idx).append("\n");
        }
        return ddl.toString();
    }

    @Override
    public String getDbType() {
        return "mysql";
    }

    private String mapToSqlType(String dataType, Integer length) {
        if (dataType == null) return "VARCHAR(255)";
        String t = dataType.toUpperCase();
        switch (t) {
            case "VARCHAR": case "STRING": return "VARCHAR(" + (length == null ? 255 : length) + ")";
            case "INT": case "INTEGER": return "INT";
            case "BIGINT": return "BIGINT";
            case "DECIMAL": case "NUMERIC": return "DECIMAL" + (length == null ? "" : ("(" + length + ")"));
            case "DATE": return "DATE";
            case "DATETIME": return "DATETIME";
            case "BOOLEAN": return "TINYINT(1)";
            case "TEXT": return "TEXT";
            default: return "VARCHAR(255)";
        }
    }
}

