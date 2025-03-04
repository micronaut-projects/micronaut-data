package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface MyMainEntityRepository extends GenericRepository<MyMainEntity, Long> {
    Optional<MyMainEntity> findById(Long id);

    MyMainEntity save(MyMainEntity entity);

    MyMainEntity update(MyMainEntity entity);

    void deleteAll();

    void deleteByExample(String ex);
}
