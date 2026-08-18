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
package io.micronaut.data.nitrite.generated;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Owning side of the association exercised by {@link GeneratedQueryBookRepository}, so the suite
 * can assert that a dotted reference such as {@code book_.author.id} in a generated predicate
 * resolves to the persisted document path.
 */
@MappedEntity("generated_query_authors")
public class GeneratedQueryAuthor {

  @Id
  @GeneratedValue
  private String id;

  private String name;

  /** Default constructor used by the data runtime. */
  public GeneratedQueryAuthor() {
  }

  /**
   * Creates an author with the supplied name.
   *
   * @param name author name
   */
  public GeneratedQueryAuthor(String name) {
    this.name = name;
  }

  /**
   * Returns the author identifier.
   *
   * @return id
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the author identifier.
   *
   * @param id new id
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Returns the author name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the author name.
   *
   * @param name new name
   */
  public void setName(String name) {
    this.name = name;
  }
}
