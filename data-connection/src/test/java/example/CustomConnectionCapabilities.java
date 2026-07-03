package example;

import io.micronaut.data.connection.ConnectionCapabilities;

import java.sql.Connection;

public class CustomConnectionCapabilities implements ConnectionCapabilities {
    @Override
    public boolean supports(ConnectionCapabilities.Capability capability, Connection connection) {
        return true;
    }
}
