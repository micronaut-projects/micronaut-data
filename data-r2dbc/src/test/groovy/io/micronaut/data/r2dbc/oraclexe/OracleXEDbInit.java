package io.micronaut.data.r2dbc.oraclexe;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.order.Ordered;
import io.micronaut.r2dbc.DefaultBasicR2dbcProperties;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import jakarta.inject.Singleton;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Requires(notEnv = "oracle-jsonview")
@Singleton
public class OracleXEDbInit implements BeanCreatedEventListener<DefaultBasicR2dbcProperties>, Ordered {

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    public DefaultBasicR2dbcProperties onCreated(BeanCreatedEvent<DefaultBasicR2dbcProperties> event) {
        DefaultBasicR2dbcProperties configuration = event.getBean();

        ConnectionFactoryOptions options = configuration.getBuilder().build();

        if (!"oracle".equals(options.getValue(Option.valueOf("driver")))) {
            return configuration;
        }

        final Properties info = new Properties();
        info.put("user", options.getValue(Option.valueOf("user")));
        info.put("password", options.getValue(Option.valueOf("password")));

        String host = (String) configuration.getBuilder().build().getValue(Option.valueOf("host"));
        Integer port = (Integer) configuration.getBuilder().build().getValue(Option.valueOf("port"));
        String database = (String) options.getValue(Option.valueOf("database"));
        String url = "jdbc:oracle:thin:@" + host + ":" + port + "/" + database;

        try {
            try (Connection connection = DriverManager.getConnection(url, info)) {
                try (CallableStatement st = connection.prepareCall("""
CREATE OR REPLACE PROCEDURE add1(myInput IN number, myOutput OUT number) IS
BEGIN
myOutput := myInput + 1;
END;
                 """)) {
                    st.execute();
                } catch (SQLException e) {
                    e.printStackTrace();
                    // Ignore if already exists
                }

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return configuration;
    }
}
