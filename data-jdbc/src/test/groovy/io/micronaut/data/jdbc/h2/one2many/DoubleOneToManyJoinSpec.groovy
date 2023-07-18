package io.micronaut.data.jdbc.h2.one2many

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.annotation.Nullable
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Test for entity that has two one to many collections without mappedBy and joined using custom query.
 */
@MicronautTest
@H2DBProperties
class DoubleOneToManyJoinSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    EntityARepository entityARepository = applicationContext.getBean(EntityARepository)

    @Shared
    @Inject
    EntityBRepository entityBRepository = applicationContext.getBean(EntityBRepository)

    @Shared
    @Inject
    EntityCRepository entityCRepository = applicationContext.getBean(EntityCRepository)

    void 'test one-to-many double join'() {
        given:
        def a = new EntityA()
        a.name = "a1"
        a = entityARepository.save(a)

        def c1 = new EntityC()
        c1.aId = a.aId
        c1.cProp = "c1"
        entityCRepository.save(c1)

        def b1 = new EntityB()
        b1.aId = a.aId
        b1.bProp = "b1"
        b1.reqProp = "c1"
        entityBRepository.save(b1)

        def b2 = new EntityB()
        b2.aId = a.aId
        b2.bProp = "b2"
        entityBRepository.save(b2)
        when:
        def aItems = entityARepository.findOrderByAsc(a.aId)
        then:
        aItems.size() == 1
        aItems[0].c.size() == 1
        aItems[0].b.size() == 2
        aItems[0].b[0].bId == 1
        aItems[0].b[1].bId == 2
        when:
        aItems = entityARepository.findOrderByAscBDescCDesc(a.aId)
        then:
        aItems.size() == 1
        aItems[0].c.size() == 1
        aItems[0].b.size() == 2
        // b collection ordered descending
        aItems[0].b[0].bId == 2
        aItems[0].b[1].bId == 1
    }

}

@MappedEntity
class EntityA {
    @Id
    @GeneratedValue
    Long aId

    String name;

    @Relation(Relation.Kind.ONE_TO_MANY)
    @Nullable
    List<EntityB> b

    @Relation(Relation.Kind.ONE_TO_MANY)
    @Nullable
    List<EntityC> c

    Long getAId() {
        return aId
    }

    void setAId(Long aId) {
        this.aId = aId
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    @Nullable
    List<EntityB> getB() {
        return b
    }

    void setB(@Nullable List<EntityB> b) {
        this.b = b
    }

    @Nullable
    List<EntityC> getC() {
        return c
    }

    void setC(@Nullable List<EntityC> c) {
        this.c = c
    }
}
@MappedEntity
class EntityB {
    @Id
    @GeneratedValue
    Long bId
    Long aId
    String bProp
    @io.micronaut.core.annotation.Nullable
    String reqProp

    Long getBId() {
        return bId
    }

    void setBId(Long bId) {
        this.bId = bId
    }

    Long getAId() {
        return aId
    }

    void setAId(Long aId) {
        this.aId = aId
    }

    String getBProp() {
        return bProp
    }

    void setBProp(String bProp) {
        this.bProp = bProp
    }

    String getReqProp() {
        return reqProp
    }

    void setReqProp(String reqProp) {
        this.reqProp = reqProp
    }
}
@MappedEntity
class EntityC {
    @Id
    @GeneratedValue
    Long cId
    Long aId
    String cProp

    Long getCId() {
        return cId
    }

    void setCId(Long cId) {
        this.cId = cId
    }

    Long getAId() {
        return aId
    }

    void setAId(Long aId) {
        this.aId = aId
    }

    String getCProp() {
        return cProp
    }

    void setCProp(String cProp) {
        this.cProp = cProp
    }
}

@JdbcRepository(dialect = Dialect.H2)
abstract class EntityARepository implements CrudRepository<EntityA, Long> {

    @Query("""
    SELECT a.*,
           b.b_id b_b_id, b.a_id b_a_id, b.b_prop b_b_prop, b.req_prop b_req_prop,
           c.c_id c_c_id, c.a_id c_a_id, c.c_prop c_c_prop
    FROM entity_a a
    LEFT JOIN entity_b b ON a.a_id=b.a_id
    LEFT JOIN entity_c c ON a.a_id=c.a_id AND b.req_prop=c.c_prop
    WHERE a.a_id=:aId
    ORDER BY a.a_id,b.b_id,c.c_id
  """)
    @Join(value = "b", alias = "b_")
    @Join(value = "c", alias = "c_")
    abstract List<EntityA> findOrderByAsc(Long aId);

    @Query("""
    SELECT a.*,
           b.b_id b_b_id, b.a_id b_a_id, b.b_prop b_b_prop, b.req_prop b_req_prop,
           c.c_id c_c_id, c.a_id c_a_id, c.c_prop c_c_prop
    FROM entity_a a
    LEFT JOIN entity_b b ON a.a_id=b.a_id
    LEFT JOIN entity_c c ON a.a_id=c.a_id AND b.req_prop=c.c_prop
    WHERE a.a_id=:aId
    ORDER BY a.a_id, b.b_id DESC,c.c_id DESC
  """)
    @Join(value = "b", alias = "b_")
    @Join(value = "c", alias = "c_")
    abstract List<EntityA> findOrderByAscBDescCDesc(Long aId);
}
@JdbcRepository(dialect = Dialect.H2)
abstract class EntityBRepository implements CrudRepository<EntityB, Long> {
}
@JdbcRepository(dialect = Dialect.H2)
abstract class EntityCRepository implements CrudRepository<EntityC, Long> {
}
