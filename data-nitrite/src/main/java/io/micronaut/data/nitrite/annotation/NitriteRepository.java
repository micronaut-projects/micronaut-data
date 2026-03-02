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
package io.micronaut.data.nitrite.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.document.annotation.DocumentProcessorRequired;
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder2;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stereotype repository annotation for NitriteDB.
 *
 * <p>This annotation configures the Micronaut Data annotation processor to generate repository
 * implementations that use NitriteDB as the backing store.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @NitriteRepository
 * public interface PersonRepository extends CrudRepository<Person, String> {
 *     List<Person> findByAgeGreaterThan(int age);
 *     Optional<Person> findByName(String name);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
@RepositoryConfiguration(
    queryBuilder = NitriteQueryBuilder2.class,
    operations = NitriteRepositoryOperations.class,
    implicitQueries = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Repository
@DocumentProcessorRequired
public @interface NitriteRepository {

  /**
   * @return The datasource name (for multi-datasource configurations).
   */
  @AliasFor(annotation = Repository.class, member = "value")
  String value() default "";
}
