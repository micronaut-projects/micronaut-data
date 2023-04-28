package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.JsonViewColumn;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinColumn;

import java.util.List;

@JsonView(table = "STUDENT", permissions = "UPDATE INSERT DELETE")
@MappedEntity(value = "STUDENT_SCHEDULE")
public class StudentView {
    @JsonViewColumn(field = "id")
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long studentId;

    @JsonViewColumn(field = "name", attributes = "UPDATE")
    private String student;

    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<StudentScheduleView> schedule;
    @JsonProperty("_metadata")
    private Metadata metadata;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudent() {
        return student;
    }

    public void setStudent(String student) {
        this.student = student;
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
