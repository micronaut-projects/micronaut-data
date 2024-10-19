package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.async.AsyncPageableRepository;
import io.micronaut.data.repository.jpa.async.AsyncJpaSpecificationExecutor;
import io.micronaut.data.tck.entities.Book;

public interface BookAsyncRepository extends AsyncPageableRepository<Book, Long>, AsyncJpaSpecificationExecutor<Book> {
}
