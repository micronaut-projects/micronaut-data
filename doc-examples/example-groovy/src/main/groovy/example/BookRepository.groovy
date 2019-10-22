// tag::repository[]
package example

import io.micronaut.data.annotation.*
import io.micronaut.data.model.*
import io.micronaut.data.repository.CrudRepository

@Repository // <1>
interface BookRepository extends CrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple[]
    Book findByTitle(String title)

    Book getByTitle(String title)

    Book retrieveByTitle(String title)
    // end::simple[]

    // tag::simple-alt[]
    // tag::repository[]
    Book find(String title)
    // end::simple-alt[]
    // end::repository[]

    // tag::greaterthan[]
    List<Book> findByPagesGreaterThan(int pageCount)
    // end::greaterthan[]

    // tag::logical[]
    List<Book> findByPagesGreaterThanOrTitleLike(int pageCount, String title)
    // end::logical[]

    // tag::pageable[]
    List<Book> findByPagesGreaterThan(int pageCount, Pageable pageable)

    Page<Book> findByTitleLike(String title, Pageable pageable)

    Slice<Book> list(Pageable pageable)
    // end::pageable[]

    // tag::simple-projection[]
    List<String> findTitleByPagesGreaterThan(int pageCount)
    // end::simple-projection[]

    // tag::top-projection[]
    List<Book> findTop3ByTitleLike(String title)
    // end::top-projection[]

    // tag::ordering[]
    List<Book> listOrderByTitle()

    List<Book> listOrderByTitleDesc()
    // end::ordering[]

    // tag::explicit[]
    @Query("FROM Book b WHERE b.title = :t ORDER BY b.title")
    List<Book> listBooks(String t)
    // end::explicit[]

    // tag::save[]
    Book persist(Book entity)
    // end::save[]

    // tag::save2[]
    Book persist(String title, int pages)
    // end::save2[]

    // tag::update[]
    void update(@Id Long id, int pages)
    // end::update[]

    // tag::update2[]
    void updateByTitle(String title, int pages)
    // end::update2[]

    // tag::update3[]
    void updatePages(@Id Long id, int pages)
    // end::update3[]

    // tag::deleteall[]
    void deleteAll()
    // end::deleteall[]

    // tag::deleteone[]
    void delete(String title)
    // end::deleteone[]

    // tag::deleteby[]
    void deleteByTitleLike(String title)
    // end::deleteby[]

    // tag::dto[]
    BookDTO findOne(String title);
    // end::dto[]

    // tag::native[]
    @Query(value = "select * from books b where b.title like :title limit 5",
            nativeQuery = true)
    List<Book> findNativeBooks(String title)
    // end::native[]

// tag::repository[]
}
// end::repository[]
