package example;

import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@MicronautTest
class CustomEntityRepositorySpec {

    @Inject
    CustomEntityRepository repository;

    @Value("${entity.name}")
    String entityName;

    @Test
    void testSaveAndFind() {
        CustomEntity entity = repository.save(new CustomEntity(null, "Entity1"));
        CustomEntity found = repository.findById(entity.id()).orElse(null);
        Assertions.assertNotNull(found);
        Assertions.assertEquals(entity.name(), found.name());
        Assertions.assertEquals(1, repository.count());
        Assertions.assertFalse(repository.findAll().isEmpty());

        CustomEntity entity2 = repository.save(new CustomEntity(null, "Entity2"));
        CustomEntity entity3 = repository.save(new CustomEntity(null, "Entity3"));

        List<String> allNames = List.of("Entity1", "Entity2", "Entity3");
        List<Long> allIds = List.of(entity.id(), entity2.id(), entity3.id());

        Page<CustomEntity> page = repository.findAll(Pageable.from(0, 2));
        Assertions.assertEquals(2, page.getSize());
        Assertions.assertEquals(3, page.getTotalSize());

        page = repository.findByNameIn(allNames, Pageable.from(0, 2));
        Assertions.assertEquals(2, page.getSize());
        Assertions.assertEquals(2, page.getTotalPages());
        Assertions.assertEquals(3, page.getTotalSize());

        Assertions.assertEquals(3, repository.countByIdIn(allIds));

        page = repository.findByNameIn(List.of("Entity1", "Entity2"),
            Pageable.from(0, 2));
        Assertions.assertEquals(2, page.getSize());
        Assertions.assertEquals(1, page.getTotalPages());
        Assertions.assertEquals(2, page.getTotalSize());

        List<CustomEntity> customEntities = repository.findDataByEnvPropertyValue();
        Assertions.assertEquals(1, customEntities.size());
        Assertions.assertEquals(entityName, customEntities.get(0).name());

        Assertions.assertEquals(1, repository.countDataByEnvPropertyValue());

        customEntities = repository.findDataById(allIds);
        Assertions.assertEquals(3, customEntities.size());
        for (CustomEntity customEntity : customEntities) {
            Assertions.assertEquals(entityName, customEntity.name());
        }

        repository.deleteAll();
    }
}
