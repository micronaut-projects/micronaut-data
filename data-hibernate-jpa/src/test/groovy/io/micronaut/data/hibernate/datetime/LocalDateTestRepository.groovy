package io.micronaut.data.hibernate.datetime

import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.repository.GenericRepository

@Repository
interface LocalDateTestRepository extends GenericRepository<LocalDateTest, Long> {
    LocalDateTest save(String name);

    Page<LocalDateTest> findAll(Pageable pageable);

    int count()
}
