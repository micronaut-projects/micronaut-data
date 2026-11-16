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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for annotations that declare an association whose {@link Relation.Kind} is
 * inferred from the type of the annotated property.
 *
 * <p>A property annotated with such an annotation, and not also annotated with {@link Relation},
 * is mapped as {@link Relation.Kind#ONE_TO_MANY} when its type is a collection, an iterable or an array,
 * and as {@link Relation.Kind#MANY_TO_ONE} otherwise.</p>
 *
 * <p>Used by data models where the kind of an association is not part of the mapping,
 * such as relationships between graph nodes.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Documented
public @interface InferredRelation {
}
