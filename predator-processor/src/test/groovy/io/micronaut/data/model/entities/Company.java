package io.micronaut.data.model.entities;


import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.net.URL;
import java.time.Instant;
import java.util.Date;

@Entity
public class Company {

    @GeneratedValue
    @Id
    private Long myId;
    @DateCreated
    private Date dateCreated;

    @DateUpdated
    private Instant lastUpdated;

    private String name;
    private URL url;

    public Company(String name, URL url) {
        this.name = name;
        this.url = url;
    }

    public Company() {
    }

    public Long getMyId() {
        return myId;
    }

    public void setMyId(Long myId) {
        this.myId = myId;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }
}

