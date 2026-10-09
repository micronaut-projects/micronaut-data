package example.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Singleton
@Requires(property = "query-notification.query.enabled")
open class LargeLibraryCache(private val repository: LibraryRepository) {

    @Volatile
    private var libraries = ConcurrentHashMap<Long, Library>()

    @PostConstruct
    fun initialize() {
        refreshCache()
    }

    fun find(name: String): Optional<Library> = libraries.values
        .firstOrNull { library -> library.name == name }
        ?.let { Optional.of(it) } ?: Optional.empty()

    // tag::query[]
    @Synchronized
    @ChangeListener
    @OracleChangeNotification(
        select = "name",
        where = "capacity >= 10000",
        properties = [
            OracleChangeNotification.Property(
                name = "DCN_QUERY_CHANGE_NOTIFICATION",
                value = "true"
            )
        ]
    )
    open fun onLibraryChanged(event: ChangeEvent<Library>) {
        when (event.operation()) {
            ChangeOperation.INSERT, ChangeOperation.UPDATE -> event.entity().ifPresent { library ->
                if (library.capacity >= 10000) {
                    libraries[library.id!!] = library
                } else {
                    libraries.remove(library.id!!)
                }
            }
            ChangeOperation.DELETE, ChangeOperation.INVALIDATE -> refreshCache()
        }
    }
    // end::query[]

    @Synchronized
    private fun refreshCache() {
        val currentLibraries = repository.findByCapacityGreaterThanEquals(10000)
        val refreshed = ConcurrentHashMap<Long, Library>()
        currentLibraries.forEach { library -> refreshed[library.id!!] = library }
        libraries = refreshed
    }
}
