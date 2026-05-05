package example;

import io.micronaut.data.model.geo.LineString;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.geo.Polygon;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class SchoolRepositorySpec {

    @Inject
    SchoolRepository schoolRepository;

    @Test
    void testFindByLocationGeoWithin() {
        School inside1 = new School("Central High", new Point(10.0, 10.0));
        School inside2 = new School("West Academy", new Point(12.0, 12.0));
        School outside = new School("Far School", new Point(30.0, 30.0));

        schoolRepository.saveAll(List.of(inside1, inside2, outside));

        Polygon city = new Polygon(
            List.of(
                new LineString(
                    List.of(
                        new Point(9.0, 9.0),
                        new Point(9.0, 15.0),
                        new Point(15.0, 15.0),
                        new Point(15.0, 9.0),
                        new Point(9.0, 9.0)
                    )
                )
            )
        );

        List<School> result = schoolRepository.findByLocationGeoWithin(city);

        List<String> names = result.stream()
            .map(School::getName)
            .toList();

        assertEquals(2, names.size());
        assertTrue(names.contains("Central High"));
        assertTrue(names.contains("West Academy"));
        assertFalse(names.contains("Far School"));
    }
}
