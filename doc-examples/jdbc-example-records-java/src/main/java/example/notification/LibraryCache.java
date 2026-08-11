package example.notification;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.data.jdbc.annotation.ChangeListener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Context
@Requires(property = "query-notification.object.enabled")
final class LibraryCache implements ApplicationEventListener<StartupEvent> {

    private final LibraryRepository repository;
    private final Map<Long, Library> libraries = new ConcurrentHashMap<>();

    LibraryCache(LibraryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        repository.findAll().forEach(library -> libraries.put(library.id(), library));
    }

    public Optional<Library> find(String name) {
        return libraries.values()
            .stream()
            .filter(library -> library.name().equals(name))
            .findFirst();
    }

    @ChangeListener(properties = @ChangeListener.Property(
        name = "DCN_CLIENT_INIT_CONNECTION", value = "true"
    ))
    void onLibraryChanged(Library library) {
        libraries.put(library.id(), library);
    }
}
