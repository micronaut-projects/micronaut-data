package io.micronaut.data.hibernate.sort;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

@Entity
public class Level2 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name2;

    @JsonBackReference
    @OneToOne
    private Level1 parent;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "parent")
    private Level3 level3;

    public Level2() {
    }

    public Level2(String name2, Level1 parent) {
        this.name2 = name2;
        this.parent = parent;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName2() {
        return name2;
    }

    public void setName2(String name2) {
        this.name2 = name2;
    }

    public Level1 getParent() {
        return parent;
    }

    public void setParent(Level1 parent) {
        this.parent = parent;
    }

    public Level3 getLevel3() {
        return level3;
    }

    public void setLevel3(Level3 child) {
        this.level3 = child;
    }
}
