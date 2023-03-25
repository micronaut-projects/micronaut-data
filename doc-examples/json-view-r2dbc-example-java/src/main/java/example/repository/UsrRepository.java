package example.repository;

import example.domain.Usr;
import example.domain.view.UsrView;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.annotation.TransformJsonParameter;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface UsrRepository extends PageableRepository<Usr, Long> {

    @Query("SELECT uv.* FROM usr_view uv WHERE uv.DATA.usrId=:usrId")
    @QueryResult(type = QueryResult.Type.JSON)
    Optional<UsrView> findByUsrId(Long usrId);

    @Query("UPDATE USR_VIEW uv SET uv.data = :data WHERE uv.data.usrId = :usrId")
    @TransformJsonParameter
    void update(@TypeDef(type = DataType.JSON) UsrView data, Long usrId);

    @Query("INSERT INTO USR_VIEW VALUES (:data)")
    @TransformJsonParameter
    void insert(@TypeDef(type = DataType.JSON) @Parameter("data") UsrView usrView);

    @Query("DELETE FROM USR_VIEW uv WHERE uv.DATA.usrId = :usrId")
    void deleteByUsrId(Long usrId);
}
