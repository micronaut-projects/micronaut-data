package io.micronaut.data.spring.hibernate.micronaut;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.repositories.AuthorRepository;
import io.micronaut.data.tck.repositories.BookRepository;

@Repository
public abstract class HibernateBookRepository extends BookRepository {

    public HibernateBookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

}
