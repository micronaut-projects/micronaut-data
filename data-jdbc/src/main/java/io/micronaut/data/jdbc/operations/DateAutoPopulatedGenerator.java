/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.date.DateTimeProvider;

/**
 * Generates a new date via the {@link DateTimeProvider} to populate a field annotated with {@link io.micronaut.data.annotation.AutoPopulated}.
 * @author Sergio del Amo
 * @since 1.1.0
 */
public abstract class DateAutoPopulatedGenerator implements AutoPopulatedGenerator {

    private final DateTimeProvider<?> dateTimeProvider;

    public DateAutoPopulatedGenerator(DateTimeProvider<?> dateTimeProvider) {
        this.dateTimeProvider = dateTimeProvider;
    }

    @Override
    public AutoPopulatedValue valueAtIndex(int index, @NonNull Dialect dialect) {
        Object now = dateTimeProvider.getNow();
        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
            DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", now, index);
        }
        return new AutoPopulatedValue(now, WriteValueMode.DYNAMIC, true);
    }
}
