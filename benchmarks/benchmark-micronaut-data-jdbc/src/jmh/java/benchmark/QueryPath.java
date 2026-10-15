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
import example.BookDto;
import example.BookRepository;
import example.Novel;
import example.NovelRepository;
import example.TimestampedBook;
import example.TimestampedBookRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures repository calls through the whole runtime query path (interceptor, stored and prepared query, binding,
 * execution on in-memory H2 and mapping).
 *
 * <p>Run with {@code -prof gc} and compare {@code gc.alloc.rate.norm} (bytes per operation) before and after a
 * change: H2 runs in the same JVM, so its allocations are included, but they are the same on both sides of a
 * comparison. The data set is fixed: {@value #BOOKS} books, {@value #AUTHORS} authors with
 * {@value #NOVELS_PER_AUTHOR} novels each, and {@value #BATCH} books per thread for the batch writes.</p>
 */
@State(Scope.Benchmark)
public class QueryPath {

    static final int BOOKS = 1000;
    static final int AUTHORS = 10;
    static final int NOVELS_PER_AUTHOR = 5;
    static final int BATCH = 100;
    // Books written by the saveAll benchmark have this number of pages, so they can be removed after every call
    static final int NEW_BOOK_PAGES = -1;

    ApplicationContext applicationContext;
    BookRepository bookRepository;
    AuthorRepository authorRepository;
    TimestampedBookRepository timestampedBookRepository;
    List<Long> bookIds;

    @Setup
    public void prepare() {
        applicationContext = ApplicationContext.run();
        bookRepository = applicationContext.getBean(BookRepository.class);
        authorRepository = applicationContext.getBean(AuthorRepository.class);
        timestampedBookRepository = applicationContext.getBean(TimestampedBookRepository.class);
        NovelRepository novelRepository = applicationContext.getBean(NovelRepository.class);

        List<Book> books = new ArrayList<>(BOOKS);
        for (int i = 0; i < BOOKS; i++) {
            books.add(new Book("Book " + i, i));
        }
        bookIds = new ArrayList<>(BOOKS);
        for (Book book : bookRepository.saveAll(books)) {
            bookIds.add(book.getId());
        }
        bookIds = List.copyOf(bookIds);

        List<Novel> novels = new ArrayList<>(AUTHORS * NOVELS_PER_AUTHOR);
        for (int i = 0; i < AUTHORS; i++) {
            Author author = authorRepository.save(new Author("Author " + i));
            for (int j = 0; j < NOVELS_PER_AUTHOR; j++) {
                novels.add(new Novel("Novel " + i + "-" + j, author));
            }
        }
        novelRepository.saveAll(novels);
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
    }

    /**
     * The state of one benchmark thread: its own batch of books and its own pageable.
     */
    @State(Scope.Thread)
    public static class ThreadState {

        private static final AtomicInteger THREADS = new AtomicInteger();

        List<Book> batch;
        List<TimestampedBook> timestampedBatch;
        Pageable pageable;

        @Setup
        public void prepare(QueryPath queryPath) {
            List<Book> books = new ArrayList<>(BATCH);
            String prefix = "Batch " + THREADS.incrementAndGet() + " ";
            for (int i = 0; i < BATCH; i++) {
                books.add(new Book(prefix + i, BOOKS + i));
            }
            batch = List.copyOf(queryPath.bookRepository.saveAll(books));
            List<TimestampedBook> timestampedBooks = new ArrayList<>(BATCH);
            for (int i = 0; i < BATCH; i++) {
                timestampedBooks.add(new TimestampedBook(prefix + i, i));
            }
            timestampedBatch = List.copyOf(queryPath.timestampedBookRepository.saveAll(timestampedBooks));
            pageable = Pageable.from(1, 10, Sort.of(Sort.Order.desc("pages"), Sort.Order.asc("title")));
        }
    }

    /**
     * The state of the saveAll benchmark: removes the books inserted by a call after that call, so that every call
     * inserts into a table of the same size. The removal runs outside the measured time on a plain JDBC connection to
     * the same database, so that it is the same work on both sides of a comparison.
     */
    @State(Scope.Thread)
    public static class NewBooks {

        Connection connection;
        PreparedStatement delete;

        @Setup
        public void prepare(QueryPath queryPath) throws SQLException {
            String url = queryPath.applicationContext.getRequiredProperty("datasources.default.url", String.class);
            connection = DriverManager.getConnection(url, "sa", "");
            delete = connection.prepareStatement("DELETE FROM book WHERE pages = " + NEW_BOOK_PAGES);
        }

        @TearDown(Level.Invocation)
        public void removeNewBooks() throws SQLException {
            delete.executeUpdate();
        }

        @TearDown
        public void close() throws SQLException {
            delete.close();
            connection.close();
        }
    }

    /**
     * A simple finder with one parameter.
     */
    @Benchmark
    public Book findByTitle() {
        return bookRepository.findByTitle("Book 500");
    }

    /**
     * An IN list of {@value #BOOKS} ids.
     */
    @Benchmark
    public List<Book> inList() {
        return bookRepository.findByIdIn(bookIds);
    }

    /**
     * A page of 10 rows sorted by two properties, with its count query.
     */
    @Benchmark
    public Page<Book> pagedAndSorted(ThreadState state) {
        return bookRepository.findByPagesGreaterThan(950, state.pageable);
    }

    /**
     * 20 rows mapped to a DTO.
     */
    @Benchmark
    public List<BookDto> dtoProjection() {
        return bookRepository.findByPagesBetween(10, 29);
    }

    /**
     * {@value #AUTHORS} authors fetched with their {@value #NOVELS_PER_AUTHOR} novels each.
     */
    @Benchmark
    public List<Author> joinFetch() {
        return authorRepository.findByNameStartsWith("Author");
    }

    /**
     * A batch update of {@value #BATCH} books.
     */
    @Benchmark
    public List<Book> updateAll(ThreadState state) {
        return bookRepository.updateAll(state.batch);
    }

    /**
     * A batch update of {@value #BATCH} books whose update timestamp is set by the auto-populate listener.
     */
    @Benchmark
    public List<TimestampedBook> updateAllTimestamped(ThreadState state) {
        return timestampedBookRepository.updateAll(state.timestampedBatch);
    }

    /**
     * A batch insert of {@value #BATCH} new books into a table of fixed size, the books are removed after every call.
     */
    @Benchmark
    public List<Book> saveAll(NewBooks newBooks) {
        List<Book> books = new ArrayList<>(BATCH);
        for (int i = 0; i < BATCH; i++) {
            books.add(new Book("New", NEW_BOOK_PAGES));
        }
        return bookRepository.saveAll(books);
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
