package example.notification

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Context
@Requires(property = "query-notification.query.enabled")
open class LargeLibraryCache(private val repository: LibraryRepository) : ApplicationEventListener<StartupEvent> {

    private val libraries = ConcurrentHashMap<Long, Library>()

    override fun onApplicationEvent(event: StartupEvent) {
        refreshCache()
    }

    fun find(name: String): Optional<Library> = libraries.values
        .firstOrNull { library -> library.name == name }
        ?.let { Optional.of(it) } ?: Optional.empty()

    // tag::query[]
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
    @Synchronized
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
        libraries.clear()
        currentLibraries.forEach { library -> libraries[library.id!!] = library }
    }
}
