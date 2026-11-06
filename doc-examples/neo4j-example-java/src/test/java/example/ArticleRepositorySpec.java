package example;

import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleRepositorySpec extends AbstractNeo4jTest {

    @Inject
    ArticleRepository articleRepository;

    @AfterEach
    void cleanup() {
        articleRepository.deleteAll();
    }

    @Test
    void testOptimisticLocking() {
        // tag::optimisticLocking[]
        articleRepository.save(new Article("graphs", "Graphs", "Ada")); // <1>
        Article first = articleRepository.findById("graphs").orElseThrow();
        Article second = articleRepository.findById("graphs").orElseThrow();

        first.setTitle("Graph databases");
        articleRepository.update(first); // <2>

        second.setTitle("Graphs everywhere");
        assertThrows(OptimisticLockException.class, () -> articleRepository.update(second)); // <3>
        // end::optimisticLocking[]

        Article stored = articleRepository.findById("graphs").orElseThrow();
        assertEquals("Graph databases", stored.getTitle());
        assertEquals(1L, stored.getVersion());
        assertEquals(List.of("graphs"), articleRepository.findByAuthor("Ada").stream().map(Article::getSlug).toList());
    }
}
