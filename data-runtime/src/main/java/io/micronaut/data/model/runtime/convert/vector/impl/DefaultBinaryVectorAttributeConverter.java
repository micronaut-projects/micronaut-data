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
package io.micronaut.data.model.runtime.convert.vector.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.VectorStorage;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.vector.BinaryVectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.BinaryVector;
import jakarta.inject.Singleton;
import jakarta.persistence.Column;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Unified BinaryVector converter that delegates to a dialect-specific VectorTypeConverter selected by DatabaseType.
 * Supports Oracle only, where the element type is BINARY. Other databases get the same VARCHAR(255)
 * fallback column as vectors on databases without vector support.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Singleton
@Internal
final class DefaultBinaryVectorAttributeConverter extends AbstractVectorAttributeConverter<BinaryVector, Object> implements BinaryVectorAttributeConverter<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultBinaryVectorAttributeConverter.class);

    DefaultBinaryVectorAttributeConverter(List<VectorTypeConverter<?>> converterList) {
        super(converterList, BinaryVector.class);
    }

    @Override
    String getOracleType() {
        return "BINARY";
    }

    @Override
    public String getColumnDefinition(Argument<?> argument, DatabaseType databaseType) {
        if (databaseType == DatabaseType.ORACLE) {
            return super.getColumnDefinition(argument, databaseType);
        }
        if (databaseType == DatabaseType.POSTGRES) {
            // pgvector stores binary vectors in the bit type, one bit per dimension
            int dimensions = argument.getAnnotationMetadata()
                .intValue(VectorStorage.class, "length")
                .orElseGet(() -> argument.getAnnotationMetadata().intValue(Column.class, "length").orElse(-1));
            return dimensions > 0 ? "bit(%d)".formatted(dimensions) : "bit varying";
        }
        LOG.warn("Binary vectors are supported for Oracle and PostgreSQL only, not for the database {}. Falling back to VARCHAR(255) column definition.", databaseType);
        return "VARCHAR(255)";
    }
}
