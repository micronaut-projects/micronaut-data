package example;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.r2dbc.DefaultBasicR2dbcProperties;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Result;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

@Context
@Singleton
public class MariaDbInit implements BeanCreatedEventListener<DefaultBasicR2dbcProperties> {

    @Override
    public DefaultBasicR2dbcProperties onCreated(BeanCreatedEvent<DefaultBasicR2dbcProperties> event) {
        DefaultBasicR2dbcProperties configuration = event.getBean();
        ConnectionFactoryOptions options = configuration.builder().build().mutate()
            .option(ConnectionFactoryOptions.USER, "root")
            .option(ConnectionFactoryOptions.PASSWORD, "test")
            .build();

        ConnectionFactory connectionFactory = ConnectionFactories.get(options);

        Flux.usingWhen(connectionFactory.create(), connection -> Flux.from(connection.createStatement("GRANT ALL PRIVILEGES ON *.* TO 'test'@'%' WITH GRANT OPTION;").execute())
            .flatMap(Result::getRowsUpdated), Connection::close).collectList().block();

        return configuration;
    }
}
