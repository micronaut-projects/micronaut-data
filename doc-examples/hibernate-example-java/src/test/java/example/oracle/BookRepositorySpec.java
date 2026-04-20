package example.oracle;

import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
@Requires(env = "oracle")
class BookRepositorySpec extends example.BookRepositorySpec {
    public BookRepositorySpec() {
        super();
    }
}
