package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.MappedProperty;

@Embeddable
@JsonSubView(entity = Teacher.class)
public class TeacherView {

    @Id
    @MappedProperty(value = "id")
    private Long teachID;

    @MappedProperty(value = "name")
    private String teacher;

    public Long getTeachID() {
        return teachID;
    }

    public void setTeachID(Long teachID) {
        this.teachID = teachID;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }
}
