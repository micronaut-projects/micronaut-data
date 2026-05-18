/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.jd;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

/**
 * The record represents a repository method constraint: `@By("propName") @Is(Constraint.class) String value`.
 *
 * @param argument The argument
 * @param value    The value
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public record SpecificationConstraint(Argument<?> argument, Object value) {
}
