// tag::repository[]
package example;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository // <1>
public interface BookRepository extends CrudRepository<Book, Long> { // <2>
	// end::repository[]

	// tag::simple[]
	@Query("SELECT * FROM book b WHERE b.title = :title")
	Book findByTitle(String title);

	// tag::repository[]
}
// end::repository[]
