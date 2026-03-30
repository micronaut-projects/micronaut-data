package example.oracle;

import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
@Requires(env="oracle")
class BookClientSpec extends example.BookClientSpec {
}
