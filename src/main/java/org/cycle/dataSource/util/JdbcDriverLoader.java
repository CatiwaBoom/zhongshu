package org.cycle.dataSource.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

@Slf4j
public final class JdbcDriverLoader {

    private JdbcDriverLoader() {}

    public static void loadDriver(String driverClassName, String driverDir) throws Exception {
        // 1\. 先尝试默认类加载器
        try {
            Class.forName(driverClassName);
            return;
        } catch (ClassNotFoundException ignore) {
            log.warn("默认classpath未找到驱动：{}", driverClassName);
        }

        // 2\. 从外部目录加载所有jar
        File dir = new File(driverDir);
        File[] jars = dir.listFiles((d, name) -> name != null && name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            throw new ClassNotFoundException("驱动目录无可用jar: " + driverDir);
        }

        URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            urls[i] = jars[i].toURI().toURL();
        }

        URLClassLoader cl = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        Class.forName(driverClassName, true, cl);
    }
}