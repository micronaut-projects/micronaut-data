package example.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Requires(property = "query-notification.object.enabled")
// tag::listener[]
@Singleton
open class LibraryCache(private val repository: LibraryRepository) {
    // end::listener[]
    private val log = LoggerFactory.getLogger(LibraryCache::class.java)

    private val libraries = ConcurrentHashMap<Long, Library>()

    @PostConstruct
    fun initialize() {
        refreshCache()
    }

    fun find(name: String): Optional<Library> = libraries.values
        .firstOrNull { library -> library.name == name }
        ?.let { Optional.of(it) } ?: Optional.empty()

    // tag::listener[]
    // tag::events[]
    @ChangeListener
    @OracleChangeNotification
    open fun onLibraryChanged(event: ChangeEvent<Library>) {
        // end::listener[]
        event.metadata(OracleChangeEventMetadata::class.java).ifPresent { metadata ->
            log.debug("Changed library ROWID: {}", metadata.rowId())
        }
        when (event.operation()) {
            ChangeOperation.INSERT, ChangeOperation.UPDATE ->
                event.entity().ifPresent { library -> libraries[library.id!!] = library }
            ChangeOperation.DELETE, ChangeOperation.INVALIDATE -> refreshCache()
        }
        // tag::listener[]
    }
    // end::events[]
    // end::listener[]

    @Synchronized
    private fun refreshCache() {
        val currentLibraries = repository.findAll()
        libraries.clear()
        currentLibraries.forEach { library -> libraries[library.id!!] = library }
    }
    // tag::listener[]
}
// end::listener[]
