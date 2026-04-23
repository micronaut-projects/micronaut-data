package io.micronaut.data.jdbc.sqlite.jakarta_data.entity;

import io.micronaut.core.annotation.Introspected;

@jakarta.persistence.Entity
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class Box {
    @jakarta.persistence.Id
    public String boxIdentifier;

    public int length;

    public int width;

    public int height;

    public static Box of(String id, int length, int width, int height) {
        Box box = new Box();
        box.boxIdentifier = id;
        box.length = length;
        box.width = width;
        box.height = height;
        return box;
    }

    @Override
    public String toString() {
        return "Box@" + Integer.toHexString(hashCode()) + ":" + length + "x" + width + "x" + height + ":" + boxIdentifier;
    }
}
