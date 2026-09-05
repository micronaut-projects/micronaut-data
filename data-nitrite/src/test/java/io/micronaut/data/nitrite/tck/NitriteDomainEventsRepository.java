package io.micronaut.data.nitrite.tck;

import io.micronaut.data.document.tck.repositories.DomainEventsRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;

@NitriteRepository
public interface NitriteDomainEventsRepository extends DomainEventsRepository {
}
