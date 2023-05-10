package example.repository;

import example.domain.view.UsrView;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface UsrViewRepository extends CrudRepository<UsrView, Long> {

    void updateBdByUsrId(@Id Long usrId, Double bd);

}
