package example.controllers;

import javax.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import example.domain.Owner;
import example.repositories.*;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.annotation.MicronautTest;

import java.util.List;
import static example.repositories.OwnerRepository.*;

@MicronautTest
class OwnerControllerTest {

    @Inject
    @Client("/owners")
    RxHttpClient client;

    @Inject
    OwnerRepository ownerRepository;

    @Test
    void testListInitialOwners() {

        List<Owner> results = client.retrieve(HttpRequest.GET("/"), Argument.listOf(Owner.class)).blockingFirst();

        Assertions.assertEquals(
                2,
                results.size()
        );
    }

    @Test
    void testSpecification() {
          Assertions.assertEquals(
                1,
                ownerRepository.count(ageGreaterThanThirty())
        );
    }
}