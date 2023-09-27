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
    public void testReactiveTxMono() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            Mono<String> job1 = exampleBean.doWorkMono("job1");
            String job = Mono.from(
                job1
            ).block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "CLOSE CONNECTION_1"), opLogger.getLogs());
            Assertions.assertEquals("Doing job: job1 in transaction: CONNECTION_1", job);
        }
    }

    @Test
    public void testReactiveTxMonoWithEvents() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            String job = exampleBean.doWorkMonoWithEvents("job1").block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "ON_COMPLETE CONNECTION_1", "ON_CLOSE CONNECTION_1", "CLOSE CONNECTION_1", "AFTER_CLOSE CONNECTION_1"), opLogger.getLogs());
            Assertions.assertEquals("Doing job: job1 in transaction: CONNECTION_1", job);
        }
    }

    @Test
    public void testReactiveTxMonoFailingWithEvents() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            try {
                exampleBean.doWorkMonoFailingWithEvents("job1").block();
            } catch (Exception e) {
                // Ignore
            }

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "ROLLBACK TX CONNECTION_1", "ON_ERROR Failed job: job1 in transaction: CONNECTION_1 CONNECTION_1", "ON_CLOSE CONNECTION_1", "CLOSE CONNECTION_1", "AFTER_CLOSE CONNECTION_1"), opLogger.getLogs());
        }
    }

    @Test
    public void testReactiveTxMonoFromFlux() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            Mono<String> job1 = exampleBean.doWorkMonoFromFlux("job1");
            String job = Mono.from(
                job1
            ).block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "CANCEL TX CONNECTION_1", "CLOSE CONNECTION_1"), opLogger.getLogs());
            Assertions.assertEquals("Doing job: job1 in transaction: CONNECTION_1", job);
        }
    }

    @Test
    public void testReactiveTxMonoFromFluxWithEvents() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            Mono<String> job1 = exampleBean.doWorkMonoFromFluxWithEvents("job1");
            String job = Mono.from(
                job1
            ).block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "CANCEL TX CONNECTION_1", "ON_CANCEL CONNECTION_1", "ON_CLOSE CONNECTION_1", "CLOSE CONNECTION_1", "AFTER_CLOSE CONNECTION_1"), opLogger.getLogs());
            Assertions.assertEquals("Doing job: job1 in transaction: CONNECTION_1", job);
        }
    }

    @Test
    public void testReactiveTxFlux() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            Flux<String> job1 = exampleBean.doWorkFlux("job1");
            String job = job1.collectList().block().get(0);

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "CLOSE CONNECTION_1"), opLogger.getLogs());
            Assertions.assertEquals("Doing job: job1 in transaction: CONNECTION_1", job);
        }
    }

    @Test
    public void testTwoFluxReactiveTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());
            List<String> results = new ArrayList<>();

            Flux.from(
                exampleBean.doWorkFlux("job1").doOnNext(results::add)
            ).thenMany(
                Flux.from(
                    exampleBean.doWorkFlux("job2").doOnNext(results::add)
                )
            ).collectList().block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "CLOSE CONNECTION_1", "OPEN CONNECTION_2", "BEGIN TX CONNECTION_2", "COMMIT TX CONNECTION_2", "CLOSE CONNECTION_2"), opLogger.getLogs());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_2"), results);
        }
    }

    @Test
    public void testTwoMonoReactiveTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());
            List<String> results = new ArrayList<>();

            Mono.from(
                exampleBean.doWorkMono("job1").doOnNext(results::add)
            ).flatMap(result ->
                Mono.from(
                    exampleBean.doWorkMono("job2").doOnNext(results::add)
                )
            ).block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "CLOSE CONNECTION_1", "OPEN CONNECTION_2", "BEGIN TX CONNECTION_2", "COMMIT TX CONNECTION_2", "CLOSE CONNECTION_2"), opLogger.getLogs());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_2"), results);
        }
    }

    @Test
    public void testTwoMonoReactiveTx2() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());
            List<String> results = new ArrayList<>();

            Flux.from(
                exampleBean.doWorkMono("job1").doOnNext(results::add)
            ).then(
                Mono.from(
                    exampleBean.doWorkMono("job2").doOnNext(results::add)
                )
            ).block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "CLOSE CONNECTION_1", "OPEN CONNECTION_2", "BEGIN TX CONNECTION_2", "COMMIT TX CONNECTION_2", "CLOSE CONNECTION_2"), opLogger.getLogs());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_2"), results);
        }
    }

    @Test
    public void testTwoMonoJobsInOneTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            List<String> results = exampleBean.doTwoJobsMono().block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "CLOSE CONNECTION_1"), opLogger.getLogs());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_1"), results);
        }
    }

    @Test
    public void testTwoMonoFromFluxJobsInOneTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            List<String> results = exampleBean.doTwoJobsMonoFromFlux().block();

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "CLOSE CONNECTION_1"), opLogger.getLogs());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_1"), results);
        }
    }

    @Test
    public void testTwoFluxJobsInOneTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(opLogger.getLogs().isEmpty());

            List<String> results = exampleBean.doTwoJobsFlux().collectList().block().get(0);

            Assertions.assertEquals(List.of("OPEN CONNECTION_1", "BEGIN TX CONNECTION_1", "COMMIT TX CONNECTION_1", "CLOSE CONNECTION_1"), opLogger.getLogs());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: CONNECTION_1", "Doing job: job2 in transaction: CONNECTION_1"), results);
        }
    }

    // end::test[]
}
