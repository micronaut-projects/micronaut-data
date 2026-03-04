/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Models a Nitrite-backed person document for the test suite.
 */
@MappedEntity("persons")
public class Person {
  @Id @GeneratedValue private String id;

  private String name;
  private Integer age;
  private Boolean active;

  /**
   * Default constructor for reflection-based mapping.
   */
  public Person() {}

  /**
   * Constructs a person with the supplied name and age and marks them active.
   *
   * @param name the person's name
   * @param age the person's age
   */
  public Person(String name, Integer age) {
    this.name = name;
    this.age = age;
    this.active = true;
  }

  /**
   * Constructs a person with the supplied name, age, and active flag.
   *
   * @param name the person's name
   * @param age the person's age
   * @param active whether the person is active
   */
  public Person(String name, Integer age, Boolean active) {
    this.name = name;
    this.age = age;
    this.active = active;
  }

  /**
   * Returns the document identifier.
   *
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the document identifier.
   *
   * @param id the new id
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Returns the stored name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the stored name.
   *
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the stored age.
   *
   * @return the age
   */
  public Integer getAge() {
    return age;
  }

  /**
   * Sets the stored age.
   *
   * @param age the age to set
   */
  public void setAge(Integer age) {
    this.age = age;
  }

  /**
   * Indicates whether the person is active.
   *
   * @return true when active
   */
  public Boolean isActive() {
    return active;
  }

  /**
   * Sets the active flag.
   *
   * @param active whether the person should be treated as active
   */
  public void setActive(Boolean active) {
    this.active = active;
  }
}
