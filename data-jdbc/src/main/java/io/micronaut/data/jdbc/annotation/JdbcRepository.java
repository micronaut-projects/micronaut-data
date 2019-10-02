/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.jdbc.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.jdbc.mapper.SqlResultConsumer;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.lang.annotation.*;

/**
 * Stereotype repository that configures a {@link Repository} as a {@link JdbcRepository} using
 * raw SQL encoding and {@link JdbcRepositoryOperations} as the runtime engine.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    operations = JdbcRepositoryOperations.class,
    implicitQueries = false,
    namedParameters = false,
    typeRoles = @TypeRole(
            role = SqlResultConsumer.ROLE,
            type = SqlResultConsumer.class
    )
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
public @interface JdbcRepository {
    /**
     * @return The dialect to use.
     */
    @AliasFor(annotation = Repository.class, member = "dialect")
    Dialect dialect() default Dialect.ANSI;

    /**
     * @return The dialect to use.
     */
    @AliasFor(annotation = Repository.class, member = "dialect")
    @AliasFor(annotation = JdbcRepository.class, member = "dialect")
    String dialectName() default "ANSI";
}
