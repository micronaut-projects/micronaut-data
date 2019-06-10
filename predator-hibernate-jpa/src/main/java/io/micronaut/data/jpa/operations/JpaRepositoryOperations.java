package io.micronaut.data.jpa.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.operations.RepositoryOperations;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * Operations interface specific to JPA.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface JpaRepositoryOperations extends RepositoryOperations {

    /**
     * @return The currrent entity manager
     */
    @NonNull
    EntityManager getCurrentEntityManager();

    /**
     * @return The entity manager factory
     */
    @NonNull
    EntityManagerFactory getEntityManagerFactory();

    /**
     * Flush the current session.
     */
    void flush();
}
