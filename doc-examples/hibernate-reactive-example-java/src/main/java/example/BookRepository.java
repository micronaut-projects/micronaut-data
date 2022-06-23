
package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.function.Consumer;

// tag::repository[]
@Repository // <1>
interface BookRepository extends ReactorCrudRepository<Book, Long> { // <2>

    // tag::read[]
    Mono<Book> find(String title);

    Mono<BookDTO> findOne(String title);

    Flux<Book> findByPagesGreaterThan(int pageCount, Pageable pageable);

    Mono<Page<Book>> findByTitleLike(String title, Pageable pageable);

    Mono<Slice<Book>> list(Pageable pageable);
    // end::read[]

    @Transactional
    default Mono<Void> findByIdAndUpdate(Long id, Consumer<Book> bookConsumer) {
        return findById(id).map(book -> {
            bookConsumer.accept(book);
            return book;
        }).then();
    }

    // tag::save[]
    Mono<Book> save(Book entity);
    // end::save[]

    // tag::update[]
    Mono<Book> update(Book newBook);

    Mono<Void> update(@Id Long id, int pages);
    // end::delete[]

    // tag::delete[]
    @Override
    Mono<Long> deleteAll();

    Mono<Void> delete(String title);
    // end::delete[]
}
// end::repository[]
