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
package io.micronaut.data.annotation.search;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a property as part of the full-text search index of a {@link Searchable} entity.
 *
 * <p>The annotation is repeatable so one property can be indexed both as analysed
 * {@link SearchFieldType#TEXT} and as an exact {@link SearchFieldType#KEYWORD}.</p>
 *
 * @since 5.4.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Repeatable(SearchFields.class)
@Inherited
@Experimental
public @interface SearchField {

    /**
     * The annotation name.
     */
    String ANNOTATION_NAME = SearchField.class.getName();

    /**
     * The {@code weight} member.
     */
    String MEMBER_WEIGHT = "weight";

    /**
     * The {@code type} member.
     */
    String MEMBER_TYPE = "type";

    /**
     * Optional name of the field in the search index. Defaults to the persisted property name.
     *
     * @return The field name
     */
    String name() default "";

    /**
     * How the field is indexed.
     *
     * @return The field type
     */
    SearchFieldType type() default SearchFieldType.TEXT;

    /**
     * How much a match in this field contributes to the relevance score.
     *
     * @return The weight
     */
    SearchWeight weight() default SearchWeight.MEDIUM;

    /**
     * Optional analyzer name. Analyzers are resolved per backend; an empty value uses the
     * backend default for the entity {@link Searchable#language()}.
     *
     * @return The analyzer name
     */
    String analyzer() default "";

    /**
     * Whether highlighted fragments can be requested for this field.
     *
     * @return True if highlightable
     */
    boolean highlightable() default false;

    /**
     * Whether results can be sorted on this field. Only meaningful for {@link SearchFieldType#KEYWORD}.
     *
     * @return True if sortable
     */
    boolean sortable() default false;
}
