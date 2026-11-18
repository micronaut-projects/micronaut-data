/*
 * Copyright 2017-2026 original authors
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

import example.Author;
import example.AuthorRepository;
import example.Book;
import example.BookRepository;
import example.Novel;
import example.NovelRepository;
import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Measures repository calls that decode and encode documents through the Micronaut Data codecs, against MongoDB in a
 * container.
 *
 * <p>Run with {@code -prof gc} and compare {@code gc.alloc.rate.norm} (bytes per operation). The driver allocations
 * are included but are the same on both sides of a comparison. The data set is fixed: {@value #BOOKS} books,
 * {@value #AUTHORS} authors and {@value #NOVELS_PER_AUTHOR} novels per author.</p>
 */
@State(Scope.Benchmark)
public class QueryPath {

    // The MongoDB image, for example -Dbench.mongo.image=mongo:8 to use one that is already pulled
    static final String IMAGE_PROPERTY = "bench.mongo.image";
    static final String DEFAULT_IMAGE = "mongo:5";
    static final int BOOKS = 100;
    static final int AUTHORS = 10;
    static final int NOVELS_PER_AUTHOR = 2;

    MongoDBContainer mongoDBContainer;
    ApplicationContext applicationContext;
    BookRepository bookRepository;
    AuthorRepository authorRepository;
    NovelRepository novelRepository;
    List<Author> authors;

    @Setup
    public void prepare() {
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse(System.getProperty(IMAGE_PROPERTY, DEFAULT_IMAGE))
            .asCompatibleSubstituteFor("mongo"));
        mongoDBContainer.start();
        applicationContext = ApplicationContext.run(Map.of("mongodb.uri", mongoDBContainer.getReplicaSetUrl()));
        bookRepository = applicationContext.getBean(BookRepository.class);
        authorRepository = applicationContext.getBean(AuthorRepository.class);
        novelRepository = applicationContext.getBean(NovelRepository.class);

        List<Book> books = new ArrayList<>(BOOKS);
        for (int i = 0; i < BOOKS; i++) {
            books.add(new Book("Book " + i, i));
        }
        bookRepository.saveAll(books);

        List<Author> savedAuthors = new ArrayList<>(AUTHORS);
        List<Novel> novels = new ArrayList<>(AUTHORS * NOVELS_PER_AUTHOR);
        for (int i = 0; i < AUTHORS; i++) {
            Author author = authorRepository.save(new Author("Author " + i));
            savedAuthors.add(author);
            for (int j = 0; j < NOVELS_PER_AUTHOR; j++) {
                novels.add(new Novel("Novel " + i + "-" + j, author));
            }
        }
        novelRepository.saveAll(novels);
        authors = List.copyOf(savedAuthors);
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
        mongoDBContainer.close();
    }

    /**
     * 20 documents with a generated String id decoded to entities.
     */
    @Benchmark
    public List<Book> findWithGeneratedIds() {
        return bookRepository.findByPagesBetween(10, 29);
    }

    /**
     * {@value #AUTHORS} times {@value #NOVELS_PER_AUTHOR} documents decoded with their to-one relation.
     */
    @Benchmark
    public List<Novel> findWithToOneRelation() {
        return novelRepository.findByTitleStartsWith("Novel");
    }

    /**
     * {@value #AUTHORS} entities with a to-many relation encoded and replaced.
     */
    @Benchmark
    public List<Author> updateWithToManyRelation() {
        return authorRepository.updateAll(authors);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(".*" + QueryPath.class.getSimpleName() + ".*")
            .warmupIterations(3)
            .measurementIterations(4)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
