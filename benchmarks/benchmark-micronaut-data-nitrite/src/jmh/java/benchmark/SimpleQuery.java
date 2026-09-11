package benchmark;

import example.Book;
import example.BookRepository;
import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Benchmark for simple query in Nitrite.
 */
@State(Scope.Benchmark)
public class SimpleQuery {

    @Param({"MVSTORE", "IN_MEMORY", "ROCKSDB"})
    public String storageMode;

    ApplicationContext applicationContext;
    BookRepository bookRepository;
    File tempDir;

    @Setup
    public void prepare() throws Exception {
        tempDir = Files.createTempDirectory("nitrite-benchmark").toFile();
        Map<String, Object> props = new HashMap<>();
        props.put("micronaut.nitrite.default.storage-mode", storageMode);
        // Disable query logging to avoid overhead during benchmarks
        props.put("logger.levels.io.micronaut.data.query", "INFO");
        if (!"IN_MEMORY".equals(storageMode)) {
            props.put("micronaut.nitrite.default.db-path", new File(tempDir, "test.db").getAbsolutePath());
        }
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
        if (applicationContext != null) {
            applicationContext.close();
        }
        if (tempDir != null) {
            deleteDirectory(tempDir);
        }
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    @Benchmark
    public void measureFinder() {
        bookRepository.findByTitle("The Border");
    }

    @Benchmark
    public void measureMetadataAwareCoercion() {
        bookRepository.findByPages(700);
    }

    @Benchmark
    public void measureDynamicQuery() {
        bookRepository.findByPagesQuery(700);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + SimpleQuery.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(4)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

}
