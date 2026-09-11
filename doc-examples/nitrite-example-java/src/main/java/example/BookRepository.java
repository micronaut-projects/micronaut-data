package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

import java.util.List;
import java.util.Optional;

// tag::repository[]
@NitriteRepository
public interface BookRepository extends CrudRepository<Book, String>, JpaSpecificationExecutor<Book> {
// end::repository[]

    Optional<Book> findByTitle(String title);

    // tag::join-students[]
    // Eagerly fetch students for all books (MANY_TO_MANY)
    @Join("students")
    List<Book> findAll();
    // end::join-students[]

    // tag::update[]
    void update(@Id String id, String title);
    // end::update[]

    // tag::findByAuthorName[]
    List<Book> findByAuthorName(String authorName);
    // end::findByAuthorName[]

    // tag::case-insensitive[]
    // Case-insensitive search by title
    List<Book> findByTitleIgnoreCase(String title);

    // Case-insensitive contains
    List<Book> findByTitleContainsIgnoreCase(String keyword);
    // end::case-insensitive[]

    // tag::in-queries[]
    // IN query with List parameter
    List<Book> findByTitleIn(List<String> titles);

    // IN query with array parameter
    List<Book> findByTitleIn(String[] titles);

    // NOT IN query
    List<Book> findByTitleNotIn(List<String> titles);
    // end::in-queries[]

    // tag::projections[]
    // Single-field projection - returns List<String>
    List<String> findDistinctTitle();

    // Count query - returns long
    long countByAuthorId(String authorId);

    // Aggregate functions
    Long countByTitle(String title);
    // end::projections[]

    class Specifications {
        // tag::association-navigation[]
        static PredicateSpecification<Book> authorNameEquals(String name) {
            return (root, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("author").get("name"), name);
        }
        // end::association-navigation[]
    }

// tag::repository[]
}
// end::repository[]

