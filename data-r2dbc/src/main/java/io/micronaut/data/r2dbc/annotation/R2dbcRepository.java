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
package io.micronaut.data.r2dbc.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.SqlQueryConfiguration;
import io.micronaut.data.r2dbc.operations.R2dbcRepositoryOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import java.lang.annotation.*;

/**
 * Stereotype repository that configures a {@link Repository} as a {@link R2dbcRepository} using
 * raw SQL encoding and {@link R2dbcRepositoryOperations} as the runtime engine.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    operations = R2dbcRepositoryOperations.class,
    implicitQueries = false,
    namedParameters = false,
    typeRoles = @TypeRole(
            role = R2dbcRepository.PARAMETER_TX_STATUS,
            type = ReactiveTransactionStatus.class
    )
)
@SqlQueryConfiguration({
    @SqlQueryConfiguration.DialectConfiguration(
        dialect = Dialect.POSTGRES,
        positionalParameterFormat = "$%s"
    ),
    @SqlQueryConfiguration.DialectConfiguration(
        dialect = Dialect.SQL_SERVER,
        positionalParameterFormat = "@p%s"
    )
})
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
public @interface R2dbcRepository {

    /**
     * @deprecated Transaction status key needs to be created using the data source name to allow propagating of multiple data source transactions
     */
    @Deprecated
    String PARAMETER_TX_STATUS = "tx-status";

    /**
     * @return The datasource name.
     */
    @AliasFor(annotation = Repository.class, member = "value")
    String value() default "default";

    /**
     * @return The datasource name.
     */
    @AliasFor(annotation = Repository.class, member = "value")
    String dataSource() default "default";

    /**
     * @return The dialect to use.
     */
    @AliasFor(annotation = Repository.class, member = "dialect")
    Dialect dialect() default Dialect.ANSI;

    /**
     * @return The dialect to use.
     */
    @AliasFor(annotation = Repository.class, member = "dialect")
    @AliasFor(member = "dialect")
    String dialectName() default "ANSI";
}
