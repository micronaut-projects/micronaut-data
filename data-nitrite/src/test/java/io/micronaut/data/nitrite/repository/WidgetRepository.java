package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Widget;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.UUID;

@NitriteRepository
public interface WidgetRepository extends CrudRepository<Widget, UUID> {

  List<Widget> findByName(String name);
}
