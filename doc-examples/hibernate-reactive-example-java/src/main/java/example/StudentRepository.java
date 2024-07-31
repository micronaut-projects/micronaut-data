package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

@Repository
public interface StudentRepository extends ReactorCrudRepository<Student, Long> {

    @NonNull
    @Override
    @Join(value = "courses", type = Join.Type.LEFT)
    Mono<Student> findById(@NonNull Long id);
}
