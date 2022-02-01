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
package benchmark;

import example.Book;
import example.BookRepository;
import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@State(Scope.Benchmark)
public class SimpleQuery {

    ApplicationContext applicationContext;
    BookRepository bookRepository;
    MongoDBContainer mongoDBContainer;

    @Setup
    public void prepare() {
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo").withTag("5"));
        mongoDBContainer.start();
        Map<String, Object> props = new HashMap<>();
        props.put("mongodb.uri", mongoDBContainer.getReplicaSetUrl());
        this.applicationContext = ApplicationContext.run(props);
        this.bookRepository = applicationContext.getBean(BookRepository.class);
        this.bookRepository.saveAll(Arrays.asList(
                new Book("The Stand", 1000),
                new Book("The Shining", 600),
                new Book("The Power of the Dog", 500),
                new Book("The Border", 700),
                new Book("Along Came a Spider", 300),
                new Book("Pet Cemetery", 400),
                new Book("A Game of Thrones", 900),
                new Book("A Clash of Kings", 1100)
        ));
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
        mongoDBContainer.close();
    }

    @Benchmark
    public void measureFinder() {
        bookRepository.findByTitle("The Border");
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + SimpleQuery.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(4)
                .forks(1)
//                .jvmArgs("-agentpath:/Applications/YourKit-Java-Profiler-2019.8.app/Contents/Resources/bin/mac/libyjpagent.dylib")
                .build();

        new Runner(opt).run();
    }

}
