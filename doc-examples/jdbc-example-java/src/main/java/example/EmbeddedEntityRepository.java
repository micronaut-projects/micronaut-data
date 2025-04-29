package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public interface EmbeddedEntityRepository extends GenericRepository<EmbeddedEntity, EmbeddedEntity.PrimaryKey> {

    EmbeddedEntity save(EmbeddedEntity entity);

    List<EmbeddedEntity> findAll();
}
