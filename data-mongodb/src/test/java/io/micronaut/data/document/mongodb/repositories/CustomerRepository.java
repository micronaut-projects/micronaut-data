package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.mongodb.entities.Customer;
import io.micronaut.data.document.mongodb.entities.CustomerView;
import io.micronaut.data.mongodb.annotation.MongoFindQuery;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@MongoRepository
public interface CustomerRepository extends CrudRepository<Customer, String> {

    @MongoFindQuery(filter = "{}", project = "{ changeLogs: 0}")
    List<CustomerView> viewFindAll();
}
