package org.cycle.dataSource.util;

import java.io.File;

/**
 * 工具：统一获取 drivers 目录路径（从系统属性或环境变量读取），避免代码中硬编码路径
 */
public final class DriverPathUtils {

    private DriverPathUtils() {}

    public static String getDriversDir() {
        // 优先系统属性 drivers.dir
        String p = System.getProperty("drivers.dir");
        if (p != null && !p.trim().isEmpty()) return p.trim();
        // 再尝试环境变量 DRIVERS_DIR
        p = System.getenv("DRIVERS_DIR");
        if (p != null && !p.trim().isEmpty()) return p.trim();
        // 回退到项目目录下的 drivers
        String userDir = System.getProperty("user.dir");
        return userDir + File.separator + "drivers";
    }
}

