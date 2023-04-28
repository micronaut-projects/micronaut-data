package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.UsrView

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JsonViewSpec extends AbstractDataSpec {

    void "test JsonView repository"() {
        given:
        def repository = buildRepository('test.UsrViewRepository', """
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.UsrView;
import java.util.Optional;
@JdbcRepository(dialect = Dialect.ORACLE)
interface UsrViewRepository extends GenericRepository<UsrView, Long> {
    @QueryResult(type = QueryResult.Type.JSON)
    Optional<UsrView> findByUsrId(Long usrId);
    void insertUsrView(@TypeDef(type = DataType.JSON) UsrView usrView);
    Long updateUsrView(@Id Long usrId, String name);
    void update(@TypeDef(type = DataType.JSON) UsrView data);
    int deleteByUsrId(Long usrId);
    int delete(UsrView usrView);
    void deleteAll(Iterable<UsrView> entities);
    void deleteAll();
}
""")

        def findByUsrIdMethod = repository.getRequiredMethod("findByUsrId", Long)
        def insertUsrViewMethod = repository.getRequiredMethod("insertUsrView", UsrView)
        def updateMethod = repository.getRequiredMethod("update", UsrView)
        def deleteByUsrIdMethod = repository.getRequiredMethod("deleteByUsrId", Long)
        def deleteMethod = repository.getRequiredMethod("delete", UsrView)
        def deleteAllIterableMethod = repository.getRequiredMethod("deleteAll", Iterable<UsrView>)
        def deleteAllMethod = repository.getRequiredMethod("deleteAll")
        def findByUsrIdQuery = getQuery(findByUsrIdMethod)
        def insertUsrViewQuery = getQuery(insertUsrViewMethod)
        def updateQuery = getQuery(updateMethod)
        def deleteByUsrIdQuery = getQuery(deleteByUsrIdMethod)
        def deleteQuery = getQuery(deleteMethod)
        def deleteAllQuery = getQuery(deleteAllMethod)
        def deleteAllIterableQuery = getQuery(deleteAllIterableMethod)

        expect:
        findByUsrIdQuery == 'SELECT uv.* FROM "USR_VIEW" uv WHERE (uv.DATA.usrId = ?)'
        insertUsrViewQuery == 'INSERT INTO "USR_VIEW" VALUES (?)'
        updateQuery == 'UPDATE "USR_VIEW" uv SET uv.DATA=? WHERE (uv.DATA.usrId = ?)'
        deleteByUsrIdQuery == 'DELETE  FROM "USR_VIEW"  uv WHERE (uv.DATA.usrId = ?)'
        deleteQuery == 'DELETE  FROM "USR_VIEW"  uv WHERE (uv.DATA.usrId = ?)'
        deleteAllQuery == 'DELETE  FROM "USR_VIEW"  uv'
        deleteAllIterableQuery == 'DELETE  FROM "USR_VIEW"  uv WHERE (uv.DATA.usrId IN (?))'
    }
}
