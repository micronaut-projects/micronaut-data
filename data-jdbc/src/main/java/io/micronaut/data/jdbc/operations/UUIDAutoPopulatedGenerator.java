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
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.config.DataSettings;

import javax.inject.Singleton;
import java.util.UUID;

/**
 * Generates a UUID to populate a field of type {@link java.util.UUID} annotated with {@link io.micronaut.data.annotation.AutoPopulated}.
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class UUIDAutoPopulatedGenerator implements AutoPopulatedGenerator {
    @Override
    public boolean canPopulate(@NonNull BeanProperty beanProperty) {
        return UUID.class.isAssignableFrom(beanProperty.getType());
    }

    @Override
    public AutoPopulatedValue valueAtIndex(int index, @NonNull Dialect dialect) {
        UUID uuid = UUID.randomUUID();
        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
            DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", uuid, index);
        }
        if (dialect == Dialect.ORACLE || dialect == Dialect.MYSQL) {
            return new AutoPopulatedValue(uuid, WriteValueMode.STRING);
        }
        return new AutoPopulatedValue(uuid, WriteValueMode.DYNAMIC);
    }
}
