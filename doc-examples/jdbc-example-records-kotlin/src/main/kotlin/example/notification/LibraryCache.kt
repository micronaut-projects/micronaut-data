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
    @Synchronized
    // tag::listener-signature[]
    @ChangeListener // <1>
    // end::listener-signature[]
    @OracleChangeNotification // <2>
    // tag::events[]
    // tag::listener-signature[]
    open fun onLibraryChanged(event: ChangeEvent<Library>) {
        // end::listener-signature[]
        // end::listener[]
        if (!cacheEnabled) {
            return
        }
        event.metadata(OracleChangeEventMetadata::class.java).ifPresent { metadata -> // <1>
            log.debug("Changed library table: {}.{}, ROWID: {}",
                metadata.schema() ?: "<unknown>", metadata.table(), metadata.rowId())
        }
        when (event.operation()) {
            ChangeOperation.INSERT, ChangeOperation.UPDATE ->
                event.entity().ifPresent { library -> libraries[library.id!!] = library } // <2>
            ChangeOperation.DELETE, ChangeOperation.INVALIDATE -> refreshCache() // <3>
            ChangeOperation.TERMINATED -> {
                cacheEnabled = false // <4>
                libraries.clear()
            }
        }
        // tag::listener[]
    // tag::listener-signature[]
    }
    // end::listener-signature[]
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
