package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteCategory;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@NitriteRepository
public interface NitriteCategoryRepository extends CrudRepository<NitriteCategory, String>, PageableRepository<NitriteCategory, String> {

    @Join(value = "productList")
    @Join(value = "productList.productOption")
    @Join(value = "productList.productOption.option")
    @Override
    Optional<NitriteCategory> findById(String id);

    @Join(value = "productList")
    @Join(value = "productList.productOption")
    @Join(value = "productList.productOption.option")
    Iterable<NitriteCategory> listAll();

    Optional<NitriteCategory> queryById(String id);
}
