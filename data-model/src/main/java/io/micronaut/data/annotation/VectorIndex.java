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
package io.micronaut.data.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Declares a vendor-specific vector index for a Micronaut Data vector property.
 * Applies to entity types and properties; supported dialects may ignore it.
 *
 * @since 5.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Inherited
@Experimental
public @interface VectorIndex {

    /**
     * Optional index name. If empty, a name will be derived from table and column names.
     *
     * @return the index name to use, or an empty string to let Micronaut derive one
     */
    String name() default "";
    /**
     * Index algorithm to use for the vector index.
     *
     * @return the vector index algorithm type
     */
    VectorIndexType vectorIndexType() default VectorIndexType.IVF;

    /**
     * Distance (similarity) metric to use for the vector index.
     *
     * @return the distance metric for nearest-neighbor search
     */
    VectorIndexType.DistanceType distanceType() default VectorIndexType.DistanceType.COSINE;

    /**
     * Target accuracy used by the vendor implementation (if applicable).
     * The value is interpreted by the database engine and may be ignored on unsupported dialects.
     *
     * @return the target accuracy value (typically a percentage-like integer)
     */
    int accuracy() default 90;
}
