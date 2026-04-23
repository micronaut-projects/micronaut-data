package io.micronaut.data.jdbc.sqlite;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite")
class SQLiteCascadeTest {

    @Inject
    CascadeEntityRepository repository;

    @Test
    void testCascadeSave() {
        CascadeSubEntityA entityA = new CascadeSubEntityA(null, 1, null);
        CascadeSubEntityB entityB = new CascadeSubEntityB(null, 2, null);
        CascadeEntity entity = new CascadeEntity(null, List.of(entityA), List.of(entityB));

        entity = repository.save(entity);
        var opt = repository.findById(entity.id());

        assertTrue(opt.isPresent());
        CascadeEntity loadedEntity = opt.get();
        assertEquals(1, loadedEntity.subEntityAs().size());
        assertEquals(1, loadedEntity.subEntityBs().size());
    }
}
