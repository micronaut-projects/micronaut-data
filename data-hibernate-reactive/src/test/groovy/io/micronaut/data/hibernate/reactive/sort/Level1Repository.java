package io.micronaut.data.hibernate.reactive.sort;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@Repository
public interface Level1Repository extends ReactorCrudRepository<Level1, Long> {

    @Query("select distinct level1_ from Level1 level1_" +
            " left join fetch level1_.level2 level1_level2_" +
            " left join fetch level1_level2_.level3 level1_level2_level3_")
    @NonNull
    Flux<Level1> findAll(@NonNull Sort sort);
}
