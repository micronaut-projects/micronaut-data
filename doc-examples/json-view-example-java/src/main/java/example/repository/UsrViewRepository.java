package example.repository;

import example.domain.view.UsrView;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface UsrViewRepository extends GenericRepository<UsrView, Long> {

    @Query("SELECT uv.* FROM usr_view uv WHERE uv.DATA.usrId=:usrId")
    @QueryResult(type = QueryResult.Type.JSON)
    Optional<UsrView> findUsrViewByUsrId(Long usrId);

    @Query("UPDATE USR_VIEW uv SET uv.data = :data WHERE uv.DATA.usrId = :usrId")
    void updateUsrView(@TypeDef(type = DataType.JSON) UsrView data, Long usrId);

    @Query("INSERT INTO USR_VIEW VALUES (:data)")
    void insertUsrView(@TypeDef(type = DataType.JSON) @Parameter("data") UsrView usrView);

    @Query("DELETE FROM USR_VIEW uv WHERE uv.DATA.usrId = :usrId")
    int deleteUsrView(Long usrId);

    @Query("UPDATE USR_VIEW uv SET uv.DATA = json_transform(DATA, SET '$.bd' = :bd) WHERE uv.DATA.usrId = :usrId")
    void updateUsrViewBd(Double bd, Long usrId);

}
