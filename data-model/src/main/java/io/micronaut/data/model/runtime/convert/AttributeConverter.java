/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.model.runtime.convert;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

/**
 * Interface roughly equivalent to JPA's {@code AttributeConverter} without being specific to
 * any database type.
 *
 * @param <X> The type of the attribute
 * @param <Y> The type of the native value stored in the database
 * @author graemerocher
 * @since 1.0
 */
@Introspected
public interface AttributeConverter<X, Y> {

    /**
     * Converts the value stored in the entity attribute into a native value that can
     * be persisted to the target database.
     *
     * @param attribute the entity attribute value to be converted
     * @param property The property that the attribute relates to
     * @return The native value that can be stored in the database
     */
    @NonNull
    Y convertToNativeValue(@NonNull X attribute, @NonNull RuntimePersistentProperty<X> property);

    /**
     * Converts the data stored in the database to a value that can
     * be written to the entity attribute.
     *
     * @param value the data read from the database
     * @param property The property that represents the attribute
     * @return the converted value to be stored in the entity
     * attribute
     */
    @NonNull
    X convertToEntityAttribute(@NonNull Y value, @NonNull RuntimePersistentProperty<X> property);
}
