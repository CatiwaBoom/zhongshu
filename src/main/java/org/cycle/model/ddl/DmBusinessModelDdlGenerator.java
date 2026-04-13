package org.cycle.model.ddl;

import org.cycle.model.entity.BusinessFieldEntity;
import org.cycle.model.entity.BusinessModelEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 达梦/Oracle 风格的 DDL 生成器实现（示例）
 */
@Component
public class DmBusinessModelDdlGenerator implements BusinessModelDdlGenerator {

    @Override
    public String generateCreateTable(BusinessModelEntity model, List<BusinessFieldEntity> fields) {
        StringBuilder ddl = new StringBuilder();
        // 表名：若包含 schema 则使用原样，否则大写表名
        String tableName = model.getTableName() == null ? "" : model.getTableName();
        String tbl = tableName.toUpperCase();

        ddl.append("CREATE TABLE ").append(tbl).append(" (\n");
        List<String> pkCols = new ArrayList<>();
        List<String> columnComments = new ArrayList<>();
        List<String> extraIndexes = new ArrayList<>();

        for (BusinessFieldEntity f : fields) {
            String col = f.getColumnName() == null ? "" : f.getColumnName().toUpperCase();
            ddl.append("    ").append(col).append(" ");
            ddl.append(mapToSqlType(f.getDataType(), f.getLength()));
            if (f.getIsNullable() == null || f.getIsNullable() == 0) ddl.append(" NOT NULL");
            if (f.getDefaultValue() != null && !f.getDefaultValue().isEmpty()) {
                ddl.append(" DEFAULT '").append(f.getDefaultValue().replace("'", "''")).append("'");
            }
            ddl.append(",\n");
            if (f.getIsPrimary() != null && f.getIsPrimary() == 1) pkCols.add(col);
            if (f.getFieldComment() != null && !f.getFieldComment().isEmpty()) {
                columnComments.add(col + "|'" + f.getFieldComment().replace("'", "''") + "'");
            }
            // 如果该列为主键，则不为其额外创建索引（避免重复创建与主键冲突的索引）
            boolean isPk = (f.getIsPrimary() != null && f.getIsPrimary() == 1);
            if (!isPk) {
                if (f.getIsUnique() != null && f.getIsUnique() == 1) {
                    extraIndexes.add("CREATE UNIQUE INDEX IDX_" + tbl.replace('.', '_') + "_" + col + " ON " + tbl + " (" + col + ");");
                } else if (f.getIsIndexed() != null && f.getIsIndexed() == 1) {
                    extraIndexes.add("CREATE INDEX IDX_" + tbl.replace('.', '_') + "_" + col + " ON " + tbl + " (" + col + ");");
                }
            }
        }

        if (!pkCols.isEmpty()) {
            ddl.append("    CONSTRAINT ")
               .append("PK_")
               .append(tbl.replace('.', '_'))
               .append(" PRIMARY KEY (")
               .append(String.join(", ", pkCols))
               .append(")\n");
        } else {
            int len = ddl.length();
            if (len >= 2 && ddl.substring(len - 2).equals(",\n")) {
                ddl.delete(len - 2, len);
                ddl.append('\n');
            }
        }

        ddl.append(");\n\n");

        // 表注释
        if (model.getDescription() != null && !model.getDescription().isEmpty()) {
            ddl.append("COMMENT ON TABLE ").append(tbl).append(" IS '")
               .append(model.getDescription().replace("'", "''")).append("';\n\n");
        }

        // 列注释
        for (String cc : columnComments) {
            // cc 格式 col|'comment'
            int sep = cc.indexOf("|'" );
            if (sep > 0) {
                String col = cc.substring(0, sep);
                String comment = cc.substring(sep + 2);
                ddl.append("COMMENT ON COLUMN ").append(tbl).append('.').append(col).append(" IS ")
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
        return "dm"; // 达梦数据库标识
    }

    private String mapToSqlType(String dataType, Integer length) {
        if (dataType == null) return "VARCHAR2(255)";
        String t = dataType.toUpperCase();
        switch (t) {
            case "VARCHAR": case "VARCHAR2": case "STRING": return "VARCHAR2(" + (length == null ? 255 : length) + ")";
            case "INT": case "INTEGER": return "NUMBER(10)";
            case "BIGINT": return "NUMBER(19)";
            case "DECIMAL": case "NUMERIC": return "NUMBER" + (length == null ? "" : ("(" + length + ")"));
            case "DATE": case "DATETIME": return "TIMESTAMP(6)";
            case "BOOLEAN": return "NUMBER(1)";
            case "TEXT": return "CLOB";
            default: return "VARCHAR2(255)";
        }
    }
}

