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
package io.micronaut.data.model.runtime.convert.vector.impl;

import io.micronaut.core.annotation.Internal;

import java.util.List;

import io.micronaut.data.model.runtime.convert.vector.ByteVectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.ByteVector;
import jakarta.inject.Singleton;


/**
 * Unified ByteVector converter that delegates to a dialect-specific VectorTypeConverter selected by DatabaseType.
 * Supports PostgreSQL (pgvector) and Oracle (textual "[...]") persisted forms; Oracle element type is INT8.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Singleton
@Internal
final class DefaultByteVectorAttributeConverter extends AbstractVectorAttributeConverter<ByteVector, Object> implements ByteVectorAttributeConverter<Object> {

    DefaultByteVectorAttributeConverter(List<VectorTypeConverter<?>> converterList) {
        super(converterList, ByteVector.class);
    }

    @Override
    String getOracleType() {
        return "INT8";
    }
}
