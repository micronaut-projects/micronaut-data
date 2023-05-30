package io.micronaut.data.jdbc.oraclexe.jsonview.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonData;
import io.micronaut.data.annotation.JsonViewColumn;

@Embeddable
@JsonData(table = "TBL_TEACHER", permissions = "UPDATE")
public class TeacherView {

    @Id
    @JsonViewColumn(field = "ID")
    private Long teachID;

    @JsonViewColumn(field = "NAME")
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
