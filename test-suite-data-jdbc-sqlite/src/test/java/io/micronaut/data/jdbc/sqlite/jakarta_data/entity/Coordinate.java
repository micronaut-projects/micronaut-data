package io.micronaut.data.jdbc.sqlite.jakarta_data.entity;

import io.micronaut.core.annotation.Introspected;

import java.util.UUID;

/**
 * This entity includes some field types that aren't covered elsewhere in the TCK.
 */
@Introspected(accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD}, visibility = Introspected.Visibility.ANY)
@jakarta.persistence.Entity
public class Coordinate {
    @jakarta.persistence.Id
    public UUID id;

    public double x;

    public float y;

    public static Coordinate of(String id, double x, float y) {
        Coordinate c = new Coordinate();
        c.id = UUID.nameUUIDFromBytes(id.getBytes());
        c.x = x;
        c.y = y;
        return c;
    }

    @Override
    public String toString() {
        return "Coordinate@" + Integer.toHexString(hashCode()) + "(" + x + "," + y + ")" + ":" + id;
    }
}
