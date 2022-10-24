package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.document.tck.entities.Settlement;
import io.micronaut.data.document.tck.entities.SettlementPk;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.document.tck.repositories.SettlementRepository;

import java.util.List;
import java.util.Optional;

@MongoRepository
public interface MongoSettlementRepository extends SettlementRepository {

    @Join(value = "settlementType")
    @Join(value = "zone")
    @Join(value = "id.county")
    Optional<Settlement> queryById(@NonNull SettlementPk settlementPk);

    @Join(value = "settlementType")
    @Join(value = "zone")
    @Join(value = "id.county")
    List<Settlement> findAll(Pageable pageable);
}
