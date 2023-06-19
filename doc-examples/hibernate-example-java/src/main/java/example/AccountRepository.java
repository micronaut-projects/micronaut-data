package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

@Repository
public interface AccountRepository extends CrudRepository<@jakarta.validation.Valid Account, @jakarta.validation.constraints.Min(0) Long> {
}
