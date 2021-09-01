package example;

import io.micronaut.context.annotation.Context;
import io.r2dbc.spi.ConnectionFactory;

// just tests ConnectionFactory is injectable
@Context
public class ConnectionFactoryTest {
    private final ConnectionFactory connectionFactory;

    public ConnectionFactoryTest(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
}
