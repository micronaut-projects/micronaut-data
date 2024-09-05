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

/**
 * The expression type.
 *
 * @param <E> The type
 * @author Denis Stepanov
 * @since 4.10
 */
@Experimental
public interface ExpressionType<E> {

    /**
     * The boolean type.
     */
    ExpressionType<Boolean> BOOLEAN = new ClassExpressionType<>(Boolean.class);

    /**
     * The object type.
     */
    ExpressionType<Object> OBJECT = new ClassExpressionType<>(Object.class);

    /**
     * @return The type name
     */
    String getName();

    /**
     * @return true if the expression is of boolean type
     */
    boolean isBoolean();

    /**
     * @return true if the expression is of numeric type
     */
    boolean isNumeric();

    /**
     * @return true if the expression is of comparable type
     */
    boolean isComparable();

    /**
     * @return true if the expression is of string type
     */
    boolean isTextual();

    /**
     * @return The Java type
     */
    Class<E> getJavaType();

}
