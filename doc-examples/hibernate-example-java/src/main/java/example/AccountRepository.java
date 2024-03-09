package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Repository
public interface AccountRepository extends CrudRepository<@Valid Account, @Min(0) Long> {
}
