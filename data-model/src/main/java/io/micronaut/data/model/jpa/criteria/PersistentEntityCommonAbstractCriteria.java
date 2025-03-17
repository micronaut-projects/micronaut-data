/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.data.model.jpa.criteria.impl.expression.ClassExpressionType;
import jakarta.persistence.criteria.CommonAbstractCriteria;

/**
 * The persistent entity {@link CommonAbstractCriteria}.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Experimental
public interface PersistentEntityCommonAbstractCriteria extends CommonAbstractCriteria {

    /**
     * Create a subquery from the expression type.
     * @param type The type
     * @param <U> The subquery type
     * @return A new subquery
     * @since 4.10
     */
    <U> PersistentEntitySubquery<U> subquery(ExpressionType<U> type);

    @Override
    default <U> PersistentEntitySubquery<U> subquery(Class<U> type) {
        return subquery(new ClassExpressionType<>(type));
    }

}
