/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;
import org.postgresql.util.PGobject;

import java.util.List;

/**
 * PostgreSQL-specific {@link VectorTypeConverter} that maps {@link Vector} to {@link PGobject} of type {@code vector}
 * and back.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Singleton
@Requires(classes = PGobject.class)
public final class PostgresJdbcVectorConverter extends AbstractJdbcVectorConverter<PGobject> {

    public PostgresJdbcVectorConverter(ConversionService conversionService) {
        super(conversionService);
    }

    @Override
    public Class<PGobject> getPersistedType() {
        return PGobject.class;
    }

    public List<Class<? extends Vector>> supportedVectorTypes() {
        return List.of(Vector.class, FloatVector.class);
    }

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.POSTGRES;
    }
}
