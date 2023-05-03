package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinColumn;

import java.util.List;

@JsonView
@MappedEntity(value = "STUDENT_VIEW")
public class StudentView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    private String name;

    private Double averageGrade;

    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<StudentScheduleView> schedule;
    @JsonProperty("_metadata")
    private Metadata metadata;

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

    public Double getAverageGrade() {
        return averageGrade;
    }

    public void setAverageGrade(Double averageGrade) {
        this.averageGrade = averageGrade;
    }

    public List<StudentScheduleView> getSchedule() {
        return schedule;
    }

    public void setSchedule(List<StudentScheduleView> schedule) {
        this.schedule = schedule;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
