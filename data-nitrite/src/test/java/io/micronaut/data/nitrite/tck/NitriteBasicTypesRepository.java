package io.micronaut.data.nitrite.tck;

import io.micronaut.data.document.tck.repositories.BasicTypesRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;

/**
 * TCK-oriented repository for testing basic data types against Nitrite.
 */
@NitriteRepository
public interface NitriteBasicTypesRepository extends BasicTypesRepository {
}
