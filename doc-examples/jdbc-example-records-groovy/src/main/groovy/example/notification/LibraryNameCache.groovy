package example.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton

import java.util.concurrent.ConcurrentHashMap

@Singleton
@Requires(property = "query-notification.query.enabled")
class LibraryNameCache {

    private final LibraryRepository repository
    private volatile Map<Long, String> libraryNames = new ConcurrentHashMap<>()
    private volatile boolean cacheEnabled = true

    LibraryNameCache(LibraryRepository repository) {
        this.repository = repository
    }

    @PostConstruct
    void initialize() {
        refreshCache()
    }

    Optional<String> find(long id) {
        cacheEnabled ? Optional.ofNullable(libraryNames.get(id))
            : repository.findById(id).filter { library -> library.capacity() >= 10000 }.map { library -> library.name() }
    }

    boolean contains(String name) {
        cacheEnabled ? libraryNames.containsValue(name)
            : repository.findByCapacityGreaterThanEquals(10000).any { library -> library.name() == name }
    }

    // tag::query[]
    @ChangeListener
    @OracleChangeNotification(
        select = "id, name",
        where = "capacity >= 10000",
        properties = @OracleChangeNotification.Property(
            name = "DCN_QUERY_CHANGE_NOTIFICATION",
            value = "true"
        )
    )
    synchronized void onLibraryChanged(ChangeEvent<Library> event) {
        if (!cacheEnabled) {
            return
        }
        switch (event.operation()) {
            case ChangeOperation.INSERT:
            case ChangeOperation.UPDATE:
                event.entity().ifPresent { library ->
                    if (library.capacity() >= 10000) {
                        libraryNames.put(library.id(), library.name())
                    } else {
                        libraryNames.remove(library.id())
                    }
                }
                break
            case ChangeOperation.DELETE:
            case ChangeOperation.INVALIDATE:
                refreshCache()
                break
            case ChangeOperation.TERMINATED:
                cacheEnabled = false
                libraryNames.clear()
                break
        }
    }
    // end::query[]

    private synchronized void refreshCache() {
        Map<Long, String> refreshed = new ConcurrentHashMap<>()
        repository.findByCapacityGreaterThanEquals(10000).forEach { library -> refreshed.put(library.id(), library.name()) }
        libraryNames = refreshed
    }
}
