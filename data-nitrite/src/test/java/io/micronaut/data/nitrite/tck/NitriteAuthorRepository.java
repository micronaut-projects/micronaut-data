package io.micronaut.data.nitrite.tck;

import io.micronaut.data.document.tck.repositories.AuthorRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;

/**
 * TCK-specific author repository backed by Nitrite.
 */
@NitriteRepository
public interface NitriteAuthorRepository extends AuthorRepository {
}
