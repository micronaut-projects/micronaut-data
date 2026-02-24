package example

import io.micronaut.data.annotation.Fetch
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import jakarta.validation.constraints.NotNull
import org.jspecify.annotations.NonNull

import java.util.stream.Stream

@Repository
interface UserRepository extends CrudRepository<User, Long> {

    @Override
    @Query("UPDATE User SET enabled = false WHERE id = :id")
    void deleteById(@NonNull @NotNull Long id)

    @Query("FROM User user WHERE enabled = false")
    List<User> findDisabled()

    // tag::fetch_size_streaming[]

    /**
     * Retrieves all users from the data store in batches.
     *
     * The returned Stream is lazily evaluated, meaning that it will only fetch data as it is consumed.
     * The fetch size is set to 1500, which controls the number of rows fetched from the database at a time.
     * It is the caller's responsibility to close the Stream to release any underlying resources.
     *
     * @return a Stream of all enabled users
     */
    @Fetch(1500)
    Stream<User> listAll()

    /**
     * Retrieves all users from the data store.
     *
     * The returned Stream is lazily evaluated, meaning that it will only fetch data as it is consumed.
     * It is the caller's responsibility to close the Stream to release any underlying resources.
     *
     * @return a Stream of all users
     */
    Stream<User> queryAll()

    // end::fetch_size_streaming[]
}
