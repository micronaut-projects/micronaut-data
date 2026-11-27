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
package io.micronaut.data.annotation;

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity whose columns map one to one onto properties the driver returns with a typed getter, which the
 * annotation processor determines when it computes the mapping defaults: no associations or embedded values, no
 * composite identity, no attribute converters, only data types with a typed getter (string, the numbers, boolean,
 * decimal, bytes, timestamp, date, time, UUID and character), and an identity and version that can be set.
 *
 * <p>The SQL runtimes read such an entity by column ordinal with the typed getters and set the properties through
 * the bean introspection directly, instead of resolving the mapping per column and row; every other entity is read
 * by the general entity mapper.</p>
 *
 * @since 5.3.0
 */
@Internal
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Documented
public @interface FlatEntity {
}
