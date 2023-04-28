package io.micronaut.data.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connection;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import jakarta.inject.Singleton;

import javax.transaction.Transactional;

@Requires(property = AbstractBookService.BOOK_REPOSITORY_CLASS_PROPERTY)
@Singleton
public class ConnectionBookService extends AbstractBookService {

    public ConnectionBookService(ApplicationContext beanContext) {
        super(beanContext);
    }

    public void addBookNoConnection() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Connection
    public void bookAddedInMandatoryConnection() {
        mandatoryConnection();
    }

    @Connection(propagation = ConnectionDefinition.Propagation.MANDATORY)
    public void mandatoryConnection() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Connection(propagation = ConnectionDefinition.Propagation.MANDATORY)
    public void mandatoryConnection(Runnable check) {
        bookRepository.save(newBook("MandatoryBook"));
        check.run();
    }

    @Connection
    public void checkInConnection(Runnable runnable) {
        runnable.run();
    }

    @Connection
    public void bookIsAddedInConnectionMethod() {
        bookRepository.save(newBook("Toys"));
    }

    @Connection
    public void bookIsAddedInAnotherRequiresNewConnection() {
        addBookRequiresNew();
    }

    @Connection
    public void bookIsAddedInAnotherRequiresNewConnectionWhichIsFailing() {
        addBookRequiresNewFailing();
    }

    @Connection
    public void bookIsAddedAndAnotherRequiresNewConnectionIsFailing() {
        bookRepository.save(newBook("Book1"));
        connectionRequiresNewFailing();
    }

    @Connection
    public void innerConnectionHasSuppressedException() {
        try {
            connectionFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @Connection
    public void innerRequiresNewConnectionHasSuppressedException() {
        try {
            connectionRequiresNewFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @Connection(propagation = ConnectionDefinition.Propagation.REQUIRES_NEW)
    protected void addBookRequiresNew() {
        bookRepository.save(newBook("Book1"));
    }

    @Connection(propagation = ConnectionDefinition.Propagation.REQUIRES_NEW)
    protected void addBookRequiresNewFailing() {
        bookRepository.save(newBook("Book2"));
        throw new IllegalStateException("Big fail!");
    }

    @Connection(propagation = ConnectionDefinition.Propagation.REQUIRES_NEW)
    protected void connectionRequiresNewFailing() {
        throw new IllegalStateException("Big fail!");
    }

    @Connection
    protected void connectionFailing() {
        throw new IllegalStateException("Big fail!");
    }

}
