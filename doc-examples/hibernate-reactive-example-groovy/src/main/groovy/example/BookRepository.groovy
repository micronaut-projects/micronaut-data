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

// tag::repository[]
@Repository // <1>
abstract class BookRepository implements ReactorCrudRepository<Book, Long> { // <2>

    // tag::read[]
    abstract Mono<Book> find(String title);

    abstract Mono<Page<Book>> findByTitleLike(String title, Pageable pageable);

    abstract Mono<BookDTO> findOne(String title);

    abstract Flux<Book> findByPagesGreaterThan(int pageCount, Pageable pageable);

    abstract Mono<Slice<Book>> list(Pageable pageable);
    // end::read[]

    // tag::save[]
    abstract Mono<Book> save(Book entity);
    // end::save[]

    // tag::update[]
    @Transactional
    Mono<Void> findByIdAndUpdate(Long id, Consumer<Book> bookConsumer) {
        return findById(id).map(book -> {
            bookConsumer.accept(book)
            return book
        }).then()
    }

    abstract Mono<Book> update(Book newBook);

    abstract Mono<Void> update(@Id Long id, int pages);
    // end::update[]

    // tag::delete[]
    @Override
    abstract Mono<Long> deleteAll();

    abstract Mono<Void> delete(String title);
    // end::delete[]
}
// end::repository[]
