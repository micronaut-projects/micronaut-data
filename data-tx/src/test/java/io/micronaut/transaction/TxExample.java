/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.transaction;

import io.micronaut.data.connection.reactive.ReactiveConnectionStatus;
import io.micronaut.data.connection.reactive.ReactiveConnectionSynchronization;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class TxExample {

    private final ReactiveTxManager txManager;
    private final OpLogger opLogger;

    public TxExample(ReactiveTxManager txManager, OpLogger opLogger) {
        this.txManager = txManager;
        this.opLogger = opLogger;
    }

    @Transactional
    Mono<List<String>> doTwoJobsMono() {
        List<String> results = new ArrayList<>();
        return doWorkMono("job1").doOnNext(results::add)
            .then(doWorkMono("job2").doOnNext(results::add))
            .thenReturn(results);
    }

    @Transactional
    Mono<List<String>> doTwoJobsMonoFromFlux() {
        List<String> results = new ArrayList<>();
        return doWorkMonoFromFlux("job1").doOnNext(results::add)
            .then(doWorkMonoFromFlux("job2").doOnNext(results::add))
            .thenReturn(results);
    }

    @Transactional
    Flux<List<String>> doTwoJobsFlux() {
        List<String> results = new ArrayList<>();
        return doWorkFlux("job1").doOnNext(results::add)
            .thenMany(doWorkFlux("job2").doOnNext(results::add))
            .thenMany(Flux.just(results));
    }

    @Transactional
    Mono<String> doWorkMono(String taskName) {
        return Mono.deferContextual(contextView -> {
            String txName = txManager.findTransactionStatus(contextView).get().getConnection();
            return Mono.just("Doing job: " + taskName + " in transaction: " + txName);
        });
    }

    @Transactional
    Mono<String> doWorkMonoFailingWithEvents(String taskName) {
        return Mono.deferContextual(contextView -> {
            ReactiveTransactionStatus<String> txStatus = txManager.findTransactionStatus(contextView).get();
            ReactiveConnectionStatus<String> connStatus = (ReactiveConnectionStatus<String>) txStatus.getConnectionStatus();
            String connection = connStatus.getConnection();
            connStatus.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
                @Override
                public Publisher<Void> onComplete() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_COMPLETE " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onError(Throwable throwable) {
                    return Mono.defer(() -> {
                        opLogger.add("ON_ERROR " + throwable.getMessage() + " " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onCancel() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_CANCEL " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onClose() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_CLOSE " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> afterClose() {
                    return Mono.defer(() -> {
                        opLogger.add("AFTER_CLOSE " + connection);
                        return Mono.empty();
                    });
                }
            });
            String txName = txStatus.getConnection();
            return Mono.error(new RuntimeException("Failed job: " + taskName + " in transaction: " + txName));
        });
    }

    @Transactional
    Mono<String> doWorkMonoWithEvents(String taskName) {
        return Mono.deferContextual(contextView -> {
            ReactiveTransactionStatus<String> txStatus = txManager.findTransactionStatus(contextView).get();
            ReactiveConnectionStatus<String> connStatus = (ReactiveConnectionStatus<String>) txStatus.getConnectionStatus();
            String connection = connStatus.getConnection();
            connStatus.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
                @Override
                public Publisher<Void> onComplete() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_COMPLETE " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onError(Throwable throwable) {
                    return Mono.defer(() -> {
                        opLogger.add("ON_ERROR " + throwable.getMessage() + " " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onCancel() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_CANCEL " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onClose() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_CLOSE " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> afterClose() {
                    return Mono.defer(() -> {
                        opLogger.add("AFTER_CLOSE " + connection);
                        return Mono.empty();
                    });
                }
            });
            String txName = txStatus.getConnection();
            return Mono.just("Doing job: " + taskName + " in transaction: " + txName);
        });
    }

    Mono<String> doWorkMonoFromFlux(String taskName) {
        return Mono.from(txManager.withTransactionFlux(status -> {
            String txName = status.getConnection();
            return Flux.just("Doing job: " + taskName + " in transaction: " + txName);
        }));
    }

    Mono<String> doWorkMonoFromFluxWithEvents(String taskName) {
        return Mono.from(txManager.withTransactionFlux(status -> {
            ReactiveConnectionStatus<String> connStatus = (ReactiveConnectionStatus<String>) status.getConnectionStatus();
            String connection = connStatus.getConnection();
            connStatus.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
                @Override
                public Publisher<Void> onComplete() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_COMPLETE " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onError(Throwable throwable) {
                    return Mono.defer(() -> {
                        opLogger.add("ON_ERROR " + throwable.getMessage() + " " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onCancel() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_CANCEL " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> onClose() {
                    return Mono.defer(() -> {
                        opLogger.add("ON_CLOSE " + connection);
                        return Mono.empty();
                    });
                }

                @Override
                public Publisher<Void> afterClose() {
                    return Mono.defer(() -> {
                        opLogger.add("AFTER_CLOSE " + connection);
                        return Mono.empty();
                    });
                }
            });
            String txName = status.getConnection();
            return Flux.just("Doing job: " + taskName + " in transaction: " + txName);
        }));
    }

    @javax.transaction.Transactional
    Flux<String> doWorkFlux(String taskName) {
        return Flux.deferContextual(contextView -> {
            String txName = txManager.findTransactionStatus(contextView).get().getConnection();
            return Mono.just("Doing job: " + taskName + " in transaction: " + txName);
        });
    }
}
