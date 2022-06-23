package io.micronaut.data.hibernate.reactive;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.EntityIdClass;
import io.micronaut.data.tck.entities.EntityWithIdClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;

@Repository
public interface EntityWithIdClassRepository extends ReactorCrudRepository<EntityWithIdClass, EntityIdClass> {
    Flux<EntityWithIdClass> findById1(Long id1);
    Flux<EntityWithIdClass> findById2(Long id2);

    @Transactional
    default Mono<Void> findByIdAndDelete(EntityIdClass id) {
        return findById(id).flatMap(this::delete).then();
    }

}
