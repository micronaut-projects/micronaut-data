package example;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface CompositeFkParentRepository extends CrudRepository<CompositeFkParent, String> {
}
