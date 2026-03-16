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
package io.micronaut.data.runtime.convert;

import org.jspecify.annotations.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext;

/**
 * Factory responsible for creating {@link DatabaseTypeConversionContext} instances used during SQL type mapping.
 *
 * <p>The default implementation in {@code data-runtime} creates lightweight contexts that delegate to
 * {@code ConversionContext.of(Argument)} and carry no datastore-specific state.</p>
 *
 * <p>Datastore modules (e.g. JDBC, R2DBC) may provide richer implementations that supply additional
 * context such as dialect or connection metadata, allowing type converters to produce more precise
 * database-specific representations.</p>
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
public interface ConversionContextFactory {

    /**
     * Create a conversion context for a Micronaut {@link Argument}.
     *
     * @param argument the argument
     * @return SQL conversion context
     */
    @NonNull
    DatabaseTypeConversionContext forArgument(@NonNull Argument<?> argument);
}
