package org.cycle.model.ddl;

import org.cycle.model.entity.BusinessFieldEntity;
import org.cycle.model.entity.BusinessModelEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL 风格的 DDL 生成器实现（示例）
 */
@Component
public class PostgresBusinessModelDdlGenerator implements BusinessModelDdlGenerator {

    @Override
    public String generateCreateTable(BusinessModelEntity model, List<BusinessFieldEntity> fields) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE \"").append(model.getTableName()).append("\" (\n");
        List<String> pkCols = new ArrayList<>();
        List<String> extraIndexes = new ArrayList<>();
        List<String> columnComments = new ArrayList<>();
        for (BusinessFieldEntity f : fields) {
            ddl.append("  \"").append(f.getColumnName()).append("\" ");
            ddl.append(mapToSqlType(f.getDataType(), f.getLength()));
            if (f.getIsNullable() == null || f.getIsNullable() == 0) ddl.append(" NOT NULL");
            if (f.getDefaultValue() != null && !f.getDefaultValue().isEmpty()) {
                ddl.append(" DEFAULT '").append(f.getDefaultValue().replace("'", "''")).append("'");
            }
            if (f.getFieldComment() != null && !f.getFieldComment().isEmpty()) ddl.append(" -- ").append(f.getFieldComment());
            ddl.append(",\n");
            if (f.getIsPrimary() != null && f.getIsPrimary() == 1) pkCols.add('"' + f.getColumnName() + '"');
            if (f.getFieldComment() != null && !f.getFieldComment().isEmpty()) {
                columnComments.add(f.getColumnName() + "|'" + f.getFieldComment().replace("'", "''") + "'");
            }
            if (f.getIsUnique() != null && f.getIsUnique() == 1) {
                extraIndexes.add("CREATE UNIQUE INDEX \"UQ_" + model.getTableName() + "_" + f.getColumnName() + "\" ON \"" + model.getTableName() + "\"(\"" + f.getColumnName() + "\");");
            } else if (f.getIsIndexed() != null && f.getIsIndexed() == 1) {
                extraIndexes.add("CREATE INDEX \"IDX_" + model.getTableName() + "_" + f.getColumnName() + "\" ON \"" + model.getTableName() + "\"(\"" + f.getColumnName() + "\");");
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
        ddl.append(");\n\n");
        // 列注释
        for (String cc : columnComments) {
            int sep = cc.indexOf("|'" );
            if (sep > 0) {
                String col = cc.substring(0, sep);
                String comment = cc.substring(sep + 2);
                ddl.append("COMMENT ON COLUMN \"").append(model.getTableName()).append("\".").append(col).append(" IS ")
                   .append(comment).append(";\n");
            }
        }
        // 追加索引语句
        for (String idx : extraIndexes) {
            ddl.append(idx).append("\n");
        }
        return ddl.toString();
    }

    @Override
    public String getDbType() {
        return "postgres";
    }

    private String mapToSqlType(String dataType, Integer length) {
        if (dataType == null) return "varchar(255)";
        String t = dataType.toUpperCase();
        switch (t) {
            case "VARCHAR": case "STRING": return "varchar(" + (length == null ? 255 : length) + ")";
            case "INT": case "INTEGER": return "integer";
            case "BIGINT": return "bigint";
            case "DECIMAL": case "NUMERIC": return "numeric" + (length == null ? "" : ("(" + length + ")"));
            case "DATE": return "date";
            case "DATETIME": return "timestamp";
            case "BOOLEAN": return "boolean";
            case "TEXT": return "text";
            default: return "varchar(255)";
        }
    }
}

