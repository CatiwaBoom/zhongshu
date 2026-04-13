package org.cycle.model.ddl;

import org.cycle.model.entity.BusinessFieldEntity;
import org.cycle.model.entity.BusinessModelEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 风格的 DDL 生成器实现（示例）
 */
@Component
public class MysqlBusinessModelDdlGenerator implements BusinessModelDdlGenerator {

    @Override
    public String generateCreateTable(BusinessModelEntity model, List<BusinessFieldEntity> fields) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE `").append(model.getTableName()).append("` (\n");
        List<String> pkCols = new ArrayList<>();
        List<String> extraIndexes = new ArrayList<>();
        for (BusinessFieldEntity f : fields) {
            ddl.append("  `").append(f.getColumnName()).append("` ");
            ddl.append(mapToSqlType(f.getDataType(), f.getLength()));
            if (f.getIsNullable() == null || f.getIsNullable() == 0) ddl.append(" NOT NULL");
            // 默认值（简单字符串形式，单引号处理）
            if (f.getDefaultValue() != null && !f.getDefaultValue().isEmpty()) {
                ddl.append(" DEFAULT '").append(f.getDefaultValue().replace("'", "''")).append("'");
            }
            if (f.getFieldComment() != null && !f.getFieldComment().isEmpty()) ddl.append(" COMMENT '").append(f.getFieldComment().replace("'", "''")).append("'");
            ddl.append(",\n");
            if (f.getIsPrimary() != null && f.getIsPrimary() == 1) pkCols.add("`" + f.getColumnName() + "`");
            // 唯一/索引信息在表外生成
            if (f.getIsUnique() != null && f.getIsUnique() == 1) {
                extraIndexes.add("CREATE UNIQUE INDEX `UQ_" + model.getTableName() + "_" + f.getColumnName() + "` ON `" + model.getTableName() + "`(`" + f.getColumnName() + "`);");
            } else if (f.getIsIndexed() != null && f.getIsIndexed() == 1) {
                extraIndexes.add("CREATE INDEX `IDX_" + model.getTableName() + "_" + f.getColumnName() + "` ON `" + model.getTableName() + "`(`" + f.getColumnName() + "`);");
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
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n");
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

