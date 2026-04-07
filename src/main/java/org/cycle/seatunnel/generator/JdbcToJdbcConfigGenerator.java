package org.cycle.seatunnel.generator;

import org.cycle.dataSource.entity.DataSourceEntity;
import org.cycle.seatunnel.entity.DataSyncTaskEntity;

public class JdbcToJdbcConfigGenerator {

    public String generate(DataSyncTaskEntity task, DataSourceEntity source, DataSourceEntity sink) {
        int parallelism = task.getParallelism() == null ? 1 : task.getParallelism();
        String saveMode = safeUpper(task.getSaveMode());
        if (saveMode.isEmpty()) {
            saveMode = "APPEND_DATA";
        }

        String sourceTablePath = buildTablePath(task.getSourceSchema(), task.getSourceTable());
        String sinkTablePath = buildTablePath(task.getSinkSchema(), task.getSinkTable());
        String sinkDatabase = resolveDatabase(task.getSinkSchema(), sink.getUrl());

        StringBuilder sb = new StringBuilder();
        sb.append("env {\n");
        sb.append("  parallelism = ").append(parallelism).append('\n');
        sb.append("  job.mode = \"BATCH\"\n");
        sb.append("}\n\n");

        sb.append("source {\n");
        sb.append("  Jdbc {\n");
        sb.append("    driver = \"").append(escape(source.getDriverClassName())).append("\"\n");
        sb.append("    url = \"").append(escape(source.getUrl())).append("\"\n");
        if (!safeTrim(source.getUsername()).isEmpty()) {
            sb.append("    user = \"").append(escape(source.getUsername())).append("\"\n");
        }
        if (!safeTrim(source.getPassword()).isEmpty()) {
            sb.append("    password = \"").append(escape(source.getPassword())).append("\"\n");
        }
        sb.append("    table_list = [\n");
        sb.append("      { table_path = \"").append(escape(sourceTablePath)).append("\" }\n");
        sb.append("    ]\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("transform {\n");
        sb.append("}\n\n");

        sb.append("sink {\n");
        sb.append("  jdbc {\n");
        sb.append("    url = \"").append(escape(sink.getUrl())).append("\"\n");
        sb.append("    driver = \"").append(escape(sink.getDriverClassName())).append("\"\n");
        if (!safeTrim(sink.getUsername()).isEmpty()) {
            sb.append("    user = \"").append(escape(sink.getUsername())).append("\"\n");
        }
        if (!safeTrim(sink.getPassword()).isEmpty()) {
            sb.append("    password = \"").append(escape(sink.getPassword())).append("\"\n");
        }
        sb.append("    generate_sink_sql = true\n");
        sb.append("    database = \"").append(escape(sinkDatabase)).append("\"\n");
        sb.append("    table = \"").append(escape(sinkTablePath)).append("\"\n");
        sb.append("    schema_save_mode = \"CREATE_SCHEMA_WHEN_NOT_EXIST\"\n");
        sb.append("    data_save_mode = \"").append(escape(saveMode)).append("\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String resolveDatabase(String sinkSchema, String sinkUrl) {
        String sch = safeTrim(sinkSchema);
        if (!sch.isEmpty()) {
            return sch;
        }
        String url = safeTrim(sinkUrl);
        int idx = url.indexOf("://");
        if (idx >= 0) {
            int slash = url.indexOf('/', idx + 3);
            if (slash >= 0) {
                int end = url.indexOf('?', slash + 1);
                String db = end > slash ? url.substring(slash + 1, end) : url.substring(slash + 1);
                db = safeTrim(db);
                if (!db.isEmpty()) {
                    return db;
                }
            }
        }
        throw new IllegalArgumentException("sink database is required when generate_sink_sql=true, please provide sinkSchema or use url with database name");
    }

    private String buildTablePath(String schema, String table) {
        String sch = safeTrim(schema);
        String tbl = safeTrim(table);
        if (sch.isEmpty()) {
            return tbl;
        }
        return sch + "." + tbl;
    }

    private String escape(String value) {
        return safeTrim(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeUpper(String value) {
        return safeTrim(value).toUpperCase();
    }
}

