package io.micronaut.data.runtime.date;

import javax.inject.Singleton;
import java.time.OffsetDateTime;

/**
 * Default implementation of {@link DateTimeProvider}.
 * @param <T> The type of the time.
 * @author niravassar
 * @since 1.0.0
 */
@Singleton
public class CurrentDateTimeProvider implements DateTimeProvider<OffsetDateTime> {

    @Override
    public OffsetDateTime getNow() {
        return OffsetDateTime.now();
    }
}
