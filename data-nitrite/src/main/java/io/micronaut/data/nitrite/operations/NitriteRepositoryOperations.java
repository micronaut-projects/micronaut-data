package io.micronaut.data.nitrite.operations;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.operations.PrimaryRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import org.dizitart.no2.Nitrite;

/**
 * NitriteDB specialized {@link RepositoryOperations}.
 *
 * @since 1.0.0
 */
public interface NitriteRepositoryOperations extends RepositoryOperations, PrimaryRepositoryOperations {

  /**
   * Returns the underlying Nitrite database.
   * @return the underlying Nitrite database instance
   */
  @NonNull
  Nitrite getDatabase();
}
