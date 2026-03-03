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
package io.micronaut.data.nitrite.operations;

import io.micronaut.data.operations.PrimaryRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import java.io.Serializable;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.repository.ObjectRepository;

/**
 * Nitrite-specific repository operations.
 *
 * <p>This interface extends {@link RepositoryOperations} to integrate with Micronaut Data's
 * repository abstraction.
 *
 * <p>It also implements {@link PrimaryRepositoryOperations} so Micronaut Data can reliably pick
 * Nitrite as the primary {@link RepositoryOperations} implementation for repositories annotated
 * with {@code @NitriteRepository}.
 */
public interface NitriteRepositoryOperations
    extends RepositoryOperations, PrimaryRepositoryOperations {
  /**
   * @return the underlying Nitrite database instance
   */
  Nitrite getDatabase();

  /**
   * Get (or create) the Nitrite {@link ObjectRepository} for an entity type.
   *
   * @param entityType the entity type
   * @return the repository
   * @param <T> the entity type
   * @param <ID> the ID type
   */
  <T, ID extends Serializable> ObjectRepository<T> getRepository(Class<T> entityType);

  /**
   * Get (or create) the Nitrite {@link ObjectRepository} for an entity type and discriminator.
   *
   * @param entityType the entity type
   * @param discriminator the discriminator (for {@code @MappedEntity(discriminator=...)} use-cases)
   * @return the repository
   * @param <T> the entity type
   */
  <T> ObjectRepository<T> getRepository(Class<T> entityType, String discriminator);
}
