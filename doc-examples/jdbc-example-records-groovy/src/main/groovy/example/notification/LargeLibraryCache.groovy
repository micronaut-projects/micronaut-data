package example.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton

import java.util.concurrent.ConcurrentHashMap

@Singleton
@Requires(property = "query-notification.query.enabled")
class LargeLibraryCache {

    private final LibraryRepository repository
    private final Map<Long, Library> libraries = new ConcurrentHashMap<>()

    LargeLibraryCache(LibraryRepository repository) {
        this.repository = repository
    }

    @PostConstruct
    void initialize() {
        refreshCache()
    }

    Optional<Library> find(String name) {
        libraries.values().stream()
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
    void onLibraryChanged(ChangeEvent<Library> event) {
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
        }
    }
    // end::query[]

    private synchronized void refreshCache() {
        def currentLibraries = repository.findByCapacityGreaterThanEquals(10000)
        libraries.clear()
        currentLibraries.forEach { library -> libraries.put(library.id(), library) }
    }
}
