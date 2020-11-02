
package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface BookJpaRepository extends JpaRepository<Book, Long> {
}
