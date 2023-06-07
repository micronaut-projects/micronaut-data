package io.micronaut.data.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.data.connection.ConnectionDefinition;
import jakarta.inject.Singleton;

@Requires(property = AbstractBookService.BOOK_REPOSITORY_CLASS_PROPERTY)
@Singleton
public class ConnectionBookService extends AbstractBookService {

    public ConnectionBookService(ApplicationContext beanContext) {
        super(beanContext);
    }

    public void addBookNoConnection() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Connectable
    public void bookAddedInMandatoryConnection() {
        mandatoryConnection();
    }

    @Connectable(propagation = ConnectionDefinition.Propagation.MANDATORY)
    public void mandatoryConnection() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Connectable(propagation = ConnectionDefinition.Propagation.MANDATORY)
    public void mandatoryConnection(Runnable check) {
        bookRepository.save(newBook("MandatoryBook"));
        check.run();
    }

    @Connectable
    public void checkInConnection(Runnable runnable) {
        runnable.run();
    }

    @Connectable
    public void bookIsAddedInConnectionMethod() {
        bookRepository.save(newBook("Toys"));
    }

    @Connectable
    public void bookIsAddedInAnotherRequiresNewConnection() {
        addBookRequiresNew();
    }

    @Connectable
    public void bookIsAddedInAnotherRequiresNewConnectionWhichIsFailing() {
        addBookRequiresNewFailing();
    }

    @Connectable
    public void bookIsAddedAndAnotherRequiresNewConnectionIsFailing() {
        bookRepository.save(newBook("Book1"));
        connectionRequiresNewFailing();
    }

    @Connectable
    public void innerConnectionHasSuppressedException() {
        try {
            connectionFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @Connectable
    public void innerRequiresNewConnectionHasSuppressedException() {
        try {
            connectionRequiresNewFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @Connectable(propagation = ConnectionDefinition.Propagation.REQUIRES_NEW)
    protected void addBookRequiresNew() {
        bookRepository.save(newBook("Book1"));
    }

    @Connectable(propagation = ConnectionDefinition.Propagation.REQUIRES_NEW)
    protected void addBookRequiresNewFailing() {
        bookRepository.save(newBook("Book2"));
        throw new IllegalStateException("Big fail!");
    }

    @Connectable(propagation = ConnectionDefinition.Propagation.REQUIRES_NEW)
    protected void connectionRequiresNewFailing() {
        throw new IllegalStateException("Big fail!");
    }

    @Connectable
    protected void connectionFailing() {
        throw new IllegalStateException("Big fail!");
    }

}
