package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Entity used to demonstrate Criteria API support.
 */
@MappedEntity("criteria_persons")
public class CriteriaPerson {

  @Id
  @GeneratedValue
  private String id;

  private String name;

  private int age;

  /** Default constructor for the Criteria API tests. */
  public CriteriaPerson() {}

  /**
   * Constructs a criteria person.
   *
   * @param name person name
   * @param age person age
   */
  public CriteriaPerson(String name, int age) {
    this.name = name;
    this.age = age;
  }

  /**
   * Returns the document identifier.
   *
   * @return id
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the document identifier.
   *
   * @param id new id
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Returns the stored name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the stored name.
   *
   * @param name new name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the stored age.
   *
   * @return age
   */
  public int getAge() {
    return age;
  }

  /**
   * Sets the stored age.
   *
   * @param age new age
   */
  public void setAge(int age) {
    this.age = age;
  }
}
