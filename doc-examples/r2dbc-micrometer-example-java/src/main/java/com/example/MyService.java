package com.example;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import reactor.core.publisher.Mono;

@Singleton
public class MyService {
    @Transactional(Transactional.TxType.MANDATORY)
    public Mono<String> getMessage() {
        return Mono.just("hello");
    }
}
