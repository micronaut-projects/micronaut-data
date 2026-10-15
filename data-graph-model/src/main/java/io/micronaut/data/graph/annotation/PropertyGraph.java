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
 * Names the property graph a repository queries, for databases that hold several graphs defined over tables,
 * and the query language its queries are compiled to.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Documented
public @interface PropertyGraph {

    /**
     * @return The name of the graph
     */
    String value() default "";

    /**
     * @return The query language
     */
    QueryLanguage language() default QueryLanguage.PGQL;

    /**
     * Query languages of property graphs defined over tables.
     */
    enum QueryLanguage {
        /**
         * PGQL, run through the PGQL JDBC driver.
         */
        PGQL,
        /**
         * SQL/PGQ: {@code GRAPH_TABLE} queries in SQL, the property graph queries of the SQL standard.
         */
        SQL_PGQ
    }
}
