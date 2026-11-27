package example;

import io.micronaut.context.BeanContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guide shows the Cypher that finders compile to in hand-written blocks. This test reads those
 * blocks out of the guide pages and compares them with the queries compiled for the repository of
 * this example, so that the documentation cannot drift from the generated text.
 */
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentedQueriesTest extends AbstractNeo4jTest {

    private static final Pattern CYPHER_BLOCK = Pattern.compile("\\[source,cypher\\]\\s*\\n----\\n(.*?)\\n----", Pattern.DOTALL);

    /**
     * The repository method behind each Cypher block of a page, in the order the blocks appear.
     */
    private static final Map<String, List<String>> DOCUMENTED = Map.of(
        "graphRepositories.adoc", List.of("findByReleasedGreaterThanOrderByReleased"),
        "graphTraversal.adoc", List.of("findByRolesPersonName", "findByTitle", "findByReleased")
    );

    @Inject
    BeanContext beanContext;

    @Test
    void theCypherShownInTheGuideIsTheCypherThatIsCompiled() throws IOException {
        Path guide = repositoryRoot().resolve("src/main/docs/guide/graph");
        BeanDefinition<MovieRepository> definition = beanContext.getBeanDefinition(MovieRepository.class);
        for (Map.Entry<String, List<String>> page : DOCUMENTED.entrySet()) {
            List<String> blocks = cypherBlocks(Files.readString(guide.resolve(page.getKey())));
            assertEquals(page.getValue().size(), blocks.size(), "Cypher blocks in " + page.getKey());
            for (int i = 0; i < blocks.size(); i++) {
                String method = page.getValue().get(i);
                assertEquals(compiledQuery(definition, method), blocks.get(i), page.getKey() + " block " + (i + 1) + " for " + method);
            }
        }
    }

    private static String compiledQuery(BeanDefinition<MovieRepository> definition, String method) {
        ExecutableMethod<MovieRepository, ?> executable = definition.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals(method))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No repository method " + method));
        return executable.getAnnotationMetadata().stringValue(Query.class).orElseThrow();
    }

    private static List<String> cypherBlocks(String page) {
        List<String> blocks = new ArrayList<>();
        Matcher matcher = CYPHER_BLOCK.matcher(page);
        while (matcher.find()) {
            // The guide breaks a query over several lines for readability; the compiled text is one line
            blocks.add(matcher.group(1).replaceAll("\\s*\\n\\s*", " ").trim());
        }
        return blocks;
    }

    private static Path repositoryRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("settings.gradle"))) {
            directory = directory.getParent();
        }
        assertTrue(directory != null, "The repository root was not found above " + Path.of("").toAbsolutePath());
        return directory;
    }
}
