package example.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
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

    @Volatile
    private var libraries = ConcurrentHashMap<Long, Library>()

    @Volatile
    private var cacheEnabled = true

    @PostConstruct
    fun initialize() {
        refreshCache()
    }

    fun find(name: String): Optional<Library> {
        val currentLibraries = if (cacheEnabled) libraries.values else repository.findAll()
        return currentLibraries.firstOrNull { library -> library.name == name }
            ?.let { Optional.of(it) } ?: Optional.empty()
    }

    // tag::listener[]
    // tag::events[]
    @Synchronized
    @ChangeListener
    @OracleChangeNotification
    open fun onLibraryChanged(event: ChangeEvent<Library>) {
        if (!cacheEnabled) {
            return
        }
        // end::listener[]
        event.metadata(OracleChangeEventMetadata::class.java).ifPresent { metadata ->
            log.debug("Changed library ROWID: {}", metadata.rowId())
        }
        when (event.operation()) {
            ChangeOperation.INSERT, ChangeOperation.UPDATE ->
                event.entity().ifPresent { library -> libraries[library.id!!] = library }
            ChangeOperation.DELETE, ChangeOperation.INVALIDATE -> refreshCache()
            ChangeOperation.TERMINATED -> {
                cacheEnabled = false
                libraries.clear()
            }
        }
        // tag::listener[]
    }
    // end::events[]
    // end::listener[]

    @Synchronized
    private fun refreshCache() {
        val currentLibraries = repository.findAll()
        val refreshed = ConcurrentHashMap<Long, Library>()
        currentLibraries.forEach { library -> refreshed[library.id!!] = library }
        libraries = refreshed
    }
    // tag::listener[]
}
// end::listener[]
