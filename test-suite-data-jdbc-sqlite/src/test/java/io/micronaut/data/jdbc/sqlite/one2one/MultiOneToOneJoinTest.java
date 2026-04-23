package io.micronaut.data.jdbc.sqlite.one2one;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.JavaSQLiteDBProperties;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@JavaSQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.one2one")
class MultiOneToOneJoinTest {

    @Inject
    RefARepository refARepository;

    @Test
    void testOneToOneHierarchy() {
        RefC refC = new RefC();
        refC.setName("TestXyz");
        RefB refB = new RefB();
        refB.setRefC(refC);
        RefA refA = new RefA();
        refA.setRefB(refB);

        refARepository.save(refA);
        refA = refARepository.findById(refA.getId()).orElseThrow();

        assertNotNull(refA.getId());
        assertEquals("TestXyz", refA.getRefB().getRefC().getName());

        List<RefA> list = refARepository.queryAll(Pageable.from(0, 10));
        assertEquals(1, list.size());
        assertEquals("TestXyz", list.getFirst().getRefB().getRefC().getName());

        Page<RefA> page = refARepository.findAll(Pageable.from(0, 10));
        assertEquals(1, page.getContent().size());
        assertEquals("TestXyz", page.getContent().getFirst().getRefB().getRefC().getName());

        refARepository.update(refA);
        refA = refARepository.findById(refA.getId()).orElseThrow();
        assertNotNull(refA.getId());
        assertEquals("TestXyz", refA.getRefB().getRefC().getName());
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
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

@MappedEntity("one_a")
class RefA {

    @Id
    @GeneratedValue
    private Long id;

    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    private RefB refB;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    RefB getRefB() {
        return refB;
    }

    void setRefB(RefB refB) {
        this.refB = refB;
    }
}

@MappedEntity("one_b")
class RefB {

    @Id
    @GeneratedValue
    private Long id;

    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    private RefC refC;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    RefC getRefC() {
        return refC;
    }

    void setRefC(RefC refC) {
        this.refC = refC;
    }
}

@MappedEntity("one_c")
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
