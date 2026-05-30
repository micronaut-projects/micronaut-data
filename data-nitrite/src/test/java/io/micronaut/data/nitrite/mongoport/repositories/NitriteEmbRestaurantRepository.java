package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteEmbAddress;
import io.micronaut.data.nitrite.mongoport.entities.NitriteEmbRestaurant;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteEmbRestaurantRepository extends CrudRepository<NitriteEmbRestaurant, String>, PageableRepository<NitriteEmbRestaurant, String> {

    NitriteEmbRestaurant findByAddress(NitriteEmbAddress address);
}
