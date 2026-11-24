package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.sql.JoinColumn;

import java.time.LocalTime;
import java.util.List;

@Embeddable
@JsonSubView(entity = Class.class, operations = { JsonView.Operation.UPDATE, JsonView.Operation.INSERT })
public class TeacherScheduleSubView {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @MappedProperty(value = "id")
    private Long classID;

    private String name;

    @JoinColumn(name = "id", referencedColumnName = "class_id")
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<TeacherStudentSubView> students;

    private String room;
    private LocalTime time;

    public Long getClassID() {
        return classID;
    }

    public void setClassID(Long classID) {
        this.classID = classID;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TeacherStudentSubView> getStudents() {
        return students;
    }

    public void setStudents(List<TeacherStudentSubView> students) {
        this.students = students;
    }
}
