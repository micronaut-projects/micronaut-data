package example;

import io.micronaut.data.connection.Capability;
import io.micronaut.data.connection.ConnectionCapabilities;

import java.sql.Connection;

public class CustomConnectionCapabilities implements ConnectionCapabilities {

    @Override
    public boolean supports(Capability capability, Connection connection) {
        return true;
    }
}
