package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinTable;

import java.util.Collections;
import java.util.List;

@MappedEntity
public class Student {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;
    @JoinTable(name = "STUDENT_CLASSES")
    @Relation(Relation.Kind.MANY_TO_MANY)
    private List<Class> classes;

    public Student(String name) {
        this(null, name, Collections.emptyList());
    }

    public Student(Long id, String name, List<Class> classes) {
        this.id = id;
        this.name = name;
        this.classes = classes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Class> getClasses() {
        return classes;
    }

    public void setClasses(List<Class> classes) {
        this.classes = classes;
    }
}
