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

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.NamingStrategy;
import io.micronaut.data.model.naming.NamingStrategies;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class whose instances are stored as relationships with properties.
 *
 * <p>The class declares exactly one {@link TargetNode} property for the node at the far end of the
 * relationship, an optional {@link RelationshipId}, and any number of properties that are stored on
 * the relationship. The relationship type and direction are declared by the {@link Relationship}
 * annotation on the property of the owning node.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Documented
@MappedEntity
@NamingStrategy(NamingStrategies.Raw.class)
public @interface RelationshipProperties {
}
