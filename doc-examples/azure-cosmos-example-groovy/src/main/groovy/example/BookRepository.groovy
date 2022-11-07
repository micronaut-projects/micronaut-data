// tag::repository[]
package example

import io.micronaut.context.annotation.Executable
import io.micronaut.data.annotation.Id
import io.micronaut.data.cosmos.annotation.CosmosRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.repository.CrudRepository

@CosmosRepository // <1>
interface BookRepository extends CrudRepository<Book, String> { // <2>
// end::repository[]

    // tag::simple[]
    Book findByTitle(String title)

    Book getByTitle(String title)

    Book retrieveByTitle(String title)
    // end::simple[]

    // tag::greaterthan[]
    List<Book> findByPagesGreaterThan(int pageCount)
    // end::greaterthan[]

    // tag::simple-alt[]
    // tag::repository[]
    @Executable
    Book find(String title)
    // end::simple-alt[]
    // end::repository[]

    // tag::pageable[]
    List<Book> findByPagesGreaterThan(int pageCount, Pageable pageable)

    Slice<Book> list(Pageable pageable)
    // end::pageable[]

    // tag::simple-projection[]
    List<String> findTitleByPagesGreaterThan(int pageCount)
    // end::simple-projection[]

    // tag::ordering[]
    List<Book> listOrderByTitle()

    List<Book> listOrderByTitleDesc()
    // end::ordering[]

    // tag::save[]
    Book persist(Book entity)
    // end::save[]

    // tag::save2[]
    Book persist(String title, int pages)
    // end::save2[]

    // tag::update[]
    void update(@Id String id, int pages)

    void update(@Id String id, String title)
    // end::update[]

    // tag::update2[]
    void updateByTitle(String title, int pages)
    // end::update2[]

    // tag::deleteall[]
    void deleteAll()
    // end::deleteall[]

    // tag::deleteone[]
    void delete(String title)
    // end::deleteone[]

    // tag::dto[]
    BookDTO findOne(String title)
    // end::dto[]

// tag::repository[]
}
// end::repository[]
