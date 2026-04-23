package org.cycle.seatunnel.generator;

import org.cycle.dataSource.entity.DataSourceEntity;
import org.cycle.seatunnel.entity.DataSyncTaskEntity;

public class JdbcCdcConfigGenerator {

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
        sb.append("  parallelism = " + parallelism + "\n");
        sb.append("  job.mode = \"STREAMING\"\n");
        sb.append("  checkpoint.enabled = -1\n");
        sb.append("}\n\n");

        sb.append("source {\n");
        if (isMysql(source)) {
            generateMysqlCdcSource(sb, task, source, sourceTablePath);
        } else if (isDm(source)) {
            generateDmCdcSource(sb, task, source, sourceTablePath);
        } else {
            throw new IllegalArgumentException("Unsupported CDC source database: " + source.getDriverClassName());
        }
        sb.append("}\n\n");

        sb.append("transform {\n");
        sb.append("}\n\n");

        sb.append("sink {\n");
        generateJdbcSink(sb, sink, sinkDatabase, sinkTablePath, saveMode);
        sb.append("}\n");

        return sb.toString();
    }

    private boolean isMysql(DataSourceEntity source) {
        String driver = safeTrim(source.getDriverClassName());
        return driver.contains("mysql") || driver.contains("MariaDB");
    }

    private boolean isDm(DataSourceEntity source) {
        String driver = safeTrim(source.getDriverClassName());
        return driver.contains("dm") || driver.contains("DM");
    }

    private void generateMysqlCdcSource(StringBuilder sb, DataSyncTaskEntity task, DataSourceEntity source, String tablePath) {
        String host = extractHost(source.getUrl());
        int port = extractPort(source.getUrl());
        String database = extractDatabase(source.getUrl());
        // 提取纯表名，移除 schema 前缀
        String pureTableName = tablePath;
        if (tablePath.contains(".")) {
            pureTableName = tablePath.substring(tablePath.lastIndexOf(".") + 1);
        }
        sb.append("  MySQL-CDC {\n");
        sb.append("    hostname = \"" + host + "\"\n");
        sb.append("    port = " + port + "\n");
        sb.append("    base-url = \"jdbc:mysql://" + host + ":" + port + "/" + database + "\"\n");
        sb.append("    username = \"" + escape(source.getUsername()) + "\"\n");
        sb.append("    password = \"" + escape(source.getPassword()) + "\"\n");
        sb.append("    database-name = \"" + database + "\"\n");
        sb.append("    table-names = [\"" + escape(database) + "." + escape(pureTableName) + "\"]\n");
        sb.append("    server-id = " + (task.getCdcServerId() != null ? task.getCdcServerId() : 5401) + "\n");
        sb.append("    binlog-start-position = \"" + (task.getCdcStartPosition() != null ? task.getCdcStartPosition() : "latest") + "\"\n");
        sb.append("  }\n");
    }

    private void generateDmCdcSource(StringBuilder sb, DataSyncTaskEntity task, DataSourceEntity source, String tablePath) {
        sb.append("  DM-CDC {\n");
        sb.append("    url = \"" + escape(source.getUrl()) + "\"\n");
        sb.append("    username = \"" + escape(source.getUsername()) + "\"\n");
        sb.append("    password = \"" + escape(source.getPassword()) + "\"\n");
        sb.append("    table-list = [\"" + escape(tablePath) + "\"]\n");
        sb.append("    start-position = \"" + (task.getCdcStartPosition() != null ? task.getCdcStartPosition() : "latest") + "\"\n");
        sb.append("  }\n");
    }

    private void generateJdbcSink(StringBuilder sb, DataSourceEntity sink, String database, String tablePath, String saveMode) {
        sb.append("  jdbc {\n");
        sb.append("    url = \"" + escape(sink.getUrl()) + "\"\n");
        sb.append("    driver = \"" + escape(sink.getDriverClassName()) + "\"\n");
        if (!safeTrim(sink.getUsername()).isEmpty()) {
            sb.append("    user = \"" + escape(sink.getUsername()) + "\"\n");
        }
        if (!safeTrim(sink.getPassword()).isEmpty()) {
            sb.append("    password = \"" + escape(sink.getPassword()) + "\"\n");
        }
        sb.append("    generate_sink_sql = true\n");
        sb.append("    database = \"" + escape(database) + "\"\n");
        sb.append("    table = \"" + escape(tablePath) + "\"\n");
        sb.append("    schema_save_mode = \"CREATE_SCHEMA_WHEN_NOT_EXIST\"\n");
        sb.append("    data_save_mode = \"" + escape(saveMode) + "\"\n");
        sb.append("  }\n");
    }

    private String extractHost(String url) {
        String safeUrl = safeTrim(url);
        if (safeUrl.isEmpty()) {
            return "localhost";
        }
        int idx = safeUrl.indexOf("://");
        if (idx >= 0) {
            int end = safeUrl.indexOf(':', idx + 3);
            if (end >= 0) {
                return safeUrl.substring(idx + 3, end);
            }
            end = safeUrl.indexOf('/', idx + 3);
            if (end >= 0) {
                return safeUrl.substring(idx + 3, end);
            }
            return safeUrl.substring(idx + 3);
        }
        return "localhost";
    }

    private int extractPort(String url) {
        String safeUrl = safeTrim(url);
        if (safeUrl.isEmpty()) {
            return 3306;
        }
        int idx = safeUrl.indexOf("://");
        if (idx >= 0) {
            int start = safeUrl.indexOf(':', idx + 3);
            if (start >= 0) {
                int end = safeUrl.indexOf('/', start + 1);
                if (end >= 0) {
                    try {
                        return Integer.parseInt(safeUrl.substring(start + 1, end));
                    } catch (NumberFormatException e) {
                        // 解析失败，返回默认值
                    }
                } else {
                    try {
                        return Integer.parseInt(safeUrl.substring(start + 1));
                    } catch (NumberFormatException e) {
                        // 解析失败，返回默认值
                    }
                }
            }
        }
        // 根据数据库类型返回默认端口
        if (safeUrl.contains("mysql")) {
            return 3306;
        } else if (safeUrl.contains("dm")) {
            return 5236;
        }
        return 3306;
    }

    private String extractDatabase(String url) {
        String safeUrl = safeTrim(url);
        if (safeUrl.isEmpty()) {
            return "";
        }
        int idx = safeUrl.indexOf("://");
        if (idx >= 0) {
            int slash = safeUrl.indexOf('/', idx + 3);
            if (slash >= 0) {
                int end = safeUrl.indexOf('?', slash + 1);
                if (end > slash) {
                    return safeUrl.substring(slash + 1, end);
                } else {
                    return safeUrl.substring(slash + 1);
                }
            }
        }
        return "";
    }

    private String resolveDatabase(String sinkSchema, String sinkUrl) {
        String sch = safeTrim(sinkSchema);
        if (!sch.isEmpty()) {
            return sch;
        }
        return extractDatabase(sinkUrl);
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