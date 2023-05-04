package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinTable;
import org.testcontainers.shaded.com.github.dockerjava.core.dockerfile.DockerfileStatement;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@MappedEntity("TBL_STUDENT")
public class Student {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;
    private Double averageGrade;

    private LocalDateTime startDateTime;

    private boolean active;

    @JoinTable(name = "TBL_STUDENT_CLASSES")
    @Relation(Relation.Kind.MANY_TO_MANY)
    private List<Class> classes;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Address address;

    public Student(String name, Double averageGrade, LocalDateTime startDateTime, Address address) {
        this(null, name, averageGrade, startDateTime, true, address, Collections.emptyList());
    }

    public Student(Long id, String name, Double averageGrade, LocalDateTime startDateTime, boolean active, Address address, List<Class> classes) {
        this.id = id;
        this.name = name;
        this.averageGrade = averageGrade;
        this.startDateTime = startDateTime;
        this.active = active;
        this.address = address;
        this.classes = classes;
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

    public void setName(String name) {
        this.name = name;
    }

    public List<Class> getClasses() {
        return classes;
    }

    public void setClasses(List<Class> classes) {
        this.classes = classes;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}
