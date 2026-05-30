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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a document entity used by the Nitrite test suite.
 */
@MappedEntity("documents")
public class Document {
  @Id @GeneratedValue private String id;

  private String title;
  private List<String> tags;
  private Boolean published;

  /** Default constructor for serialization/deserialization. */
  public Document() {}

  /**
   * Constructs a document with the supplied metadata.
   *
   * @param title document title
   * @param tags document tags
   * @param published publication flag
   */
  public Document(String title, List<String> tags, Boolean published) {
    this.title = title;
    // Defensive copy to prevent external modification
    this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
    this.published = published;
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
   * Returns the document title.
   *
   * @return title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Sets the document title.
   *
   * @param title new title
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Returns the document tags.
   *
   * @return tag list
   */
  public List<String> getTags() {
    // Return unmodifiable view to prevent external modification
    return (tags != null) ? Collections.unmodifiableList(tags) : Collections.emptyList();
  }

  /**
   * Sets the document tags.
   *
   * @param tags new tags
   */
  public void setTags(List<String> tags) {
    // Defensive copy to prevent external modification
    this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
  }

  /**
   * Indicates whether the document is published.
   *
   * @return publication flag
   */
  public Boolean isPublished() {
    return published;
  }

  /**
   * Sets the published flag.
   *
   * @param published new state
   */
  public void setPublished(Boolean published) {
    this.published = published;
  }
}
