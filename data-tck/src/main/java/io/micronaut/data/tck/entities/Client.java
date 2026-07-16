/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.micronaut.data.tck.entities;


import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.Version;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@SuppressWarnings("checkstyle:DesignForExtension")
public class Client {

    @Id
    private Long id;

    private String name;

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    private Tier tier = Tier.BASIC;

    private Instant createdAt = Instant.now();

    @Embedded
    private Address billingAddress;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "client_categories_collection",
        joinColumns = @JoinColumn(name = "client_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Collection<ClientCategory> categoriesCollection = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "client_categories_list",
        joinColumns = @JoinColumn(name = "client_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<ClientCategory> categoriesList = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "client_categories_set",
        joinColumns = @JoinColumn(name = "client_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<ClientCategory> categoriesSet = new HashSet<>();

    @ManyToOne(cascade = CascadeType.ALL)
    private ClientCategory mainCategory;

    // It seems that This approach is not supported in JDBC , or am i missing something.
//    @ElementCollection
//    @CollectionTable(name = "client_properties", joinColumns = @JoinColumn(name = "client_id"))
//    @MapKeyColumn(name = "prop_key")
//    @Column(name = "prop_value")
//    private Map<String, String> properties = new HashMap<>();

    @Transient
    private String nonPersistent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Tier getTier() {
        return tier;
    }

    public void setTier(Tier tier) {
        this.tier = tier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Address getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(Address billingAddress) {
        this.billingAddress = billingAddress;
    }

    public Collection<ClientCategory> getCategoriesCollection() {
        return categoriesCollection;
    }

    public void setCategoriesCollection(Collection<ClientCategory> categoriesCollection) {
        this.categoriesCollection = categoriesCollection;
    }

    public List<ClientCategory> getCategoriesList() {
        return categoriesList;
    }

    public void setCategoriesList(List<ClientCategory> categoriesList) {
        this.categoriesList = categoriesList;
    }

    public Set<ClientCategory> getCategoriesSet() {
        return categoriesSet;
    }

    public void setCategoriesSet(Set<ClientCategory> categoriesSet) {
        this.categoriesSet = categoriesSet;
    }

    public ClientCategory getMainCategory() {
        return mainCategory;
    }

    public void setMainCategory(ClientCategory mainCategory) {
        this.mainCategory = mainCategory;
    }
//
//    public Map<String, String> getProperties() {
//        return properties;
//    }
//
//    public void setProperties(Map<String, String> properties) {
//        this.properties = properties;
//    }

    public String getNonPersistent() {
        return nonPersistent;
    }

    public void setNonPersistent(String nonPersistent) {
        this.nonPersistent = nonPersistent;
    }

    public enum Tier {
        BASIC, PRO, ENTERPRISE
    }

    @Embeddable
    public static class Address {
        private String street;
        private String city;

        public Address(String street, String city) {
            this.street = street;
            this.city = city;
        }

        public Address() {
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }
}
