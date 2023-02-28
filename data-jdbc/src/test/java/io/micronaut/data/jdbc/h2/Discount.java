package io.micronaut.data.jdbc.h2;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Discount {

    private Double amount;

    private int numberOfDays;

    private String note;

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public int getNumberOfDays() {
        return numberOfDays;
    }

    public void setNumberOfDays(int numberOfDays) {
        this.numberOfDays = numberOfDays;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
