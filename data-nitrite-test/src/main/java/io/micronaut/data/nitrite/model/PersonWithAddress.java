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
 * Entity with nested embedded object for testing nested field queries. Tests Gap 2: No embedded
 * document / nested object field.
 */
@MappedEntity("persons_with_address")
public class PersonWithAddress {
  @Id @GeneratedValue private String id;

  private String name;
  private int age;
  private Address address;

  public PersonWithAddress() { }

  public PersonWithAddress(String name, int age, Address address) {
    this.name = name;
    this.age = age;
    this.address = address;
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
  public int getAge() {
    return age;
  }

  /**
   * @param age the age
   */
  public void setAge(int age) {
    this.age = age;
  }

  /**
   * @return the address
   */
  public Address getAddress() {
    return address;
  }

  /**
   * @param address the address
   */
  public void setAddress(Address address) {
    this.address = address;
  }

  /** Embedded address value object. */
  public static class Address {
    private String street;
    private String city;
    private String state;
    private String zipCode;

    public Address() { }

    public Address(String street, String city, String state, String zipCode) {
      this.street = street;
      this.city = city;
      this.state = state;
      this.zipCode = zipCode;
    }

    /**
     * @return the street
     */
    public String getStreet() {
      return street;
    }

    /**
     * @param street the street
     */
    public void setStreet(String street) {
      this.street = street;
    }

    /**
     * @return the city
     */
    public String getCity() {
      return city;
    }

    /**
     * @param city the city
     */
    public void setCity(String city) {
      this.city = city;
    }

    /**
     * @return the state
     */
    public String getState() {
      return state;
    }

    /**
     * @param state the state
     */
    public void setState(String state) {
      this.state = state;
    }

    /**
     * @return the zip code
     */
    public String getZipCode() {
      return zipCode;
    }

    /**
     * @param zipCode the zip code
     */
    public void setZipCode(String zipCode) {
      this.zipCode = zipCode;
    }
  }
}
