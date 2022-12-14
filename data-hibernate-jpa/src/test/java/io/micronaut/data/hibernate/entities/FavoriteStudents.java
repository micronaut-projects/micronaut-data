package io.micronaut.data.hibernate.entities;

import io.micronaut.data.tck.entities.Student;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.util.List;
import java.util.UUID;

@Entity
public class FavoriteStudents {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;
    @javax.persistence.Version
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
