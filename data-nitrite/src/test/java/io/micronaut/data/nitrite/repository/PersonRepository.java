package io.micronaut.data.nitrite.repository;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Person;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

/**
 * Repository that exposes CRUD and pageable operations for Nitrite-backed persons.
 */
@NitriteRepository
public interface PersonRepository
    extends CrudRepository<Person, String>, PageableRepository<Person, String>, JpaSpecificationExecutor<Person> {
  /**
   * Finds a person by the exact name.
   *
   * @param name the name to look for
   * @return optional person
   */
  Optional<Person> findByName(String name);

  /**
   * Finds a person by age.
   *
   * @param age the age
   * @return matching persons
   */
  List<Person> findByAge(int age);

  /**
   * Finds all persons older than the supplied age.
   *
   * @param age lower bound age
   * @return matching persons
   */
  List<Person> findByAgeGreaterThan(int age);

  /**
   * Finds persons whose name contains the supplied keyword.
   *
   * @param keyword substring to match
   * @return matching persons
   */
  List<Person> findByNameContaining(String keyword);

  // Comparison operators
  /**
   * Finds persons whose age is less than the supplied threshold.
   *
   * @param age upper bound age
   * @return matching persons
   */
  List<Person> findByAgeLessThan(int age);

  /**
   * Finds persons whose age is less than or equal to the given value.
   *
   * @param age upper bound inclusive age
   * @return matching persons
   */
  List<Person> findByAgeLessThanEquals(int age);

  /**
   * Finds persons who are at least the supplied age.
   *
   * @param age lower bound
   * @return matching persons
   */
  List<Person> findByAgeGreaterThanEquals(int age);

  // Pattern matching
  /**
   * Finds persons whose name begins with the given prefix.
   *
   * @param prefix starting characters
   * @return matching persons
   */
  List<Person> findByNameStartsWith(String prefix);

  /**
   * Finds persons whose name ends with the given suffix.
   *
   * @param suffix trailing characters
   * @return matching persons
   */
  List<Person> findByNameEndsWith(String suffix);

  // Null/Empty checks
  /**
   * Finds persons without a configured name.
   *
   * @return persons missing a name
   */
  List<Person> findByNameIsNull();

  /**
   * Finds persons that have a name value.
   *
   * @return persons with names
   */
  List<Person> findByNameIsNotNull();

  // Range operators
  /**
   * Finds persons whose age sits between the two endpoints (inclusive).
   *
   * @param from lower bound
   * @param to upper bound
   * @return matching persons
   */
  List<Person> findByAgeBetween(int from, int to);

  // Negation (Not)
  /**
   * Finds persons whose age is not the supplied value.
   *
   * @param age excluded age
   * @return matching persons
   */
  List<Person> findByAgeNot(int age);

  // OrderBy (method-name sort)
  /**
   * Finds persons older than the supplied age and sorts them by name ascending.
   *
   * @param age lower bound
   * @return ordered persons
   */
  List<Person> findByAgeGreaterThanOrderByNameAsc(int age);

  /**
   * Finds persons older than the supplied age ordered by name descending.
   *
   * @param age lower bound
   * @return ordered persons
   */
  List<Person> findByAgeGreaterThanOrderByNameDesc(int age);

  /**
   * Finds persons older than the supplied age ordered by age descending.
   *
   * @param age lower bound
   * @return ordered persons
   */
  List<Person> findByAgeGreaterThanOrderByAgeDesc(int age);

  // OrderBy with Pageable (for testing sort precedence)
  /**
   * Finds persons older than the supplied age, ordered by name ascending, with paging.
   *
   * @param age lower bound
   * @param pageable paging information
   * @return paged persons
   */
  List<Person> findByAgeGreaterThanOrderByNameAsc(int age, Pageable pageable);

  // Delete by query
  /**
   * Deletes persons matching the supplied name.
   *
   * @param name name to delete
   * @return number of deletes
   */
  int deleteByName(String name);

  /**
   * Deletes persons younger than the supplied age.
   *
   * @param age cutoff
   * @return number of deletes
   */
  int deleteByAgeLessThan(int age);

  // Count methods
  /**
   * Counts persons older than the supplied age.
   *
   * @param age lower bound
   * @return count
   */
  int countByAgeGreaterThan(int age);

  // Multi-criteria AND queries
  /**
   * Finds persons that match both name and age.
   *
   * @param name the name
   * @param age the age
   * @return matching persons
   */
  List<Person> findByNameAndAge(String name, int age);

  // Update by query
  /**
   * Updates the name of the person with the given identifier.
   *
   * @param id the document id
   * @param name new name
   */
  void updatePerson(@Id String id, @Parameter("name") String name);

  /**
   * Updates the age of persons that share the supplied name.
   *
   * @param name the name to match
   * @param age the new age
   * @return number of updated records
   */
  long updateByName(String name, @Parameter("age") int age);

  /**
   * Updates the age of every person whose id is in the given list.
   *
   * @param ids the document ids to match
   * @param age the new age
   * @return number of updated records
   */
  long updateAgeByIdIn(List<String> ids, @Parameter("age") int age);

  /**
   * Finds all person names (projection to single property using SQL SELECT).
   *
   * @return list of names
   */
  @Query("{\"$project\": \"name\"}")
  List<String> findAllNames();

  /**
   * Finds names of active persons using JSON query with explicit $project field.
   * The $project syntax explicitly specifies which field to return.
   *
   * @return list of names of active persons
   */
  @Query("{\"$project\": \"name\", \"active\": {\"$eq\": true}}")
  List<String> findActivePersonNames();

  /**
   * Appends a suffix to the names of persons matching an age.
   *
   * @param suffix suffix to append
   * @param age age to match
   */
  @Query("{\"$concat\": {\"name\": :suffix}, \"age\": {\"$eq\": :age}}")
  void updateAppendNameToAll(@Parameter("suffix") String suffix, @Parameter("age") int age);
}
