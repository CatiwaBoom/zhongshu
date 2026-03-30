package org.cycle.dataSource.util;

import java.util.Locale;

public final class DataSourceUtils {

    private DataSourceUtils() {
        // 工具类禁止实例化
    }

    /**
     * 根据URL推断JDBC驱动类名，支持常见数据库
     * @param url 连接URL
     * @return 驱动类名，未识别返回null
     */
    public static String inferDriverByUrl(String url) {
        String u = safeTrim(url);
        if (isBlank(u)) {
            return null;
        }
        u = u.toLowerCase(Locale.ROOT);
        if (u.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (u.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (u.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
        if (u.startsWith("jdbc:sqlserver:")) return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        if (u.startsWith("jdbc:h2:")) return "org.h2.Driver";
        if (u.startsWith("jdbc:dm:")) return "dm.jdbc.driver.DmDriver";
        if (u.startsWith("jdbc:kingbase8:")) return "com.kingbase8.Driver";
        return null;
    }

    /**
     * 安全的字符串修剪，避免NullPointerException
     * @param s 输入字符串
     * @return 修剪后的字符串，输入为null时返回null
     */
    public static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    /**
     * 判断字符串是否为null或仅包含空白字符
     * @param s 输入字符串
     * @return true如果字符串为null或仅包含空白字符，否则false
     */
    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 对URL进行简单掩码处理，隐藏查询参数部分，避免日志泄露敏感信息
     * @param url 输入URL
     * @return 掩码后的URL
     */
    public static String maskUrl(String url) {
        if (url == null) return null;
        int idx = url.indexOf('?');
        return idx > 0 ? url.substring(0, idx) + "?***" : url;
    }
}