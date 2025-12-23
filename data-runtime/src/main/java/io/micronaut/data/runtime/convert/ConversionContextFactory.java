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
 * Factory to create ConversionContext instances used during SQL mapping.
 * that include datastore-specific details (e.g. dialect, connection).
 *
 * Default implementation in data-runtime provides lightweight contexts
 * delegating to {@code ConversionContext.of(...)} without datastore state.
 *
 * Datastore modules (e.g. JDBC) may provide richer contexts and still satisfy
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
