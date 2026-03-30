/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.model.runtime.convert.vector;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider;
import io.micronaut.data.model.vector.SparseFloatVector;

/**
 * Converter contract for mapping {@link SparseFloatVector} attributes to vendor-specific persisted values.
 *
 * @param <X> persisted database/driver type
 * @since 5.0.0
 */
@Indexed(SparseFloatVectorAttributeConverter.class)
public interface SparseFloatVectorAttributeConverter<X> extends AttributeConverter<SparseFloatVector, X>, SqlColumnDefinitionProvider {
}
