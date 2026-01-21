package example

import io.micronaut.data.annotation.Fetch
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import jakarta.validation.constraints.NotNull
import java.util.stream.Stream

@Repository
interface UserRepository : CrudRepository<User, Long> {

    @Query("UPDATE User SET enabled = false WHERE id = :id")
    override fun deleteById(id: @NotNull Long)

    @Query("FROM User user WHERE enabled = false")
    fun findDisabled(): MutableList<User>


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
    fun listAll(): Stream<User>

    /**
     * Retrieves all users from the data store.
     *
     * The returned Stream is lazily evaluated, meaning that it will only fetch data as it is consumed.
     * It is the caller's responsibility to close the Stream to release any underlying resources.
     *
     * @return a Stream of all users
     */
    fun queryAll(): Stream<User>

    // end::fetch_size_streaming[]
}
