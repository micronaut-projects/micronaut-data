package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

@MappedEntity("TBL_STUDENT_CLASSES")
public class StudentClass {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Student student;
    @Relation(Relation.Kind.MANY_TO_ONE)
    @MappedProperty("CLASS_ID")
    private Class clazz;

    public StudentClass(Student student, Class clazz) {
        this(null, student, clazz);
    }

    public StudentClass(Long id, Student student, Class clazz) {
        this.id = id;
        this.student = student;
        this.clazz = clazz;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public Class getClazz() {
        return clazz;
    }

    public void setClazz(Class clazz) {
        this.clazz = clazz;
    }
}
