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
package io.micronaut.data.mongo.annotation;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder;
import io.micronaut.data.mongo.operations.MongoRepositoryOperations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stereotype repository that configures a {@link Repository} as a {@link MongoRepository}.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@RepositoryConfiguration(
        queryBuilder = MongoQueryBuilder.class,
        operations = MongoRepositoryOperations.class,
        implicitQueries = true,
        namedParameters = false
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
public @interface MongoRepository {
}
