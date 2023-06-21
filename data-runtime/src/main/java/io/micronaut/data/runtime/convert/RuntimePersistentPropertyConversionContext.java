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
package io.micronaut.data.runtime.convert;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

/**
 * Conversion context that has access to converter {@link io.micronaut.data.model.runtime.RuntimePersistentProperty}.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
public interface RuntimePersistentPropertyConversionContext extends ArgumentConversionContext<Object> {

    /**
     * Returns runtime persistent property.
     *
     * @return the property
     */
    RuntimePersistentProperty<?> getRuntimePersistentProperty();

    @Override
    default Argument<Object> getArgument() {
        return getRuntimePersistentProperty().getArgument();
    }
}
