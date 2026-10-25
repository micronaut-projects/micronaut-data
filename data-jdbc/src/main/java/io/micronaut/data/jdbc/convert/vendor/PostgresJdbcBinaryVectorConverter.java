/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.jdbc.convert.vendor;

import com.pgvector.PGbit;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.vector.BinaryVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;

import java.util.Set;

/**
 * Persists {@link BinaryVector} values in pgvector {@code bit} columns.
 *
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(classes = PGbit.class)
final class PostgresJdbcBinaryVectorConverter extends AbstractJdbcVectorConverter<PGbit> {

    PostgresJdbcBinaryVectorConverter(ConversionService conversionService) {
        super(conversionService);
    }

    @Override
    public Class<PGbit> getPersistedType() {
        return PGbit.class;
    }

    @Override
    public Set<Class<? extends Vector>> supportedVectorTypes() {
        return Set.of(BinaryVector.class);
    }

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.POSTGRES;
    }
}
