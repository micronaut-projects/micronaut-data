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
 * Person entity for testing.
 */
@MappedEntity("persons")
public class Person {
  @Id @GeneratedValue private String id;

  private String name;
  private Integer age;
  private Boolean active;

  public Person() { }

  public Person(String name, Integer age) {
    this.name = name;
    this.age = age;
    this.active = true;
  }

  public Person(String name, Integer age, Boolean active) {
    this.name = name;
    this.age = age;
    this.active = active;
  }

  /**
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * @param id the id
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the age
   */
  public Integer getAge() {
    return age;
  }

  /**
   * @param age the age
   */
  public void setAge(Integer age) {
    this.age = age;
  }

  /**
   * @return is active
   */
  public Boolean isActive() {
    return active;
  }

  /**
   * @param active is active
   */
  public void setActive(Boolean active) {
    this.active = active;
  }
}
