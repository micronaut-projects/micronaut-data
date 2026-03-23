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
import java.io.Serializable;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.repository.ObjectRepository;

/**
 * Nitrite specialized repository operations.
 *
 * @since 1.0.0
 */
public interface NitriteRepositoryOperations extends PrimaryRepositoryOperations {

  /**
   * Returns the underlying Nitrite database instance.
   * @return the underlying Nitrite database instance
   */
  Nitrite getDatabase();

  /**
   * Get the Nitrite repository for the given entity type.
   *
   * @param entityType the entity type
   * @return the repository
   * @param <T> the entity type
   * @param <ID> the ID type
   */
  <T, ID extends Serializable> ObjectRepository<T> getRepository(Class<T> entityType);

  /**
   * Get the Nitrite repository for the given entity type and discriminator.
   *
   * @param entityType the entity type
   * @param discriminator the discriminator (for {@code @MappedEntity(discriminator=...)} use-cases)
   * @return the repository
   * @param <T> the entity type
   */
  <T> ObjectRepository<T> getRepository(Class<T> entityType, String discriminator);
}
