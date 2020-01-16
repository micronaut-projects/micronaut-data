package example.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import example.domain.Owner;


@Repository
public interface OwnerRepository extends CrudRepository<Owner, Long>, JpaSpecificationExecutor<Owner> {

    @Override
    List<Owner> findAll();

    Optional<Owner> findByName(String name);

  	static Specification<Owner> ageGreaterThanThirty() {
        return (Specification<Owner>) (root, query, criteriaBuilder) -> criteriaBuilder.greaterThan(
                root.get("age"), 40
        );
    }    	
    
}