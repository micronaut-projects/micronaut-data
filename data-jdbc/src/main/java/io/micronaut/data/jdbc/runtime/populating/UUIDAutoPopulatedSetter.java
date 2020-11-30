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
package io.micronaut.data.jdbc.runtime.populating;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.jdbc.runtime.AutoPopulatedValueProvider;
import io.micronaut.data.model.PersistentProperty;

import javax.inject.Singleton;
import java.util.UUID;

/**
 * Setter of UUID auto populated properties.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Singleton
@Experimental
public class UUIDAutoPopulatedSetter implements AutoPopulatedValueProvider {

    @Override
    public Object provideOnCreate(PersistentProperty persistentProperty, BeanProperty<Object, Object> beanProperty, Object entity) {
        return UUID.randomUUID();
    }

    @Override
    public Object provideOnUpdate(PersistentProperty persistentProperty, Class<?> valueType, Object previousValue) {
        return null;
    }

    @Override
    public boolean supportsCreate(PersistentProperty persistentProperty, Class<?> type) {
        return UUID.class.isAssignableFrom(type);
    }

    @Override
    public boolean supportsUpdate(PersistentProperty persistentProperty, Class<?> type) {
        return false;
    }
    
}
