package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;

@JdbcRepository(dialectName = "H2")
public interface H2AuthorRepository extends io.micronaut.data.tck.repositories.AuthorRepository  {
}
