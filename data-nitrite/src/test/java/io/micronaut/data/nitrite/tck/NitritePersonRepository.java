package io.micronaut.data.nitrite.tck;

import io.micronaut.data.document.tck.repositories.PersonRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;

@NitriteRepository
public interface NitritePersonRepository extends PersonRepository {
}
