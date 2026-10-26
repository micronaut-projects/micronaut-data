package io.micronaut.data.oracle.graph.bank;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An account of the bank graph. The {@code opened} timestamp is a temporal property, read from a TIMESTAMP column
 * of the vertex table.
 */
@Node("Account")
public record Account(@Id Long id, String name, @Nullable LocalDateTime opened, @Relationship("TRANSFERS") List<Transfer> transfers) {

    @Creator
    public Account {
    }

    public Account(Long id, String name, List<Transfer> transfers) {
        this(id, name, null, transfers);
    }
}
