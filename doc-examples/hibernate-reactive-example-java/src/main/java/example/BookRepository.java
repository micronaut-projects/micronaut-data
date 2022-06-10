
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

@Repository
interface BookRepository extends ReactorCrudRepository<Book, Long> {

    Mono<Book> find(String title);

    Flux<Book> findByPagesGreaterThan(int pageCount, Pageable pageable);

    Mono<Page<Book>> findByTitleLike(String title, Pageable pageable);

    Mono<Slice<Book>> list(Pageable pageable);

    @Transactional
    default Mono<Void> findByIdAndUpdate(Long id, Consumer<Book> bookConsumer) {
        return findById(id).map(book -> {
            bookConsumer.accept(book);
            return book;
        }).then();
    }

    Mono<Book> save(Book entity);

    Mono<Book> update(Book newBook);

    Mono<Void> update(@Id Long id, int pages);

    @Override
    Mono<Long> deleteAll();

    Mono<Void> delete(String title);

    Mono<BookDTO> findOne(String title);

}
