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
@Requires(property = "query-notification.object.enabled")
open class LibraryCache(private val repository: LibraryRepository) : ApplicationEventListener<StartupEvent> {

    private val libraries = ConcurrentHashMap<Long, Library>()

    override fun onApplicationEvent(event: StartupEvent) {
        repository.findAll().forEach { library -> libraries[library.id!!] = library }
    }

    fun find(name: String): Optional<Library> = libraries.values
        .firstOrNull { library -> library.name == name }
        ?.let { Optional.of(it) } ?: Optional.empty()

    @ChangeListener
    @OracleChangeNotification
    open fun onLibraryChanged(event: ChangeEvent<Library>) {
        event.entity().ifPresent { library -> libraries[library.id!!] = library }
    }
}
