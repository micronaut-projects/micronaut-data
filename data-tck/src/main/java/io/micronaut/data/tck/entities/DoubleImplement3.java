package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class DoubleImplement3 implements DoubleImplementA, DoubleImplementB3 {
    int a;
    int b;

    public DoubleImplement3(int a, int b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public int getA() {
        return this.a;
    }

    @Override
    public int getB() {
        return this.b;
    }
}
