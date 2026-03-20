package io.micronaut.data.tck.jdbc.entities.geo;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.model.geo.Point;

@Embeddable
public class Location {

    @Index(columns = "point")
    private Point point;

    @Nullable
    private String description;

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
