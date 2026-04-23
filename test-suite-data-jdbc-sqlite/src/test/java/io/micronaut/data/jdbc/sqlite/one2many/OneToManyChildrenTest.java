package io.micronaut.data.jdbc.sqlite.one2many;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.JavaSQLiteDBProperties;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.one2many")
class OneToManyChildrenTest {

    @Inject
    ParentRepository parentRepository;

    @Test
    void testOneToManyHierarchy() {
        List<Child> children = new ArrayList<>();
        Parent parent = new Parent();
        parent.setName("parent");
        parent.setChildren(children);
        children.add(child("A", parent));
        children.add(child("B", parent));
        children.add(child("C", parent));

        parentRepository.save(parent);

        assertNotNull(parent.getId());
        assertNotNull(parent.getChildren());
        assertTrue(parent.getChildren().size() == 3);
        parent.getChildren().forEach(this::assertChild);

        parent = parentRepository.findById(parent.getId()).orElseThrow();
        assertNotNull(parent.getId());
        assertNotNull(parent.getChildren());
        assertTrue(parent.getChildren().size() == 3);
        parent.getChildren().forEach(this::assertChild);

        parent.getChildren().forEach(child -> child.setName(child.getName() + " mod!"));
        parentRepository.update(parent);
        parent = parentRepository.findById(parent.getId()).orElseThrow();

        for (Child child : parent.getChildren()) {
            assertChild(child);
            assertTrue(child.getName().endsWith(" mod!"));
        }
    }

    private Child child(String name, Parent parent) {
        Child child = new Child();
        child.setName(name);
        child.setParent(parent);
        return child;
    }

    private void assertChild(Child child) {
        assertNotNull(child.getId());
        assertNotNull(child.getParent());
        assertNotNull(child.getName());
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface ParentRepository extends CrudRepository<Parent, Long> {

    @Join(value = "children", type = Join.Type.FETCH)
    @Override
    Optional<Parent> findById(Long id);
}

@MappedEntity("x_product")
class Parent {
    private String name;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = Relation.Cascade.ALL)
    private List<Child> children;
    @Id
    @GeneratedValue
    private Long id;

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    List<Child> getChildren() {
        return children;
    }

    void setChildren(List<Child> children) {
        this.children = children;
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }
}

@MappedEntity("x_child")
class Child {
    private String name;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Parent parent;
    @Id
    @GeneratedValue
    private Long id;

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    Parent getParent() {
        return parent;
    }

    void setParent(Parent parent) {
        this.parent = parent;
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }
}
