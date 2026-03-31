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
public class GeometryRepositorySpec {

    @Inject
    GeometryEntityJsonRepository geometryEntityJsonRepository;

    @Inject
    GeometryEntityWktRepository geometryEntityWktRepository;

    @Test
    void testCrudWhenJsonConversionUsed() {
        GeometryEntityJson entity = new GeometryEntityJson();
        entity.setPoint(new Point(1, 2));
        entity.setMultiPoint(new MultiPoint(List.of(new Point(10, 20), new Point(11, 21))));

        GeometryEntityJson savedEntity = geometryEntityJsonRepository.save(entity);
        assertNotNull(savedEntity.getId());

        Optional<GeometryEntityJson> foundEntity = geometryEntityJsonRepository.findById(savedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertEquals(entity.getMultiPoint(), foundEntity.get().getMultiPoint());

        entity.setPoint(new Point(10, 20));
        entity.setMultiPoint(new MultiPoint(List.of(new Point(100, 200), new Point(101, 201))));

        GeometryEntityJson updatedEntity = geometryEntityJsonRepository.update(entity);
        foundEntity = geometryEntityJsonRepository.findById(updatedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertEquals(entity.getMultiPoint(), foundEntity.get().getMultiPoint());

        entity.setMultiPoint(null);
        updatedEntity = geometryEntityJsonRepository.update(entity);
        foundEntity = geometryEntityJsonRepository.findById(updatedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertNull(foundEntity.get().getMultiPoint());
    }

    @Test
    void testCrudWhenWktConversionUsed() {
        GeometryEntityWkt entity = new GeometryEntityWkt();
        entity.setPoint(new Point(1, 2));
        entity.setMultiPoint(new MultiPoint(List.of(new Point(10, 20), new Point(11, 21))));

        GeometryEntityWkt savedEntity = geometryEntityWktRepository.save(entity);
        assertNotNull(savedEntity.getId());

        Optional<GeometryEntityWkt> foundEntity = geometryEntityWktRepository.findById(savedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertEquals(entity.getMultiPoint(), foundEntity.get().getMultiPoint());

        entity.setPoint(new Point(10, 20));
        entity.setMultiPoint(new MultiPoint(List.of(new Point(100, 200), new Point(101, 201))));

        GeometryEntityWkt updatedEntity = geometryEntityWktRepository.update(entity);
        foundEntity = geometryEntityWktRepository.findById(updatedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertEquals(entity.getMultiPoint(), foundEntity.get().getMultiPoint());

        entity.setMultiPoint(null);
        updatedEntity = geometryEntityWktRepository.update(entity);
        foundEntity = geometryEntityWktRepository.findById(updatedEntity.getId());
        assertTrue(foundEntity.isPresent());
        assertEquals(entity.getPoint(), foundEntity.get().getPoint());
        assertNull(foundEntity.get().getMultiPoint());
    }
}
