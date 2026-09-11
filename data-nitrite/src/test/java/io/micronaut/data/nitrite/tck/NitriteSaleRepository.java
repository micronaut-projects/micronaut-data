package io.micronaut.data.nitrite.tck;

import io.micronaut.data.document.tck.repositories.SaleRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;

@NitriteRepository
public interface NitriteSaleRepository extends SaleRepository {
}
