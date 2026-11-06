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
package example;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Runs finders of Spring Data Neo4j against an in-process Neo4j server. The Micronaut Data counterpart is the
 * {@code SimpleQuery} benchmark of benchmark-micronaut-data-graph, over the same data.
 */
@State(Scope.Benchmark)
public class SimpleQuery {

    private MovieDatabase database;

    @Setup
    public void prepare() {
        database = new MovieDatabase();
    }

    @TearDown
    public void cleanup() {
        database.close();
    }

    @Benchmark
    public void findByTitle(Blackhole blackhole) {
        blackhole.consume(database.movieRepository.findByTitle("Movie 42"));
    }

    @Benchmark
    public void findByReleasedGreaterThan(Blackhole blackhole) {
        // A third of the movies
        blackhole.consume(database.movieRepository.findByReleasedGreaterThan(2009));
    }
}
