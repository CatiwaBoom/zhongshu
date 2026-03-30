// java
package org.cycle;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AppTest {

    @Test
    void contextLoads() {
    }
}

@SpringBootTest
@ActiveProfiles("dm")
class DataSourceProfileTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void dataSourceBeanCreatedWhenDmProfileEnabled() {
        assertNotNull(dataSource);
        assertDoesNotThrow(() -> dataSource.getConnection().close());
    }
}