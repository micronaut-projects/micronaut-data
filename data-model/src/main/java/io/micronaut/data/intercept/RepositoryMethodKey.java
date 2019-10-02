/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;

import java.util.Objects;

/**
 * Key used to cache results for repository method invocations.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public final class RepositoryMethodKey {
    private final Object repository;
    private final ExecutableMethod method;
    private final int hashCode;

    /**
     * Default constructor.
     * @param repository The repository
     * @param method The method
     */
    public RepositoryMethodKey(Object repository, ExecutableMethod method) {
        this.repository = repository;
        this.method = method;
        this.hashCode = Objects.hash(repository, method);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RepositoryMethodKey that = (RepositoryMethodKey) o;
        return repository.equals(that.repository) &&
                method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
