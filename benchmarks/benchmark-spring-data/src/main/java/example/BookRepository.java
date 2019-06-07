// tag::repository[]
package example;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository // <1>
public interface BookRepository extends CrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple[]
    Book findByTitle(String title);


// tag::repository[]
}
// end::repository[]
