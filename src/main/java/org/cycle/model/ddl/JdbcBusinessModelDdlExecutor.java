package org.cycle.model.ddl;

import lombok.extern.slf4j.Slf4j;
import org.cycle.dataSource.util.DriverPathUtils;
import org.cycle.dataSource.util.JdbcDriverLoader;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 基于 JDBC 的通用 DDL 执行器实现
 */
@Slf4j
@Component
public class JdbcBusinessModelDdlExecutor implements BusinessModelDdlExecutor {

    @Override
    public ExecResult executeDdl(org.cycle.dataSource.entity.DataSourceEntity ds, String ddl) {
        if (ds == null) return new ExecResult(false, "数据源为空");
        String driver = ds.getDriverClassName();
        String url = ds.getUrl();
        String user = ds.getUsername();
        String pwd = ds.getPassword();

        try {
            // 加载驱动（项目提供工具，会尝试本地 classpath 和 drivers 目录）
            if (driver != null && !driver.trim().isEmpty()) {
                JdbcDriverLoader.loadDriver(driver, DriverPathUtils.getDriversDir());
            }

            try (Connection conn = DriverManager.getConnection(url, user, pwd)) {
                conn.setAutoCommit(false);
                try (Statement st = conn.createStatement()) {
                    // 简单按分号分割 SQL 并逐条执行
                    String[] parts = ddl.split(";\\s*");
                    for (String p : parts) {
                        if (p == null) continue;
                        String sql = p.trim();
                        if (sql.isEmpty()) continue;
                        st.execute(sql);
                    }
                    conn.commit();
                    return new ExecResult(true, "执行成功");
                } catch (Exception e) {
                    try { conn.rollback(); } catch (Exception ex) { log.warn("回滚失败", ex); }
                    log.error("执行 DDL 失败", e);
                    return new ExecResult(false, "执行失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("准备执行 DDL 失败", e);
            return new ExecResult(false, "准备执行失败: " + e.getMessage());
        }
    }

    @Override
    public String getDbType() {
        return "*"; // 通用执行器，作为兜底
    }
}


