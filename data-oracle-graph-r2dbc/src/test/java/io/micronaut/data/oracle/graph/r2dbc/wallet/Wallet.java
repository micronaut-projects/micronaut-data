package io.micronaut.data.oracle.graph.r2dbc.wallet;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Node(value = "wallets", labels = "Wallet")
public record Wallet(@Id Long id,
                     String owner,
                     @Nullable LocalDateTime opened,
                     @Nullable Instant audited,
                     @Relationship("PAID") List<Payment> payments) {

    @Creator
    public Wallet {
    }

    public Wallet(Long id, String owner, List<Payment> payments) {
        this(id, owner, null, null, payments);
    }
}
