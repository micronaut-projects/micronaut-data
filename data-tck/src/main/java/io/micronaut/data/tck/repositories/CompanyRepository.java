package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Company;

public interface CompanyRepository extends CrudRepository<Company, Long> {

    void update(@Id Long id, @Parameter("name") String name);
}

