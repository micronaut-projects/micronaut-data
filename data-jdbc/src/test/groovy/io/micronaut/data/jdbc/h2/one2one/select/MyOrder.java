package io.micronaut.data.jdbc.h2.one2one.select;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.UUID;

@MappedEntity
public class MyOrder {

    @Id
    @AutoPopulated
    private UUID orderId;

    @Nullable
    @Relation(value = Relation.Kind.ONE_TO_ONE)
    private MyEmbedded embedded;

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public MyEmbedded getEmbedded() {
        return embedded;
    }

    public void setEmbedded(MyEmbedded embedded) {
        this.embedded = embedded;
    }
}
