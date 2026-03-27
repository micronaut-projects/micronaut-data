package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteSong;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteSongRepository extends CrudRepository<NitriteSong, String>, PageableRepository<NitriteSong, String> {
}
