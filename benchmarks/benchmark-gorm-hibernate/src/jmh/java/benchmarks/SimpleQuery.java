package benchmarks;

import example.Book;
import example.BookRepository;
import groovy.lang.Closure;
import org.grails.orm.hibernate.HibernateDatastore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;

@State(Scope.Benchmark)
public class SimpleQuery {

    HibernateDatastore datastore;
    BookRepository bookRepository;

    @Setup
    public void prepare() {
        this.datastore = new HibernateDatastore(Book.class);
        this.bookRepository = datastore.getService(BookRepository.class);

        Book.withTransaction(new Closure<Object>(this) {
            public Object doCall(Object o) {
                Book.saveAll(Arrays.asList(
                        new Book("The Stand", 1000),
                        new Book("The Shining", 600),
                        new Book("The Power of the Dog", 500),
                        new Book("The Border", 700),
                        new Book("Along Came a Spider", 300),
                        new Book("Pet Cemetery", 400),
                        new Book("A Game of Thrones", 900),
                        new Book("A Clash of Kings", 1100)
                ));
                return null;
            }
        });

    }

    @TearDown
    public void cleanup() {
        datastore.close();
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
//                .jvmArgs("-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib")
                .build();

        new Runner(opt).run();
    }

}
