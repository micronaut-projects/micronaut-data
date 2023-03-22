/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;

import jakarta.persistence.ManyToOne;

@Embeddable
public class UuidEmbeddedChildEntity {
    @ManyToOne
    @MappedProperty("xyz")
    private UuidChildEntity embeddedChild1;

    @ManyToOne
    private UuidChildEntity embeddedChild2;

    public UuidChildEntity getEmbeddedChild1() {
        return embeddedChild1;
    }

    public void setEmbeddedChild1(UuidChildEntity embeddedChild1) {
        this.embeddedChild1 = embeddedChild1;
    }

    public UuidChildEntity getEmbeddedChild2() {
        return embeddedChild2;
    }

    public void setEmbeddedChild2(UuidChildEntity embeddedChild2) {
        this.embeddedChild2 = embeddedChild2;
    }
}
