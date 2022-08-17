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
package io.micronaut.data.runtime.operations;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.RepositoryOperationsRegistry;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link RepositoryOperationsRegistry}.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Internal
@Singleton
class DefaultRepositoryOperationsRegistry implements RepositoryOperationsRegistry {

    private final BeanLocator locator;

    DefaultRepositoryOperationsRegistry(BeanLocator locator) {
        this.locator = locator;
    }

    @Override
    public <T extends RepositoryOperations> T provide(Class<T> repositoryOperationsType, String dataSourceName) {
        if (dataSourceName != null) {
            try {
                return locator.getBean(repositoryOperationsType, Qualifiers.byName(dataSourceName));
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("No backing RepositoryOperations configured for repository for datasource: [" + dataSourceName + "]. Check your configuration and try again", e);
            }
        }
        try {
            return locator.getBean(repositoryOperationsType);
        } catch (NoSuchBeanException e) {
            throw new ConfigurationException("No backing RepositoryOperations configured for repository. Check your configuration and try again", e);
        }

    }

}
