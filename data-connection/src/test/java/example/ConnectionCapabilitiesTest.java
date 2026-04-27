package example;

import io.micronaut.data.connection.ConnectionCapabilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ConnectionCapabilitiesTest {

    @Test
    void youCanProvideYourOwnConnectionCapabilitiesInstanceViaSpi() {
        assertInstanceOf(CustomConnectionCapabilities.class, ConnectionCapabilities.INSTANCE);
    }
}
