
package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.constraints.NotNull;

@Repository
public interface UserRepository extends ReactorCrudRepository<User, Long> {

    @Override
    @Query("UPDATE User SET enabled = false WHERE id = :id")
    Mono<Long> deleteById(@NonNull @NotNull Long id);

    @Query("FROM User user WHERE enabled = false")
    Flux<User> findDisabled();
}

