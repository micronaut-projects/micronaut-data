package io.micronaut.data.hibernate.reactive.propagate;

import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import reactor.core.publisher.Mono;

@Singleton
public class FooService {

    @Inject
    FooRepository repository;

    @Inject
    RequestContext requestContext;

    Mono<Foo> create() {
        return repository.insert(new Foo(requestContext.getId(), "DEFAULT_NAME"))
            .flatMap(entity -> Mono.deferContextual(contextView -> {
                ReactorPropagation.findPropagatedContext(contextView).orElse(PropagatedContext.empty()).propagate(() -> {
                    entity.setName(requestContext.getName());
                    return null;
                });
                return repository.update(entity);
            }));
    }

    @Transactional
    Mono<Foo> createTransactional() {
        return Mono.deferContextual(contextView -> {
            return ReactorPropagation.findPropagatedContext(contextView).orElse(PropagatedContext.empty()).propagate(() -> {
                return repository.insert(
                    new Foo(requestContext.getId(), requestContext.getName())
                );
            });
        });
    }

    Mono<Foo> read() {
        return repository.findById(requestContext.getId());
    }
}
