package io.micronaut.data.azure.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class ItemPrice {

    private double price;

    public ItemPrice(double price) {
        this.price = price;
    }

    public double getPrice() {
        return price;
    }

    public static ItemPrice valueOf(double price) {
        return new ItemPrice(price);
    }
}
