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
 */
public interface NitriteRepositoryOperations
    extends RepositoryOperations, PrimaryRepositoryOperations {
  Nitrite getDatabase();

  <T, ID extends Serializable> ObjectRepository<T> getRepository(Class<T> entityType);

  <T> ObjectRepository<T> getRepository(Class<T> entityType, String discriminator);
}
