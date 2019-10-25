package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Page;

public interface PageRepository extends GenericRepository<Page, Long> {
    Page save(long num);
}
