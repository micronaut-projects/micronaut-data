package io.micronaut.data.jdbc.mariadb;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.jdbc.BasicJdbcConfiguration;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Properties;

@Context
@Singleton
public class MariaDbInit implements BeanCreatedEventListener<BasicJdbcConfiguration> {

    @Override
    public BasicJdbcConfiguration onCreated(BeanCreatedEvent<BasicJdbcConfiguration> event) {
        BasicJdbcConfiguration configuration = event.getBean();
        if (!configuration.getConfiguredDriverClassName().toLowerCase(Locale.ROOT).contains("maria")) {
            return configuration;
        }

        final Properties info = new Properties();
        info.put("user", "root");
        info.put("password", "test");

        try {
            try (Connection connection = DriverManager.getConnection(configuration.getUrl(), info)) {
                connection.createStatement().execute("GRANT ALL PRIVILEGES ON *.* TO 'test'@'%' WITH GRANT OPTION;");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return configuration;
    }
}
