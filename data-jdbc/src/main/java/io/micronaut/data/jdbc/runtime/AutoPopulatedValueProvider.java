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
package io.micronaut.data.jdbc.runtime;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.PersistentProperty;

/**
 * Auto populated field setter. See @{@link io.micronaut.data.annotation.AutoPopulated}.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Experimental
public interface AutoPopulatedValueProvider {

    /**
     * Provides a property value when the entity is created.
     *
     * @param persistentProperty The persistent property
     * @param beanProperty       The introspected property
     * @param entity             The instance of the entity
     * @return the provided value
     */
    Object provideOnCreate(PersistentProperty persistentProperty, BeanProperty<Object, Object> beanProperty, Object entity);

    /**
     * Provides a property value when the entity is updated.
     *
     * @param persistentProperty The persistent property
     * @param valueType          The value type
     * @param previousValue      The previous value
     * @return the provided value
     */
    Object provideOnUpdate(PersistentProperty persistentProperty, Class<?> valueType, Object previousValue);

    boolean supportsUpdate(PersistentProperty persistentProperty, Class<?> type);

    boolean supportsCreate(PersistentProperty persistentProperty, Class<?> type);

}
