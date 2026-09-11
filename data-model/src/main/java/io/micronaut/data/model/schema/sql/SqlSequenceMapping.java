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
package io.micronaut.data.model.schema.sql;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.model.DataType;

import java.util.Optional;

/**
 * The SQL table sequence.
 *
 * @param columnName The mapped column whose generated value uses the sequence
 * @param definition The custom definition as SQL command to be executed to create sequence if present or else null
 * @param definedName The sequence name defined on the attribute
 * @param dataType The data type of the property defining sequence
 * @param generatedValueType The {@link Optional} of {@link GeneratedValue.Type} since type might not be explicitly declared
 */
@Internal
public record SqlSequenceMapping(String columnName,
                                 @Nullable String definition,
                                 @Nullable String definedName,
                                 DataType dataType,
                                 Optional<GeneratedValue.Type> generatedValueType) {
}
