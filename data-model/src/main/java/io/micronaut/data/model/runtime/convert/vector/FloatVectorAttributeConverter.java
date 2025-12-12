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
import io.micronaut.data.model.runtime.convert.SqlAttributeConverter;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.runtime.convert.AttributeConverter;

/**
 * The attribute converter is used for converting mapped entity value to the persisted value and back.
 *
 * @param <X> The entity value type
 * @author Denis Stepanov
 * @since 3.1
 */
@Indexed(FloatVectorAttributeConverter.class)
public interface FloatVectorAttributeConverter<X> extends SqlAttributeConverter<FloatVector, X> {
}
