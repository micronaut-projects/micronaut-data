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

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
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

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Long getId() {
        return id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setId(Long id) {
        this.id = id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public String getName() {
        return name;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setName(String name) {
        this.name = name;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public long getVersion() {
        return version;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setVersion(long version) {
        this.version = version;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Tier getTier() {
        return tier;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setTier(Tier tier) {
        this.tier = tier;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Instant getCreatedAt() {
        return createdAt;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Address getBillingAddress() {
        return billingAddress;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setBillingAddress(Address billingAddress) {
        this.billingAddress = billingAddress;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Collection<ClientCategory> getCategoriesCollection() {
        return categoriesCollection;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setCategoriesCollection(Collection<ClientCategory> categoriesCollection) {
        this.categoriesCollection = categoriesCollection;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public List<ClientCategory> getCategoriesList() {
        return categoriesList;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setCategoriesList(List<ClientCategory> categoriesList) {
        this.categoriesList = categoriesList;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Set<ClientCategory> getCategoriesSet() {
        return categoriesSet;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setCategoriesSet(Set<ClientCategory> categoriesSet) {
        this.categoriesSet = categoriesSet;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public ClientCategory getMainCategory() {
        return mainCategory;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setMainCategory(ClientCategory mainCategory) {
        this.mainCategory = mainCategory;
    }
//
//    @SuppressWarnings("checkstyle:DesignForExtension")
//    public Map<String, String> getProperties() {
//        return properties;
//    }
//
//    @SuppressWarnings("checkstyle:DesignForExtension")
//    public void setProperties(Map<String, String> properties) {
//        this.properties = properties;
//    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public String getNonPersistent() {
        return nonPersistent;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
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

        @SuppressWarnings("checkstyle:DesignForExtension")
        public String getStreet() {
            return street;
        }

        @SuppressWarnings("checkstyle:DesignForExtension")
        public void setStreet(String street) {
            this.street = street;
        }

        @SuppressWarnings("checkstyle:DesignForExtension")
        public String getCity() {
            return city;
        }

        @SuppressWarnings("checkstyle:DesignForExtension")
        public void setCity(String city) {
            this.city = city;
        }
    }
}
