package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteMpPerson;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.List;

@NitriteRepository
public interface NitriteMpPersonRepository extends CrudRepository<NitriteMpPerson, String>, PageableRepository<NitriteMpPerson, String> {

    List<NitriteMpPerson> findAllByNameBetween(String startName, String endName);

    List<NitriteMpPerson> findAllByNameNotBetween(String startName, String endName);

    List<NitriteMpPerson> findByIdIn(List<String> ids);

    List<NitriteMpPerson> findByIdNotIn(List<String> ids);

    List<NitriteMpPerson> findByNameIn(List<String> names);

    List<NitriteMpPerson> findByNameIn(String[] names);

    List<NitriteMpPerson> findByNameLike(String pattern);

    List<NitriteMpPerson> findByNameNotLike(String pattern);

    List<NitriteMpPerson> findAllByNameRegexOrderByName(String pattern);

    Page<NitriteMpPerson> findAllByNameRegexOrderByName(String pattern, Pageable pageable);

    default List<NitriteMpPerson> customFind(String pattern) {
        return findAllByNameRegexOrderByName(pattern).stream().map(NitriteMpPersonRepository::projectNameOnly).toList();
    }

    default Page<NitriteMpPerson> customFindPage(String pattern, Pageable pageable) {
        Pageable sortedPageable = pageable.withSort(Sort.of(Sort.Order.asc("name")));
        Page<NitriteMpPerson> page = findAllByNameRegexOrderByName(pattern, sortedPageable);
        return Page.of(page.getContent().stream().map(NitriteMpPersonRepository::projectNameOnly).toList(), pageable, page.getTotalSize());
    }

    private static NitriteMpPerson projectNameOnly(NitriteMpPerson person) {
        NitriteMpPerson projected = new NitriteMpPerson();
        projected.setName(person.getName());
        return projected;
    }
}
