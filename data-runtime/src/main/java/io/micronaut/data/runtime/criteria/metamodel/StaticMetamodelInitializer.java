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
package io.micronaut.data.runtime.criteria.metamodel;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.inject.Singleton;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Static metadata properties initializer.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
@Singleton
public final class StaticMetamodelInitializer {

    private final Set<Class> processedEntityClasses = Collections.synchronizedSet(new HashSet<>());

    public <T> void initializeMetadata(RuntimePersistentEntity<T> persistentEntity) {
        if (processedEntityClasses.contains(persistentEntity.getIntrospection().getBeanType())) {
            return;
        }
        initializeMetadataInternal(persistentEntity);
    }

    private synchronized <T> void initializeMetadataInternal(RuntimePersistentEntity<T> persistentEntity) {
        Class<T> beanType = persistentEntity.getIntrospection().getBeanType();
        if (processedEntityClasses.contains(beanType)) {
            return;
        }
        processedEntityClasses.add(beanType);
        ClassUtils.forName(beanType.getName() + "_", beanType.getClassLoader())
            .filter(metadataClazz -> metadataClazz.isAnnotationPresent(StaticMetamodel.class))
            .ifPresent(metadataClazz -> {
                for (String property : persistentEntity.getPersistentPropertyNames()) {
                    ReflectionUtils.findField(metadataClazz, property).ifPresent(field -> {
                        try {
                            if (field.get(field.getDeclaringClass()) == null) {
                                RuntimePersistentProperty<T> prop = persistentEntity.getPropertyByName(property);
                                if (prop instanceof RuntimeAssociation) {
                                    initializeMetadataInternal(((RuntimeAssociation<T>) prop).getAssociatedEntity());
                                }
                                if (field.getType() == SingularAttribute.class) {
                                    ReflectionUtils.setField(field, field.getDeclaringClass(), new RuntimePersistentPropertySingularAttribute<>(persistentEntity, prop));
                                }
                                if (field.getType() == CollectionAttribute.class) {
                                    ReflectionUtils.setField(field, field.getDeclaringClass(), new RuntimePersistentPropertyCollectionAttribute<>(persistentEntity, prop));
                                }
                                if (field.getType() == ListAttribute.class) {
                                    ReflectionUtils.setField(field, field.getDeclaringClass(), new RuntimePersistentPropertyListAttribute<>(persistentEntity, prop));
                                }
                                if (field.getType() == SetAttribute.class) {
                                    ReflectionUtils.setField(field, field.getDeclaringClass(), new RuntimePersistentPropertySetAttribute<>(persistentEntity, prop));
                                }
                            }
                        } catch (IllegalAccessException e) {
                            // Ignore
                        }
                    });
                }

            });
    }

}
