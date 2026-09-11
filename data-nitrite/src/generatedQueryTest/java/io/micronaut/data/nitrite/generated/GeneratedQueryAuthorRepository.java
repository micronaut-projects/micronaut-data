package io.micronaut.data.nitrite.generated;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

/**
 * Author repository for the generated-query fallback suite. Like every repository in this source
 * set it is compiled without the Nitrite query builder on the annotation processor path — see
 * {@link GeneratedQueryBookRepository} for what that means.
 */
@NitriteRepository
public interface GeneratedQueryAuthorRepository extends CrudRepository<GeneratedQueryAuthor, String> {
}
