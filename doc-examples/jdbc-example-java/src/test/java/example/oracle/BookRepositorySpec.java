package example.oracle;

import example.BookRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@Requires(env="oracle")
class BookRepositorySpec extends example.BookRepositorySpec {

    @Test
    @Override
    protected void testAnnotationMetadata() {
        String query = beanContext.getBeanDefinition(BookRepository.class) // <1>
            .getRequiredMethod("find", String.class) // <2>
            .getAnnotationMetadata()
            .stringValue(Query.class) // <3>
            .orElse(null);

        assertEquals( // <4>
            """
            SELECT book_."ID",book_."TITLE",book_."PAGES" FROM "BOOK" book_ WHERE (book_."TITLE" = ?)""",
            query
        );

    }
}
