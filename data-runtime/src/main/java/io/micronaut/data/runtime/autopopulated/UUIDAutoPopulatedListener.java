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
package io.micronaut.data.runtime.autopopulated;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.event.EntityListener;

import javax.inject.Singleton;
import java.util.UUID;

/**
 * Setter of UUID auto populated properties.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Singleton
class UUIDAutoPopulatedListener implements EntityListener<Object> {

    @PrePersist
    void onPrePersist(Object entity) {
        for (BeanProperty<Object, Object> bp : BeanIntrospection.getIntrospection((Class<Object>) entity.getClass()).getBeanProperties()) {
            if (UUID.class.isAssignableFrom(bp.getType())) {
                bp.convertAndSet(entity, UUID.randomUUID());
            }
        }
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

}
