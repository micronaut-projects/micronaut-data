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
package io.micronaut.data.graph.annotation;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code Map<String, Object>} property that holds the schemaless properties of a node: every property
 * stored on the node that the entity does not declare.
 *
 * <p>Saving writes each entry as a property of the node, and reading collects every undeclared property into the
 * map. A declared property of the same name takes precedence. Values must be types the database can store:
 * strings, numbers, booleans, temporal values and lists of those. Setting a value to {@code null} removes the
 * property.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Documented
public @interface DynamicAttributes {
}
