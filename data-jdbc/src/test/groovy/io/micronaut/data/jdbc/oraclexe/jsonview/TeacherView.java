package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;

@Embeddable
public class TeacherView {

    @Id
    private Long teachID;

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
