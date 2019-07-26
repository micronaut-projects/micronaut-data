package io.micronaut.data.jdbc.h2;

import io.micronaut.data.tck.repositories.CompanyRepository;

import io.micronaut.data.jdbc.annotation.JdbcRepository;

@JdbcRepository(dialectName = "H2")
public interface H2CompanyRepository extends CompanyRepository {
}
