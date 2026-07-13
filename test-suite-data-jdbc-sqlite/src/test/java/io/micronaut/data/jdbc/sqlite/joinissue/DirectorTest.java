package io.micronaut.data.jdbc.sqlite.joinissue;

import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties
class DirectorTest {

    @Inject
    DirectorRepository directorRepository;

    @Test
    void test() {
        List<Director> directorList = List.of(
            new Director("John Jones", Set.of(new Movie("Random Movie"))),
            new Director("Ann Jones", Set.of(new Movie("Super Hero Movie"), new Movie("Anther Movie with Heroes")))
        );

        directorRepository.saveAll(directorList);

        Director director = directorRepository.queryByName("John Jones").orElse(null);
        assertNotNull(director);
        assertEquals("John Jones", director.getName());
        assertEquals(1, director.getMovies().size());

        List<Director> list = directorRepository.queryByNameContains("n Jones");
        assertEquals(2, list.size());
        assertEquals("John Jones", list.get(0).getName());
        assertEquals(1, list.get(0).getMovies().size());
        assertEquals("Ann Jones", list.get(1).getName());
        assertEquals(2, list.get(1).getMovies().size());

        director = directorRepository.findByNameContains("n Jones").orElse(null);
        assertNotNull(director);
        assertEquals("John Jones", director.getName());
        assertEquals(1, director.getMovies().size());
    }
}
