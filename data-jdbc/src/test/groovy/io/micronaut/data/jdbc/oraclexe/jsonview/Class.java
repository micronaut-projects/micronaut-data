package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

@MappedEntity("TBL_CLASS")
public class Class {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    @NotNull
    private String name;
    @NotNull
    private String room;
    @NotNull
    private LocalTime time;
    @NotNull
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Teacher teacher;

    public Class(String name, String room, LocalTime time, @Nullable Teacher teacher) {
        this(null, name, room, time, teacher);
    }

    public Class(Long id, String name, String room, LocalTime time, @Nullable Teacher teacher) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.time = time;
        this.teacher = teacher;
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

    public void setName(String name) {
        this.name = name;
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

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }
}
