package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.sql.JoinColumn;

import java.time.LocalTime;

@Embeddable
@JsonSubView(entity = Class.class, operations = { JsonView.Operation.UPDATE, JsonView.Operation.INSERT })
public class TeacherScheduleSubView {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @MappedProperty(value = "id")
    private Long classID;

    private String name;

    @JoinColumn(name = "id", referencedColumnName = "class_id")
    @JsonProperty("class")
    @Relation(Relation.Kind.MANY_TO_ONE)
    private TeacherStudentSubView clazz;

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

    public TeacherStudentSubView getClazz() { return clazz; }

    public void setClazz(TeacherStudentSubView clazz) { this.clazz = clazz; }
}
