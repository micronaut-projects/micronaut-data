package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class DoubleImplement2 implements DoubleImplementB2 {
    int a;
    int b;

    public DoubleImplement2(int a, int b) {
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
