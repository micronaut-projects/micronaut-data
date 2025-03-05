/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.operations;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.PersistentEntity;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link MongoCollectionNameProvider}.
 *
 * @since 3.9.0
 * @author Denis Stepanov
 */
@Singleton
@Requires(missingBeans = MongoCollectionNameProvider.class)
@Internal
public final class DefaultMongoCollectionNameProvider implements MongoCollectionNameProvider {

    @Nullable
    private final PropertyPlaceholderResolver propertyPlaceholderResolver;

    DefaultMongoCollectionNameProvider(@Nullable Environment environment) {
        this.propertyPlaceholderResolver = environment != null ? environment.getPlaceholderResolver() : null;
    }

    @Override
    public String provide(PersistentEntity persistentEntity) {
        String persistedName = persistentEntity.getPersistedName();
        if (propertyPlaceholderResolver != null && persistedName.contains(propertyPlaceholderResolver.getPrefix())) {
            persistedName = propertyPlaceholderResolver.resolveRequiredPlaceholders(persistedName);
        }
        return persistedName;
    }

}
