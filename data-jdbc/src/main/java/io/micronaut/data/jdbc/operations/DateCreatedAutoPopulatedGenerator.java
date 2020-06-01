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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.runtime.date.DateTimeProvider;
import javax.inject.Singleton;

/**
 * Generates a new date via the {@link DateTimeProvider} to populate a field annotated with {@link io.micronaut.data.annotation.DateCreated}.
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Requires(beans = DateTimeProvider.class)
@Singleton
public class DateCreatedAutoPopulatedGenerator extends DateAutoPopulatedGenerator {

    public DateCreatedAutoPopulatedGenerator(DateTimeProvider<?> dateTimeProvider) {
        super(dateTimeProvider);
    }

    @Override
    public boolean canPopulate(@NonNull BeanProperty<?, ?> beanProperty) {
        return beanProperty.hasAnnotation(DateCreated.class);
    }
}
