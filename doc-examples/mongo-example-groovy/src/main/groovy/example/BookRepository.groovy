
// tag::repository[]
package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId

@MongoRepository // <1>
interface BookRepository extends CrudRepository<Book, ObjectId> { // <2>
// end::repository[]

    // tag::simple[]
    Book findByTitle(String title);

    Book getByTitle(String title);

    Book retrieveByTitle(String title);
    // end::simple[]

    // tag::greaterthan[]
    List<Book> findByPagesGreaterThan(int pageCount);
    // end::greaterthan[]

    // tag::logical[]
//    List<Book> findByPagesGreaterThanOrTitleRegex(int pageCount, String title);
    // end::logical[]

    // tag::simple-alt[]
    // tag::repository[]
    Book find(String title);
    // end::simple-alt[]
    // end::repository[]

    // tag::pageable[]
    List<Book> findByPagesGreaterThan(int pageCount, Pageable pageable);

    Page<Book> findByTitleRegex(String title, Pageable pageable);

    Slice<Book> list(Pageable pageable);
    // end::pageable[]

    // tag::simple-projection[]
    List<String> findTitleByPagesGreaterThan(int pageCount);
    // end::simple-projection[]

    // tag::top-projection[]
    List<Book> findTop3ByTitleRegex(String title);
    // end::top-projection[]

    // tag::ordering[]
    List<Book> listOrderByTitle();

    List<Book> listOrderByTitleDesc();
    // end::ordering[]

    // tag::save[]
    Book persist(Book entity);
    // end::save[]

    // tag::save2[]
    Book persist(String title, int pages);
    // end::save2[]

    // tag::update[]
    void update(@Id ObjectId id, int pages);

    void update(@Id ObjectId id, String title);
    // end::update[]

    // tag::update2[]
    void updateByTitle(String title, int pages);
    // end::update2[]

    // tag::deleteall[]
    void deleteAll();
    // end::deleteall[]

    // tag::deleteone[]
    void delete(String title);
    // end::deleteone[]

    // tag::deleteby[]
    void deleteByTitleRegex(String title);
    // end::deleteby[]

    // tag::dto[]
    BookDTO findOne(String title);
    // end::dto[]

// tag::repository[]
}
// end::repository[]
