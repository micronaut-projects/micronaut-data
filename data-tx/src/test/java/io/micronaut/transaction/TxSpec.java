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

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public class TxSpec {

    @Test
    public void testReactiveTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty());

            Mono<String> job1 = exampleBean.doWorkMono("job1");
            String job = Mono.from(
                job1
            ).block();

            Assertions.assertEquals(List.of("OPEN TX CONNECTION_1", "COMMIT TX CONNECTION_1"), txManager.getTransactionsLog());
            Assertions.assertEquals("Doing job: job1 in transaction: CONNECTION_1", job);
        }
    }

    @Test
    public void testTwoFluxReactiveTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty());
            List<String> results = new ArrayList<>();

            Flux.from(
                exampleBean.doWorkFlux("job1").doOnNext(results::add)
            ).thenMany(
                Flux.from(
                    exampleBean.doWorkFlux("job2").doOnNext(results::add)
                )
            ).collectList().block();

            Assertions.assertEquals(List.of("OPEN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "OPEN TX CONNECTION_2", "COMMIT TX CONNECTION_2"), txManager.getTransactionsLog());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_2"), results);
        }
    }

    @Test
    public void testTwoMonoReactiveTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty());
            List<String> results = new ArrayList<>();

            Mono.from(
                exampleBean.doWorkMono("job1").doOnNext(results::add)
            ).flatMap(result ->
                Mono.from(
                    exampleBean.doWorkMono("job2").doOnNext(results::add)
                )
            ).block();

            Assertions.assertEquals(List.of("OPEN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "OPEN TX CONNECTION_2", "COMMIT TX CONNECTION_2"), txManager.getTransactionsLog());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_2"), results);
        }
    }

    @Test
    public void testTwoMonoReactiveTx2() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty());
            List<String> results = new ArrayList<>();

            Flux.from(
                exampleBean.doWorkMono("job1").doOnNext(results::add)
            ).then(
                Mono.from(
                    exampleBean.doWorkMono("job2").doOnNext(results::add)
                )
            ).block();

            Assertions.assertEquals(List.of("OPEN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "OPEN TX CONNECTION_2", "COMMIT TX CONNECTION_2"), txManager.getTransactionsLog());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_2"), results);
        }
    }


    // end::test[]
}
