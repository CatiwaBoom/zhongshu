package org.cycle.model.ddl;
/**
 * DDL 执行器接口（策略模式）
 * 不同数据库实现此接口以执行建表语句（注意风险：该操作高危，需要权限控制）
 */
public interface BusinessModelDdlExecutor {
    /**
     * 在指定数据源上执行 DDL 语句
     * @param ds 数据源实体（包含 driver/url/username/password）
     * @param ddl 要执行的 SQL（可能包含多条以分号分隔）
     * @return 返回执行结果描述（可包含 success/message）
     */
    ExecResult executeDdl(org.cycle.dataSource.entity.DataSourceEntity ds, String ddl);

    /**
     * 返回此执行器对应的数据库类型标识（例如：mysql, dm）
     */
    String getDbType();

    class ExecResult {
        public boolean success;
        public String message;

        public ExecResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}


