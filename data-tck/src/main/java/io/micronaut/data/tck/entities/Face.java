package io.micronaut.data.tck.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;

@Entity
public class Face {

    @GeneratedValue
    @Id
    private Long id;
    private String name;

    public Face(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @OneToOne(mappedBy = "face")
    private Nose nose;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Nose getNose() {
        return nose;
    }

    public void setNose(Nose nose) {
        this.nose = nose;
    }
}
