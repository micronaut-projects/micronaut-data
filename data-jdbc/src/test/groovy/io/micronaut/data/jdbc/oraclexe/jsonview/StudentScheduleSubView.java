package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubView(entity = StudentClass.class)
public class StudentScheduleSubView {
    @Id
    private Long id;

    @JsonProperty("class")
    @Relation(Relation.Kind.ONE_TO_ONE)
    private StudentScheduleClassSubView clazz;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StudentScheduleClassSubView getClazz() {
        return clazz;
    }

    public void setClazz(StudentScheduleClassSubView clazz) {
        this.clazz = clazz;
    }
}
