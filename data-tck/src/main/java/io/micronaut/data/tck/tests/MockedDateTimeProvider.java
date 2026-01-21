package io.micronaut.data.tck.tests;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.inject.Singleton;

import java.time.OffsetDateTime;

@Singleton
@Replaces(DateTimeProvider.class)
public class MockedDateTimeProvider implements DateTimeProvider<OffsetDateTime> {

    private OffsetDateTime value;

    public void setValue(OffsetDateTime value) {
        this.value = value;
    }

    @Override
    public OffsetDateTime getNow() {
        if (value == null) {
            return OffsetDateTime.now();
        }
        return value;
    }
}
