package example.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Singleton
@Requires(property = "query-notification.query.enabled")
open class LibraryNameCache(private val repository: LibraryRepository) {

    @Volatile
    private var libraryNames = ConcurrentHashMap<Long, String>()

    @Volatile
    private var cacheEnabled = true

    @PostConstruct
    fun initialize() {
        refreshCache()
    }

    fun find(id: Long): Optional<String> {
        return if (cacheEnabled) Optional.ofNullable(libraryNames[id])
            else repository.findById(id).filter { library -> library.capacity >= 10000 }.map { library -> library.name }
    }

    fun contains(name: String): Boolean {
        return if (cacheEnabled) libraryNames.containsValue(name)
            else repository.findByCapacityGreaterThanEquals(10000).any { library -> library.name == name }
    }

    // tag::query[]
    @Synchronized
    @ChangeListener
    @OracleChangeNotification(
        select = "id, name",
        where = "capacity >= 10000",
        properties = [
            OracleChangeNotification.Property(
                name = "DCN_QUERY_CHANGE_NOTIFICATION",
                value = "true"
            )
        ]
    )
    open fun onLibraryChanged(event: ChangeEvent<Library>) {
        if (!cacheEnabled) {
            return
        }
        when (event.operation()) {
            ChangeOperation.INSERT, ChangeOperation.UPDATE -> event.entity().ifPresent { library ->
                if (library.capacity >= 10000) {
                    libraryNames[library.id!!] = library.name
                } else {
                    libraryNames.remove(library.id!!)
                }
            }
            ChangeOperation.DELETE, ChangeOperation.INVALIDATE -> refreshCache()
            ChangeOperation.TERMINATED -> {
                cacheEnabled = false
                libraryNames.clear()
            }
        }
    }
    // end::query[]

    @Synchronized
    private fun refreshCache() {
        val refreshed = ConcurrentHashMap<Long, String>()
        repository.findByCapacityGreaterThanEquals(10000).forEach { library -> refreshed[library.id!!] = library.name }
        libraryNames = refreshed
    }
}
