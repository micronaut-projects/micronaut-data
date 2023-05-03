package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinTable;

import java.util.Collections;
import java.util.List;

@MappedEntity("TBL_STUDENT")
public class Student {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;

    private Double averageGrade;

    @JoinTable(name = "TBL_STUDENT_CLASSES")
    @Relation(Relation.Kind.MANY_TO_MANY)
    private List<Class> classes;

    public Student(String name, Double averageGrade) {
        this(null, name, averageGrade, Collections.emptyList());
    }

    public Student(Long id, String name, Double averageGrade, List<Class> classes) {
        this.id = id;
        this.name = name;
        this.averageGrade = averageGrade;
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

    public Double getAverageGrade() {
        return averageGrade;
    }

    public void setAverageGrade(Double averageGrade) {
        this.averageGrade = averageGrade;
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
