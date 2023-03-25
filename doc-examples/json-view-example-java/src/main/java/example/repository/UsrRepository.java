package example.repository;

import example.domain.Usr;
import example.domain.view.UsrView;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.annotation.TransformJsonParameter;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface UsrRepository extends PageableRepository<Usr, Long> {

    @Query("SELECT uv.* FROM usr_view uv WHERE uv.DATA.usrId=:usrId")
    @QueryResult(type = QueryResult.Type.JSON)
    Optional<UsrView> findByUsrId(Long usrId);

    @Query("UPDATE usr_view uv SET uv.data = :data WHERE uv.DATA.usrId = :usrId")
    void updateCustom(String data, Long usrId);

    @Query("UPDATE usr_view uv SET uv.data = :data WHERE uv.DATA.usrId = :usrId")
    void updateBinary(byte[] data, Long usrId);

    @Query("INSERT INTO usr_view VALUES (:data)")
    void insertCustom(String data);

    @Query("INSERT INTO usr_view VALUES (:data)")
    void insertBinary(byte[] data);

    @Query("UPDATE USR_VIEW uv SET uv.data = :data WHERE uv.data.usrId = :usrId")
    @TransformJsonParameter
    void update(@TypeDef(type = DataType.JSON) UsrView data, Long usrId);

    @Query("INSERT INTO USR_VIEW VALUES (:data)")
    @TransformJsonParameter
    void insert(@TypeDef(type = DataType.JSON) @Parameter("data") UsrView usrView);
}
