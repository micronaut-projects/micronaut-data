package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteEmbAddress;
import io.micronaut.data.nitrite.model.NitriteEmbRestaurant;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteEmbRestaurantRepository extends CrudRepository<NitriteEmbRestaurant, String>, PageableRepository<NitriteEmbRestaurant, String> {

    NitriteEmbRestaurant findByAddress(NitriteEmbAddress address);
}
