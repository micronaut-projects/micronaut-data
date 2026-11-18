package io.micronaut.data.r2dbc.oraclexe.testpilot;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.r2dbc.spi.ConnectionFactoryOptions;
import jakarta.inject.Singleton;
import oracle.r2dbc.OracleR2dbcOptions;

/**
 * Connects with the environment of Oracle Test Pilot: its connection string is the descriptor of Oracle R2DBC, which
 * composes the JDBC URL {@code jdbc:oracle:thin:@<descriptor>} as the JDBC Test Resources module does. The options
 * replace those of the URL, which cannot be combined with a descriptor.
 */
@Singleton
@Requires(property = TestPilotDescriptor.PREFIX + ".descriptor")
final class TestPilotDescriptor implements BeanCreatedEventListener<ConnectionFactoryOptions.Builder> {

    static final String PREFIX = "spec.testpilot";

    private final String descriptor;
    private final String user;
    private final String password;

    TestPilotDescriptor(@Value("${" + PREFIX + ".descriptor}") String descriptor,
                        @Value("${" + PREFIX + ".user}") String user,
                        @Value("${" + PREFIX + ".password}") String password) {
        this.descriptor = descriptor.replace("\"", "");
        this.user = user;
        this.password = password;
    }

    @Override
    public ConnectionFactoryOptions.Builder onCreated(BeanCreatedEvent<ConnectionFactoryOptions.Builder> event) {
        return ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "oracle")
            .option(OracleR2dbcOptions.DESCRIPTOR, descriptor)
            .option(ConnectionFactoryOptions.USER, user)
            .option(ConnectionFactoryOptions.PASSWORD, password);
    }
}
