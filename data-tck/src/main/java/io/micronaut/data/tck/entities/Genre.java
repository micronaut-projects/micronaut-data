package io.micronaut.data.tck.entities;

import javax.persistence.Entity;

@Entity
public class Genre extends GenericEntity<Long> {

    private String genreName;

    public String getGenreName() {
        return genreName;
    }

    public void setGenreName(String genreName) {
        this.genreName = genreName;
    }
}
