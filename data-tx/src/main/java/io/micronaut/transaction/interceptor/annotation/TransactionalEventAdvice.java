/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.transaction.interceptor.annotation;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.interceptor.TransactionalEventInterceptor;

import java.lang.annotation.*;

/**
 * Internal annotation applied to {@link io.micronaut.transaction.annotation.TransactionalEventListener} that
 * allows the interception of methods and binding the event consumer to the running transaction.
 *
 * @author graemerocher
 * @since 1.0
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Around
@Type(TransactionalEventInterceptor.class)
@Internal
public @interface TransactionalEventAdvice {
}
