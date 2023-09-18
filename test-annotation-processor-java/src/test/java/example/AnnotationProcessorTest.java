package example;

import io.micronaut.annotation.processing.test.JavaParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

class AnnotationProcessorTest {

    @Test
    void testSlf4jIsNoBeingInitialized() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream old = System.err;
        System.setErr(ps);

        try (JavaParser javaParser = new JavaParser()) {
            javaParser.generate("xyz.Foo", """
                    package xyz;
                    @io.micronaut.data.annotation.Repository
                    interface FooRepository extends io.micronaut.data.repository.CrudRepository<Foo, Long> {
                    }
                    @io.micronaut.data.annotation.MappedEntity
                    class Foo {

                        @io.micronaut.data.annotation.Id
                        private Long id;

                        public Long getId() {
                            return id;
                        }

                        public void setId(Long id) {
                            this.id = id;
                        }

                    }
                    """);
        }

        System.err.flush();
        System.setErr(old);

        Assertions.assertEquals("", baos.toString());
    }

}
