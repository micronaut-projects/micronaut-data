/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model.runtime;

import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.Embedded;

/**
 * A runtime representation of {@link Embedded}.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <T> the owning type
 */
class RuntimeEmbedded<T> extends RuntimeAssociation<T> implements Embedded {

    /**
     * Default constructor.
     * @param owner The owner
     * @param property The bean property
     * @param constructorArg Whether it is a constructor arg
     */
    RuntimeEmbedded(RuntimePersistentEntity<T> owner, BeanProperty<T, Object> property, boolean constructorArg) {
        super(owner, property, constructorArg);
    }
}
