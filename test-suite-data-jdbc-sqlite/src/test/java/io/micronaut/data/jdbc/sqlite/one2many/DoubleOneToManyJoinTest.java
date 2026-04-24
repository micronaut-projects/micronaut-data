package io.micronaut.data.jdbc.sqlite.one2many;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.one2many")
class DoubleOneToManyJoinTest {

    @Inject
    EntityARepository entityARepository;

    @Inject
    EntityBRepository entityBRepository;

    @Inject
    EntityCRepository entityCRepository;

    @Test
    void testOneToManyDoubleJoin() {
        EntityA a = new EntityA();
        a.setName("a1");
        a = entityARepository.save(a);

        EntityC c1 = new EntityC();
        c1.setAId(a.getAId());
        c1.setCProp("c1");
        entityCRepository.save(c1);

        EntityB b1 = new EntityB();
        b1.setAId(a.getAId());
        b1.setBProp("b1");
        b1.setReqProp("c1");
        entityBRepository.save(b1);

        EntityB b2 = new EntityB();
        b2.setAId(a.getAId());
        b2.setBProp("b2");
        entityBRepository.save(b2);

        List<EntityA> aItems = entityARepository.findOrderByAsc(a.getAId());
        assertEquals(1, aItems.size());
        assertEquals(1, aItems.getFirst().getC().size());
        assertEquals(2, aItems.getFirst().getB().size());
        assertEquals(1L, aItems.getFirst().getB().get(0).getBId());
        assertEquals(2L, aItems.getFirst().getB().get(1).getBId());

        aItems = entityARepository.findOrderByAscBDescCDesc(a.getAId());
        assertEquals(1, aItems.size());
        assertEquals(1, aItems.getFirst().getC().size());
        assertEquals(2, aItems.getFirst().getB().size());
        assertEquals(2L, aItems.getFirst().getB().get(0).getBId());
        assertEquals(1L, aItems.getFirst().getB().get(1).getBId());
    }
}

@MappedEntity
class EntityA {
    @Id
    @GeneratedValue
    private Long aId;
    private String name;
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<EntityB> b;
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<EntityC> c;

    Long getAId() {
        return aId;
    }

    void setAId(Long aId) {
        this.aId = aId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    List<EntityB> getB() {
        return b;
    }

    void setB(List<EntityB> b) {
        this.b = b;
    }

    List<EntityC> getC() {
        return c;
    }

    void setC(List<EntityC> c) {
        this.c = c;
    }
}

@MappedEntity
class EntityB {
    @Id
    @GeneratedValue
    private Long bId;
    private Long aId;
    private String bProp;
    @org.jspecify.annotations.Nullable
    private String reqProp;

    Long getBId() {
        return bId;
    }

    void setBId(Long bId) {
        this.bId = bId;
    }

    Long getAId() {
        return aId;
    }

    void setAId(Long aId) {
        this.aId = aId;
    }

    String getBProp() {
        return bProp;
    }

    void setBProp(String bProp) {
        this.bProp = bProp;
    }

    String getReqProp() {
        return reqProp;
    }

    void setReqProp(String reqProp) {
        this.reqProp = reqProp;
    }
}

@MappedEntity
class EntityC {
    @Id
    @GeneratedValue
    private Long cId;
    private Long aId;
    private String cProp;

    Long getCId() {
        return cId;
    }

    void setCId(Long cId) {
        this.cId = cId;
    }

    Long getAId() {
        return aId;
    }

    void setAId(Long aId) {
        this.aId = aId;
    }

    String getCProp() {
        return cProp;
    }

    void setCProp(String cProp) {
        this.cProp = cProp;
    }
}

@JdbcRepository(dialect = Dialect.SQLITE)
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

@JdbcRepository(dialect = Dialect.SQLITE)
abstract class EntityBRepository implements CrudRepository<EntityB, Long> {
}

@JdbcRepository(dialect = Dialect.SQLITE)
abstract class EntityCRepository implements CrudRepository<EntityC, Long> {
}
