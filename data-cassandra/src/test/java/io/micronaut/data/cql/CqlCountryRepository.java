package io.micronaut.data.cql;

import io.micronaut.data.cql.annotation.CqlRepository;
import io.micronaut.data.tck.repositories.CountryRepository;

@CqlRepository()
interface CqlCountryRepository extends CountryRepository {

}
