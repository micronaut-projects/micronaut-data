package io.micronaut.data.jdbc.oraclexe.jsonview.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.JsonViewColumn;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinColumn;
import io.micronaut.data.tck.entities.Metadata;

import java.time.LocalDateTime;
import java.util.List;

@JsonView(table = "TBL_STUDENT", permissions = "INSERT UPDATE DELETE")
public class StudentView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    @JsonViewColumn(permissions = "UPDATE")
    private String name;

    @JsonViewColumn(permissions = "UPDATE")
    private Double averageGrade;

    private LocalDateTime startDateTime;

    private boolean active;

    @Relation(Relation.Kind.ONE_TO_MANY)
    @JoinColumn(referencedColumnName = "ID", name = "STUDENT_ID")
    private List<StudentScheduleView> schedule;

    @Relation(Relation.Kind.EMBEDDED)
    @JoinColumn(referencedColumnName = "ADDRESS_ID", name = "ID")
    private AddressView address;

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

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<StudentScheduleView> getSchedule() {
        return schedule;
    }

    public void setSchedule(List<StudentScheduleView> schedule) {
        this.schedule = schedule;
    }

    public AddressView getAddress() {
        return address;
    }

    public void setAddress(AddressView address) {
        this.address = address;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
