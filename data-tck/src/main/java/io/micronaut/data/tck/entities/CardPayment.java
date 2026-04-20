package io.micronaut.data.tck.entities;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("CARD")
@AttributeOverrides({
    @AttributeOverride(name = "total.currency", column = @Column(name = "card_currency")),
    @AttributeOverride(name = "total.amount", column = @Column(name = "card_amount"))
})
public class CardPayment extends Payment {

    private String cardLast4;

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }
}
