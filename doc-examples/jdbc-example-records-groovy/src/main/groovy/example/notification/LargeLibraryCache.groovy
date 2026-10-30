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
class LargeLibraryCache {

    private final LibraryRepository repository
    private volatile Map<Long, Library> libraries = new ConcurrentHashMap<>()
    private volatile boolean cacheEnabled = true

    LargeLibraryCache(LibraryRepository repository) {
        this.repository = repository
    }

    @PostConstruct
    void initialize() {
        refreshCache()
    }

    Optional<Library> find(String name) {
        def currentLibraries = cacheEnabled ? libraries.values() : repository.findByCapacityGreaterThanEquals(10000)
        currentLibraries.stream()
            .filter { library -> library.name() == name }
            .findFirst()
    }

    // tag::query[]
    @ChangeListener
    @OracleChangeNotification(
        select = "name",
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
                        libraries.put(library.id(), library)
                    } else {
                        libraries.remove(library.id())
                    }
                }
                break
            case ChangeOperation.DELETE:
            case ChangeOperation.INVALIDATE:
                refreshCache()
                break
            case ChangeOperation.TERMINATED:
                cacheEnabled = false
                libraries.clear()
                break
        }
    }
    // end::query[]

    private synchronized void refreshCache() {
        def currentLibraries = repository.findByCapacityGreaterThanEquals(10000)
        Map<Long, Library> refreshed = new ConcurrentHashMap<>()
        currentLibraries.forEach { library -> refreshed.put(library.id(), library) }
        libraries = refreshed
    }
}
