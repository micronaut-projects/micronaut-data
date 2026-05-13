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
package io.micronaut.data.model.runtime.convert.vector;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext;
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider;
import io.micronaut.data.model.vector.Vector;

/**
 * SQL attribute converter specialization for {@link Vector} values.
 *
 * <p>Bridges between the entity-side {@link Vector} abstraction and a dialect-specific persisted type {@code X}
 * (for example, a driver object, textual representation, or a primitive array depending on dialect/driver).</p>
 *
 * <p>Implementations should honor the {@link io.micronaut.data.model.query.builder.sql.Dialect} exposed via
 * the {@link DatabaseTypeConversionContext} carried in conversion calls.</p>
 *
 * @param <X> The persisted dialect/driver type
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Indexed(VectorAttributeConverter.class)
public interface VectorAttributeConverter<X> extends AttributeConverter<Vector, X>, SqlColumnDefinitionProvider {
}
