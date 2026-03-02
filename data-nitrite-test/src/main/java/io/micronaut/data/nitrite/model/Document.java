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
 * Document entity for testing.
 */
@MappedEntity("documents")
public class Document {
  @Id @GeneratedValue private String id;

  private String title;
  private List<String> tags;
  private Boolean published;

  public Document() { }

  public Document(String title, List<String> tags, Boolean published) {
    this.title = title;
    // Defensive copy to prevent external modification
    this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
    this.published = published;
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
   * @return the title
   */
  public String getTitle() {
    return title;
  }

  /**
   * @param title the title
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * @return the tags
   */
  public List<String> getTags() {
    // Return unmodifiable view to prevent external modification
    return (tags != null) ? Collections.unmodifiableList(tags) : Collections.emptyList();
  }

  /**
   * @param tags the tags
   */
  public void setTags(List<String> tags) {
    // Defensive copy to prevent external modification
    this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
  }

  /**
   * @return is published
   */
  public Boolean isPublished() {
    return published;
  }

  /**
   * @param published is published
   */
  public void setPublished(Boolean published) {
    this.published = published;
  }
}
