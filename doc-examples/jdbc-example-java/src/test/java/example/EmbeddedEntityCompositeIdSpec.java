package example;

import io.micronaut.context.BeanContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;


@MicronautTest
class EmbeddedEntityCompositeIdSpec {


    @Inject
    private BeanContext beanContext;

    @Test
    void testH2() {
        var saveQuery = getQueryFor(EmbeddedEntityRepository.class, "save", EmbeddedEntity.class);
        assertEquals("INSERT INTO `some_table` (`col`,`some_column`,`other_entity_id`) VALUES (?,?,?)", saveQuery);

        var loadAllQuery = getQueryFor(EmbeddedEntityRepository.class, "findAll");
        assertEquals("SELECT embedded_entity_.`some_column`,embedded_entity_.`other_entity_id`,embedded_entity_.`col` FROM `some_table` embedded_entity_", loadAllQuery);
    }

    private String getQueryFor(Class<? extends GenericRepository<EmbeddedEntity, EmbeddedEntity.PrimaryKey>> repository, String methodName, Class<?>... argumentTypes) {
        var definition = beanContext.getBeanDefinition(repository);
        var method = definition.getRequiredMethod(methodName, argumentTypes);
        return method.stringValue(Query.class).orElse(null);
    }

}
