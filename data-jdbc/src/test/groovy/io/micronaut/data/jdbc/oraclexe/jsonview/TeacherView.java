package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.JoinColumn;

import java.util.List;

@Embeddable
@JsonView(entity = Teacher.class)
public class TeacherView {

    @Id
    @MappedProperty(value = "id")
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long teachID;

    private String name;

    @JoinColumn(name = "id", referencedColumnName = "teacher_id")
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<TeacherScheduleSubView> schedule;

    public Long getTeachID() {
        return teachID;
    }

    public void setTeachID(Long teachID) {
        this.teachID = teachID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TeacherScheduleSubView> getSchedule() {
        return schedule;
    }

    public void setSchedule(List<TeacherScheduleSubView> schedule) {
        this.schedule = schedule;
    }
}
