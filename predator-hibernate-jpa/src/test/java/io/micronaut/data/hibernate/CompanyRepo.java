package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;


@Repository
public interface CompanyRepo extends CrudRepository<Company, Long> {

    void update(@Id Long id, String name);
}
