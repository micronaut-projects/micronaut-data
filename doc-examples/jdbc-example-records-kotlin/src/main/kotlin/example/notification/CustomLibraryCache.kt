package example.notification

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Context
@Requires(property = "query-notification.query.enabled")
open class CustomLibraryCache(private val repository: LibraryRepository) : ApplicationEventListener<StartupEvent> {

    private val libraries = ConcurrentHashMap<Long, Library>()

    override fun onApplicationEvent(event: StartupEvent) {
        repository.findByCapacityGreaterThanEquals(10000).forEach { library -> libraries[library.id!!] = library }
    }

    fun find(name: String): Optional<Library> = libraries.values
        .firstOrNull { library -> library.name == name }
        ?.let { Optional.of(it) } ?: Optional.empty()

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
        event.entity().ifPresent { library ->
            if (library.capacity >= 10000) {
                libraries[library.id!!] = library
            } else {
                libraries.remove(library.id!!)
            }
        }
    }
}
