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

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.net.URL;
import java.time.Instant;
import java.util.Date;

@Entity
@javax.persistence.Entity
public class Company {

    @GeneratedValue
    @Id
    @javax.persistence.GeneratedValue
    @javax.persistence.Id
    private Long myId;
    @DateCreated
    private Date dateCreated;

    @DateUpdated
    private Instant lastUpdated;

    private String name;
    private URL url;

    @Creator
    public Company(String name, URL url) {
        this.name = name;
        this.url = url;
    }

    // only needed for hibernate
    public Company() {
    }

    public Long getMyId() {
        return myId;
    }

    public void setMyId(Long myId) {
        this.myId = myId;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "Company{" +
                "name='" + name + '\'' +
                '}';
    }
}
