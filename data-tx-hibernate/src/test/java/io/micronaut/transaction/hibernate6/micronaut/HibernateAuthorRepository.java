package io.micronaut.transaction.hibernate6.micronaut;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.repositories.AuthorRepository;

@Repository
public interface HibernateAuthorRepository extends AuthorRepository {
}
