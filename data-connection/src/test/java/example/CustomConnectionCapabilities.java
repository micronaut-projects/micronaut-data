package example;

import io.micronaut.data.connection.ConnectionCapabilities;
import java.util.function.Supplier;

public class CustomConnectionCapabilities implements ConnectionCapabilities {

    @Override
    public boolean supports(ConnectionCapabilities.Capability capability, Supplier<String> databaseProductNameSupplier) {
        return true;
    }
}
