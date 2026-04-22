package example;

import io.micronaut.data.connection.ConnectionCapabilities;

import java.sql.Connection;

public class CustomConnectionCapabilities implements ConnectionCapabilities {

    @Override
    public boolean supportsReadOnly(Connection connection) {
        return true;
    }
}
