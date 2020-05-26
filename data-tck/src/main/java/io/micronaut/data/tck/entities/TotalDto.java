package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class TotalDto {
    private final int total;

    public TotalDto(int total) {
        this.total = total;
    }

    public int getTotal() {
        return total;
    }
}
