
package example;

import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Optional;

// tag::repository[]
@MongoRepository
public interface PersonRepository extends CrudRepository<Person, ObjectId>, JpaSpecificationExecutor<Person> {
// end::repository[]

    // tag::find[]
    Optional<Person> findOne(PredicateSpecification<Person> spec);

    Optional<Person> findOne(QuerySpecification<Person> spec);

    List<Person> findAll(PredicateSpecification<Person> spec);

    List<Person> findAll(QuerySpecification<Person> spec);

    List<Person> findAll(PredicateSpecification<Person> spec, Sort sort);

    List<Person> findAll(QuerySpecification<Person> spec, Sort sort);

    Page<Person> findAll(PredicateSpecification<Person> spec, Pageable pageable);

    Page<Person> findAll(QuerySpecification<Person> spec, Pageable pageable);
    // end::find[]

    // tag::count[]
    long count(PredicateSpecification<Person> spec);

    long count(QuerySpecification<Person> spec);
    // end::count[]

    // tag::update[]
    long updateAll(UpdateSpecification<Person> spec);
    // end::update[]

    // tag::delete[]
    long deleteAll(PredicateSpecification<Person> spec);

    long deleteAll(DeleteSpecification<Person> spec);
    // end::delete[]

    // tag::method_array_contains[]
    List<Person> findByInterestsArrayContains(String... interests);
    // end::method_array_contains[]

    // tag::specifications[]
    // tag::allSpecifications[]
    class Specifications {

        static PredicateSpecification<Person> nameEquals(String name) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("name"), name);
        }

        static PredicateSpecification<Person> ageIsLessThan(int age) {
            return (root, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("age"), age);
        }

        // end::specifications[]
        static UpdateSpecification<Person> setNewName(String newName) {
            return (root, query, criteriaBuilder) -> {
                // tag::setUpdate[]
                query.set(root.get("name"), newName);
                // end::setUpdate[]
                return null;
            };
        }

        // tag::spec_array_contains[]
        static PredicateSpecification<Person> interestsArrayContains(String... interests) {
            return (root, criteriaBuilder) -> ((PersistentEntityCriteriaBuilder) criteriaBuilder).arrayContains(root.get("interests"), criteriaBuilder.literal(interests));
        }
        // end::spec_array_contains[]

        // tag::specifications[]
    }
    // end::allSpecifications[]
    // end::specifications[]
// tag::repository[]
}
// end::repository[]
