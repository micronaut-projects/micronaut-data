
package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Repository
interface BookRepository extends CrudRepository<Book, Long> {

    @WithoutTenantId
    List<Book> findAll$WithoutTenancy();

}
