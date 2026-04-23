package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.Organization;

import java.util.UUID;

public interface OrganizationRepository extends CrudRepository<Organization, UUID> {
}
