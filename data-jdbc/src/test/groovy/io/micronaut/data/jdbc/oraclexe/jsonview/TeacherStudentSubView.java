package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubView(entity = StudentClass.class)
public class TeacherStudentSubView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private StudentSubView student;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StudentSubView getStudent() { return student; }

    public void setStudent(StudentSubView student) { this.student = student; }
}
