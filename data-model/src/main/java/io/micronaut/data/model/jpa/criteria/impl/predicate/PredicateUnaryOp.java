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
package io.micronaut.data.model.jpa.criteria.impl.predicate;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * Predicate unary operations.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public enum PredicateUnaryOp {
    IS_NULL,
    IS_NON_NULL,
    IS_TRUE,
    IS_FALSE,
    IS_EMPTY,
    IS_NOT_EMPTY
    ;

    @Nullable
    public PredicateUnaryOp negate() {
        switch (this) {
            case IS_NULL:
                return IS_NON_NULL;
            case IS_NON_NULL:
                return IS_NULL;
            case IS_TRUE:
                return IS_FALSE;
            case IS_FALSE:
                return IS_TRUE;
            case IS_EMPTY:
                return IS_NOT_EMPTY;
            case IS_NOT_EMPTY:
                return IS_EMPTY;
            default:
                return null;
        }
    }
}
