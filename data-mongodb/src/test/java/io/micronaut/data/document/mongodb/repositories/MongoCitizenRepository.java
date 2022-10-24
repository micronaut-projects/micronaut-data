package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.document.tck.entities.Citizen;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.document.tck.repositories.CitizenRepository;

import java.util.Optional;

@MongoRepository
public interface MongoCitizenRepository extends CitizenRepository {

    @Join(value = "settlements.id.county")
    @Join(value = "settlements.zone")
    @Join(value = "settlements.settlementType")
    @Override
    Optional<Citizen> findById(@NonNull String id);
}
