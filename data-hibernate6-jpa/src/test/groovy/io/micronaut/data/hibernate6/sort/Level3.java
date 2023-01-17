package io.micronaut.data.hibernate6.sort;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

@Entity
public class Level3 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name3;

    @JsonBackReference
    @OneToOne
    private Level2 parent;

    public Level3() {
    }

    public Level3(String name3, Level2 parent) {
        this.name3 = name3;
        this.parent = parent;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName3() {
        return name3;
    }

    public void setName3(String name3) {
        this.name3 = name3;
    }

    public Level2 getParent() {
        return parent;
    }

    public void setParent(Level2 parent) {
        this.parent = parent;
    }
}
