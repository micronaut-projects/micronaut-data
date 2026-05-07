package io.micronaut.data.tck.jdbc.entities.geo;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.geo.Point;

@MappedEntity
public class Hotel {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @Index(columns = "location")
    private Point location;

    public Hotel() {
    }

    public Hotel(String name, Point location) {
        this.name = name;
        this.location = location;
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

    public void setName(String name) {
        this.name = name;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }
}
