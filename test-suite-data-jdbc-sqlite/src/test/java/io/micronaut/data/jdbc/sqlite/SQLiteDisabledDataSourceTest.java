package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
@JavaSQLiteDBProperties
@Property(name = "datasources.default.enabled", value = "false")
class SQLiteDisabledDataSourceTest {

    @Inject
    ApplicationContext applicationContext;

    @Test
    void testDisabledDataSource() {
        assertThrows(NoSuchBeanException.class, () -> applicationContext.getBean(DataSource.class));

        DataJdbcConfiguration dataJdbcConfiguration = applicationContext.getBean(DataJdbcConfiguration.class);
        assertNotNull(dataJdbcConfiguration);
        assertFalse(dataJdbcConfiguration.isEnabled());
    }
}
