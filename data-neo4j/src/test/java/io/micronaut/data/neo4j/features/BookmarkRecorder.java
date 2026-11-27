package io.micronaut.data.neo4j.features;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.data.neo4j.session.Neo4jDriverRegistration;
import jakarta.inject.Singleton;
import org.neo4j.driver.Bookmark;
import org.neo4j.driver.BookmarkManager;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wraps the default driver so that a test sees the sessions it opens, with the thread that opened each one and its
 * bookmark manager, and the bookmarks the bookmark manager hands to and takes from the transactions of those sessions.
 */
@Singleton
@Requires(property = "spec.name", value = "Neo4jDriversSpec")
public class BookmarkRecorder implements BeanCreatedEventListener<Neo4jDriverRegistration> {

    private final List<OpenedSession> sessions = new CopyOnWriteArrayList<>();
    private final List<Set<Bookmark>> handedOut = new CopyOnWriteArrayList<>();
    private final List<Set<Bookmark>> received = new CopyOnWriteArrayList<>();
    private volatile BookmarkManager manager;

    @Override
    public Neo4jDriverRegistration onCreated(BeanCreatedEvent<Neo4jDriverRegistration> event) {
        Neo4jDriverRegistration registration = event.getBean();
        Driver driver = registration.driver();
        manager = new RecordingBookmarkManager(driver.executableQueryBookmarkManager());
        Driver recording = (Driver) Proxy.newProxyInstance(Driver.class.getClassLoader(), new Class<?>[]{Driver.class}, (proxy, method, args) -> {
            if (method.getName().equals("executableQueryBookmarkManager")) {
                return manager;
            }
            if (method.getName().equals("session") && args != null) {
                for (Object arg : args) {
                    if (arg instanceof SessionConfig config) {
                        sessions.add(new OpenedSession(Thread.currentThread().getName(), config.bookmarkManager().orElse(null)));
                    }
                }
            }
            try {
                return method.invoke(driver, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
        return new Neo4jDriverRegistration(registration.name(), recording);
    }

    /**
     * @return The bookmark manager the sessions of the driver should share
     */
    public BookmarkManager getManager() {
        return manager;
    }

    /**
     * @return The sessions opened since the last reset
     */
    public List<OpenedSession> getSessions() {
        return sessions;
    }

    /**
     * @return The bookmarks the manager handed to the transactions begun since the last reset
     */
    public List<Set<Bookmark>> getHandedOut() {
        return handedOut;
    }

    /**
     * @return The bookmarks the transactions committed since the last reset left in the manager
     */
    public List<Set<Bookmark>> getReceived() {
        return received;
    }

    /**
     * Forgets what was recorded.
     */
    public void reset() {
        sessions.clear();
        handedOut.clear();
        received.clear();
    }

    /**
     * A session opened through the driver.
     *
     * @param thread          The thread that opened it
     * @param bookmarkManager The bookmark manager of its configuration
     */
    public record OpenedSession(String thread, BookmarkManager bookmarkManager) {
    }

    private final class RecordingBookmarkManager implements BookmarkManager {

        private final BookmarkManager delegate;

        RecordingBookmarkManager(BookmarkManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void updateBookmarks(Set<Bookmark> previousBookmarks, Set<Bookmark> newBookmarks) {
            received.add(Set.copyOf(newBookmarks));
            delegate.updateBookmarks(previousBookmarks, newBookmarks);
        }

        @Override
        public Set<Bookmark> getBookmarks() {
            Set<Bookmark> bookmarks = delegate.getBookmarks();
            handedOut.add(Set.copyOf(bookmarks));
            return bookmarks;
        }
    }
}
