package io.micronaut.data.tck.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
@javax.persistence.Entity
public class Genre {

    @Id
    @GeneratedValue
    @javax.persistence.Id
    @javax.persistence.GeneratedValue
    private Long id;

    private String genreName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGenreName() {
        return genreName;
    }

    public void setGenreName(String genreName) {
        this.genreName = genreName;
    }
}
