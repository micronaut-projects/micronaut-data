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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@MappedEntity("nitrite_document")
public class NitriteDocument {

    @Id
    @GeneratedValue
    @Nullable
    private String id;

    @Nullable
    private String title;

    @Nullable
    private List<String> tags;

    @Nullable
    private Map<String, NitriteDocumentOwner> owners;

    @Nullable
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Nullable
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    @Nullable
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    @Nullable
    public Map<String, NitriteDocumentOwner> getOwners() { return owners; }
    public void setOwners(Map<String, NitriteDocumentOwner> owners) { this.owners = owners; }
}
