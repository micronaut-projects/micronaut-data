package example;

import io.micronaut.data.model.geo.MultiPoint;
import io.micronaut.data.model.geo.Point;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class GeographyRepositorySpec {

    @Inject
    GeographyEntityJsonRepository geographyEntityJsonRepository;

    @Inject
    GeographyEntityWktRepository geographyEntityWktRepository;

    @Test
    void testCrudWhenJsonConversionUsed() {
        GeographyEntityJson entity = new GeographyEntityJson();
        entity.setPoint(new Point(1, 2));
        entity.setMultiPoint(new MultiPoint(List.of(new Point(10, 20), new Point(11, 21))));

        GeographyEntityJson savedEntity = geographyEntityJsonRepository.save(entity);
        assertNotNull(savedEntity.getId());

        Optional<GeographyEntityJson> foundEntity = geographyEntityJsonRepository.findById(savedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertEquals(entity.getMultiPoint(), foundEntity.get().getMultiPoint());

        entity.setPoint(new Point(10, 20));
        entity.setMultiPoint(new MultiPoint(List.of(new Point(100, 200), new Point(101, 201))));

        GeographyEntityJson updatedEntity = geographyEntityJsonRepository.update(entity);
        foundEntity = geographyEntityJsonRepository.findById(updatedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertEquals(entity.getMultiPoint(), foundEntity.get().getMultiPoint());

        entity.setMultiPoint(null);
        updatedEntity = geographyEntityJsonRepository.update(entity);
        foundEntity = geographyEntityJsonRepository.findById(updatedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertNull(foundEntity.get().getMultiPoint());
    }

    @Test
    void testCrudWhenWktConversionUsed() {
        GeographyEntityWkt entity = new GeographyEntityWkt();
        entity.setPoint(new Point(1, 2));
        entity.setMultiPoint(new MultiPoint(List.of(new Point(10, 20), new Point(11, 21))));

        GeographyEntityWkt savedEntity = geographyEntityWktRepository.save(entity);
        assertNotNull(savedEntity.getId());

        Optional<GeographyEntityWkt> foundEntity = geographyEntityWktRepository.findById(savedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertEquals(entity.getMultiPoint(), foundEntity.get().getMultiPoint());

        entity.setPoint(new Point(10, 20));
        entity.setMultiPoint(new MultiPoint(List.of(new Point(100, 200), new Point(101, 201))));

        GeographyEntityWkt updatedEntity = geographyEntityWktRepository.update(entity);
        foundEntity = geographyEntityWktRepository.findById(updatedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertEquals(entity.getMultiPoint(), foundEntity.get().getMultiPoint());

        entity.setMultiPoint(null);
        updatedEntity = geographyEntityWktRepository.update(entity);
        foundEntity = geographyEntityWktRepository.findById(updatedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertNull(foundEntity.get().getMultiPoint());
    }
}
