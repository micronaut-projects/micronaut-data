
package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import jakarta.validation.constraints.NotNull;
import java.util.List;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {

    @Override
    @Query("UPDATE User SET enabled = false WHERE id = :id")
    void deleteById(@NonNull @NotNull Long id);

    @Query("FROM User user WHERE enabled = false")
    List<User> findDisabled();
}

