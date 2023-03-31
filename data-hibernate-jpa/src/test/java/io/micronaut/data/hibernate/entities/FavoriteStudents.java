package io.micronaut.data.hibernate.entities;

import io.micronaut.data.tck.entities.Student;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.util.List;
import java.util.UUID;

@Entity
public class FavoriteStudents {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;
    @jakarta.persistence.Version
    private Integer version;

    @OneToOne(cascade = CascadeType.ALL)
    private Student favorite;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Student> students;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Student getFavorite() {
        return favorite;
    }

    public void setFavorite(Student favorite) {
        this.favorite = favorite;
    }

    public List<Student> getStudents() {
        return students;
    }

    public void setStudents(List<Student> students) {
        this.students = students;
    }
}
