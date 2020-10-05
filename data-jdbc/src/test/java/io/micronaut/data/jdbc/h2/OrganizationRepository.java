package io.micronaut.data.jdbc.h2;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.Organization;

import java.util.UUID;

public interface OrganizationRepository extends CrudRepository<Organization, UUID> {
}
