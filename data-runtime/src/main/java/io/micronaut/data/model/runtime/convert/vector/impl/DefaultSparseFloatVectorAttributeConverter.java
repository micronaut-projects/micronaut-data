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
import io.micronaut.data.model.runtime.convert.vector.SparseFloatVectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.SparseFloatVector;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Internal
final class DefaultSparseFloatVectorAttributeConverter extends AbstractVectorAttributeConverter<SparseFloatVector, Object> implements SparseFloatVectorAttributeConverter<Object> {

    DefaultSparseFloatVectorAttributeConverter(List<VectorTypeConverter<?>> converterList) {
        super(converterList, SparseFloatVector.class);
    }

    @Override
    String getOracleType() {
        return "FLOAT32";
    }
}
