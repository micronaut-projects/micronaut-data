/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.jdbc.notification;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

/**
 * Compile-time-discovered method annotated with {@code @ChangeListener}.
 *
 * <p>This value preserves the executable method and the bean definition that owns it until the
 * datasource-specific notification provider is resolved at application startup. The entity
 * argument is resolved once from {@code ChangeEvent<E>}; providers translate this generic value
 * to their own listener definition.</p>
 *
 * @param beanDefinition The bean definition that owns the listener method.
 * @param method The executable listener method.
 * @param entityArgument The persistent entity argument resolved from {@code ChangeEvent<E>}.
 */
@Internal
public record ChangeListenerMethod(BeanDefinition<?> beanDefinition,
                                   ExecutableMethod<?, ?> method,
                                   Argument<?> entityArgument) {
}
