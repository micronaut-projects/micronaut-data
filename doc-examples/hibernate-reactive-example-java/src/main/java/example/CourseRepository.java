package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

@Repository
public interface CourseRepository extends ReactorCrudRepository<Course, Long> {

    @NonNull
    @Override
    @Join(value = "students", type = Join.Type.LEFT)
    Mono<Course> findById(@NonNull Long id);
}
