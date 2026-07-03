package io.micronaut.data.jdbc.sqlite.many2many;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.many2many")
class MultiManyToManyJoinTest {

    @Inject
    RefARepository refARepository;

    @Test
    void testManyToManyHierarchy() {
        RefC refC = new RefC();
        refC.setName("TestXyz");
        RefB refB = new RefB();
        refB.setRefC(List.of(refC));
        RefA refA = new RefA();
        refA.setRefB(List.of(refB));

        refARepository.save(refA);
        refA = refARepository.findById(refA.getId()).orElseThrow();

        assertNotNull(refA.getId());
        assertEquals("TestXyz", refA.getRefB().getFirst().getRefC().getFirst().getName());

        List<RefA> list = refARepository.queryAll(Pageable.from(0, 10));
        assertEquals(1, list.size());
        assertEquals("TestXyz", list.getFirst().getRefB().getFirst().getRefC().getFirst().getName());

        Page<RefA> page = refARepository.findAll(Pageable.from(0, 10));
        assertEquals(1, page.getContent().size());
        assertEquals("TestXyz", page.getContent().getFirst().getRefB().getFirst().getRefC().getFirst().getName());
    }
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface RefARepository extends CrudRepository<RefA, Long> {

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    Page<RefA> findAll(Pageable pageable);

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    List<RefA> queryAll(Pageable pageable);

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<RefA> findById(Long id);
}

@MappedEntity("many_ref_a")
class RefA {

    @Id
    @GeneratedValue
    private Long id;

    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    private List<RefB> refB = new ArrayList<>();

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    List<RefB> getRefB() {
        return refB;
    }

    void setRefB(List<RefB> refB) {
        this.refB = refB;
    }
}

@MappedEntity("many_ref_b")
class RefB {

    @Id
    @GeneratedValue
    private Long id;

    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    private List<RefC> refC = new ArrayList<>();

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    List<RefC> getRefC() {
        return refC;
    }

    void setRefC(List<RefC> refC) {
        this.refC = refC;
    }
}

@MappedEntity("many_ref_c")
class RefC {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }
}
