package example


import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import javax.transaction.Transactional
import java.util.function.Consumer

@Repository
abstract class BookRepository implements ReactorCrudRepository<Book, Long> {

    abstract Mono<Book> find(String title);

    abstract Flux<Book> findByPagesGreaterThan(int pageCount, Pageable pageable);

    abstract Mono<Page<Book>> findByTitleLike(String title, Pageable pageable);

    abstract Mono<Slice<Book>> list(Pageable pageable);

    @Transactional
    Mono<Void> findByIdAndUpdate(Long id, Consumer<Book> bookConsumer) {
        return findById(id).map(book -> {
            bookConsumer.accept(book)
            return book
        }).then()
    }

    abstract Mono<Book> save(Book entity);

    abstract Mono<Book> update(Book newBook);

    abstract Mono<Void> update(@Id Long id, int pages);

    @Override
    abstract Mono<Long> deleteAll();

    abstract Mono<Void> delete(String title);

    abstract Mono<BookDTO> findOne(String title);

}
