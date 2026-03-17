package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@Requires(env="oracle")
@DisabledIfEnvironmentVariable(named = "MICRONAUT_ENVIRONMENTS", matches = "h2")
class OracleBookRepositorySpec extends BookRepositorySpec {

    @Test
    @Override
    void testAnnotationMetadata() {
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
