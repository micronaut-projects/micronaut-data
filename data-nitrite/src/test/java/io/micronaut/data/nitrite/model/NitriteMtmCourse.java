package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;

@MappedEntity("nitrite_mtm_course")
public class NitriteMtmCourse {
    @Id
    @GeneratedValue
    private String id;
    private String title;

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
    private List<NitriteMtmStudent> students = new ArrayList<>();

    public NitriteMtmCourse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<NitriteMtmStudent> getStudents() {
        return students;
    }

    public void setStudents(List<NitriteMtmStudent> students) {
        this.students = students;
    }
}
