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

import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Starts an in-process Neo4j server and the Spring application, and saves the movies the benchmarks query.
 */
final class MovieDatabase implements AutoCloseable {

    static final int MOVIES = 100;

    final Neo4j server;
    final ConfigurableApplicationContext applicationContext;
    final MovieRepository movieRepository;

    MovieDatabase() {
        server = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
        // The Neo4j server brings its own SLF4J binding, which Spring Boot logging would reject
        System.setProperty(LoggingSystem.SYSTEM_PROPERTY, LoggingSystem.NONE);
        SpringApplication springApplication = new SpringApplication();
        Properties properties = new Properties();
        properties.setProperty("spring.neo4j.uri", server.boltURI().toString());
        properties.setProperty("spring.neo4j.authentication.username", "neo4j");
        properties.setProperty("spring.neo4j.authentication.password", "neo4j");
        springApplication.setDefaultProperties(properties);
        springApplication.addPrimarySources(Collections.singletonList(Application.class));
        applicationContext = springApplication.run();
        movieRepository = applicationContext.getBean(MovieRepository.class);
        List<Movie> movies = new ArrayList<>(MOVIES);
        for (int i = 0; i < MOVIES; i++) {
            Movie movie = new Movie("Movie " + i, "Tagline of movie " + i, 1990 + i % 30);
            movie.setVotes(1000L + i);
            movies.add(movie);
        }
        movieRepository.saveAll(movies);
    }

    @Override
    public void close() {
        applicationContext.close();
        server.close();
    }
}
