package io.micronaut.data.spring.jdbc.micronaut;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

@Singleton
public class PostgresDelay implements BeanCreatedEventListener<DataSource> {
    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return event.getBean();
    }
}
