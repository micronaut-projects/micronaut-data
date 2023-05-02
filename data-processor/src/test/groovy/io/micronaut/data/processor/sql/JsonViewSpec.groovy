package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.UsrView

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JsonViewSpec extends AbstractDataSpec {

    void "test JsonView repository"() {
        given:
        def repository = buildRepository('test.UsrViewRepository', """
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

        def findByUsrIdQuery = getQuery(repository.getRequiredMethod("findByUsrId", Long))
        def insertUsrViewQuery = getQuery(repository.getRequiredMethod("insertUsrView", UsrView))
        def updateQuery = getQuery(repository.getRequiredMethod("update", UsrView))
        def deleteByUsrIdQuery = getQuery(repository.getRequiredMethod("deleteByUsrId", Long))
        def deleteQuery = getQuery(repository.getRequiredMethod("delete", UsrView))
        def deleteAllQuery = getQuery(repository.getRequiredMethod("deleteAll"))
        def deleteAllIterableQuery = getQuery(repository.getRequiredMethod("deleteAll", Iterable<UsrView>))

        expect:
        findByUsrIdQuery == 'SELECT uv.* FROM "USR_VIEW" uv WHERE (uv.DATA.usrId = ?)'
        insertUsrViewQuery == 'INSERT INTO "USR_VIEW" VALUES (?)'
        updateQuery == 'UPDATE "USR_VIEW" uv SET uv.DATA=? WHERE (uv.DATA.usrId = ?)'
        deleteByUsrIdQuery == 'DELETE  FROM "USR_VIEW"  uv WHERE (uv.DATA.usrId = ?)'
        deleteQuery == 'DELETE  FROM "USR_VIEW"  uv WHERE (uv.DATA.usrId = ?)'
        deleteAllQuery == 'DELETE  FROM "USR_VIEW"  uv'
        deleteAllIterableQuery == 'DELETE  FROM "USR_VIEW"  uv WHERE (uv.DATA.usrId IN (?))'
    }

    void "test JsonView repository not supported property projection"() {
        when:
        buildRepository('test.UsrViewRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.UsrView;
@JdbcRepository(dialect = Dialect.ORACLE)
interface UsrViewRepository extends GenericRepository<UsrView, Long> {

    String findNameByUsrId(Long usrId);
}
""")

        then:
        def thrown = thrown(RuntimeException)
        thrown.message.contains("Property name projection in entity UsrView not supported for JsonView and dialect ORACLE")
    }

    void "test JsonView repository not supported max function projection"() {
        when:
        buildRepository('test.UsrViewRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.UsrView;
@JdbcRepository(dialect = Dialect.ORACLE)
interface UsrViewRepository extends GenericRepository<UsrView, Long> {

    int findMaxAgeByName(String name);
}
""")

        then:
        def thrown = thrown(RuntimeException)
        thrown.message.contains("Function MAX projection for property age in entity UsrView not supported for JsonView and dialect ORACLE")
    }
}
