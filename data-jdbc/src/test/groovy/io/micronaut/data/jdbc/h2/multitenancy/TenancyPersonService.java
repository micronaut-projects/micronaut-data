package io.micronaut.data.jdbc.h2.multitenancy;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
class TenancyPersonService {

    private final TenancyPersonRepository tenancyPersonRepository;

    TenancyPersonService(TenancyPersonRepository tenancyPersonRepository) {
        this.tenancyPersonRepository = tenancyPersonRepository;
    }

    TenancyPerson save(TenancyPerson tenancyPerson) {
        return tenancyPersonRepository.insert(tenancyPerson);
    }

    List<TenancyPerson> saveAll(List<TenancyPerson> tenancyPeople) {
        return tenancyPersonRepository.insertAll(tenancyPeople);
    }

    Integer insertWithQuery(List<TenancyPerson> tenancyPeople) {
        return tenancyPersonRepository.insertWithQuery(tenancyPeople);
    }

    void insertWithQuerySingle(TenancyPerson tenancyPerson) {
        tenancyPersonRepository.insertWithQuerySingle(tenancyPerson);
    }

    Integer insertWithQueryTheLongWay(List<TenancyPerson> tenancyPeople) {
        AtomicInteger count = new AtomicInteger();
        tenancyPeople.forEach(tenancyPerson -> {
            count.set(count.get() + tenancyPersonRepository.insertWithQueryTheLongWay(tenancyPerson.id(),
                tenancyPerson.firstName(), tenancyPerson.lastName(), tenancyPerson.tenantId()));
        });

        return count.get();
    }

    Optional<TenancyPerson> findById(Integer id) {
        return tenancyPersonRepository.findById(id);
    }

    List<TenancyPerson> findAll() {
        return tenancyPersonRepository.findAll();
    }

    void deleteAll() {
        tenancyPersonRepository.deleteAll();
    }
}
