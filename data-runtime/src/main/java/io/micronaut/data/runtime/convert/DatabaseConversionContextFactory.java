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
 * Factory that creates {@link DatabaseTypeConversionContext} instances used during SQL mapping.
 *
 * <p>The default implementation in {@code data-runtime} provides lightweight contexts for argument-based
 * conversion. Datastore modules (for example JDBC or R2DBC) may provide richer implementations that carry
 * additional runtime state while preserving the same {@link DatabaseTypeConversionContext} contract.</p>
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
public interface DatabaseConversionContextFactory {

    /**
     * Create a conversion context for a Micronaut {@link Argument}.
     *
     * @param argument the argument
     * @return SQL conversion context
     */
    @NonNull
    DatabaseTypeConversionContext forArgument(@NonNull Argument<?> argument);
}
