package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class DoubleImplement1 implements DoubleImplementB1 {
    int a;
    int b;

    public DoubleImplement1(int a, int b) {
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
