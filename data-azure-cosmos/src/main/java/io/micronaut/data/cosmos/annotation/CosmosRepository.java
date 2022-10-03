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
package io.micronaut.data.cosmos.annotation;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.cosmos.common.Constants;
import io.micronaut.data.cosmos.operations.CosmosRepositoryOperations;
import io.micronaut.data.document.annotation.DocumentProcessorRequired;
import io.micronaut.data.document.model.query.builder.CosmosSqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryConfiguration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stereotype repository that configures a {@link Repository} as a {@link CosmosRepository}.
 *
 * @author Denis Stepanov
 * @since 3.8.0
 */
@RepositoryConfiguration(
        queryBuilder = CosmosSqlQueryBuilder.class,
        operations = CosmosRepositoryOperations.class,
        implicitQueries = true,
        namedParameters = true,
        typeRoles = @TypeRole(
            role = Constants.PARTITION_KEY_ROLE,
            type = PartitionKey.class
        )
)
@SqlQueryConfiguration(
    @SqlQueryConfiguration.DialectConfiguration(
        dialect = Dialect.ANSI,
        positionalParameterFormat = "@%s",
        positionalParameterName = "p%s",
        escapeQueries = false
    )
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
@DocumentProcessorRequired
public @interface CosmosRepository {

    /**
     * @return The datasource name.
     */
    @AliasFor(annotation = Repository.class, member = "value")
    String value() default "default";

    /**
     * @return The server name.
     */
    @AliasFor(annotation = Repository.class, member = "value")
    String serverName() default "default";

}
