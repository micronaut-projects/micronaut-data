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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Oracle Text parameters of the indexes schema generation creates for a {@link Searchable} entity:
 * the {@code PARAMETERS ('...')} clause of each {@code CONTEXT} index, or of the JSON search index of a
 * JSON entity, which names the sync mode, the lexer, the wordlist or the memory. The other stores ignore it.
 *
 * @since 5.4.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Inherited
@Experimental
public @interface OracleTextIndex {

    /**
     * The {@code parameters} member.
     */
    String MEMBER_PARAMETERS = "parameters";

    /**
     * @return The parameters clause, {@code SYNC (ON COMMIT)} by default
     */
    String parameters() default "SYNC (ON COMMIT)";
}
