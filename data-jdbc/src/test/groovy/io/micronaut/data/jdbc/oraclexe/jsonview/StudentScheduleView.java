package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class StudentScheduleView {
    @Id
    private Long id;

    @JsonProperty("class")
    @Relation(Relation.Kind.ONE_TO_ONE)
    private StudentScheduleClassView clazz;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StudentScheduleClassView getClazz() {
        return clazz;
    }

    public void setClazz(StudentScheduleClassView clazz) {
        this.clazz = clazz;
    }
}
