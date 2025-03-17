package io.micronaut.data.tck.repositories.embedded;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.embedded.BookEntity;
import io.micronaut.data.tck.entities.embedded.BookState;

import java.util.List;

public interface BookEntityRepository extends CrudRepository<BookEntity, Long> {

    List<BookEntity> findAllByResourceState(BookState state);
}
