package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.*;

import java.time.LocalTime;

@Embeddable
@JsonSubView(entity = Class.class)
public class StudentScheduleClassView {

    @Id
    @MappedProperty(value = "id")
    private Long classID;

    private String name;

    @Relation(Relation.Kind.ONE_TO_ONE)
    private TeacherView teacher;

    private String room;
    private LocalTime time;

    public Long getClassID() {
        return classID;
    }

    public void setClassID(Long classID) {
        this.classID = classID;
    }

    public TeacherView getTeacher() {
        return teacher;
    }

    public void setTeacher(TeacherView teacher) {
        this.teacher = teacher;
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
}
