package io.micronaut.data.nitrite.tck;

import io.micronaut.data.document.tck.repositories.AuthorRepository;
import io.micronaut.data.document.tck.repositories.BookRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;

@NitriteRepository
public abstract class NitriteBookRepository extends BookRepository {
    public NitriteBookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }
}
