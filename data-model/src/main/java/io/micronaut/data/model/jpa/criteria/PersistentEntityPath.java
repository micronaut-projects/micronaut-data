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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.PersistentEntity;
import jakarta.persistence.criteria.Path;

/**
 * The persistent entity {@link Path}.
 *
 * @param <T> The path type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentEntityPath<T> extends Path<T>, IExpression<T> {

    /**
     * @return The persistent entity
     */
    @NonNull
    PersistentEntity getPersistentEntity();

    /**
     * Get persistent property path.
     *
     * @param attributeName The property name
     * @param <Y>           The property type
     * @return The property path
     */
    @Override
    @NonNull
    <Y> PersistentPropertyPath<Y> get(@NonNull String attributeName);

}
