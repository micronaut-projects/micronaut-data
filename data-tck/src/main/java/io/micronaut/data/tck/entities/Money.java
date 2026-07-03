package io.micronaut.data.tck.entities;

import jakarta.persistence.Embeddable;

@Embeddable
public class Money {
    private String currency;
    private java.math.BigDecimal amount;

    public Money() {
    }

    public Money(String currency, java.math.BigDecimal amount) {
        this.currency = currency;
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal amount) {
        this.amount = amount;
    }
}
