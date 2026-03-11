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

package exemple;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Entity
public class Client {

    @Id
    private Long id;

    private String name;

    @OneToMany
    private Collection<Category> categoriesCollection;

    @OneToMany
    private List<Category> categoriesList;

    @OneToMany
    private Set<Category> categoriesSet;

    @ManyToOne
    private Category mainCategory;

    /**
     *
     * @return
     */
    public Long getId() {
        return id;
    }

    /**
     *
     * @param id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     *
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return
     */
    public Collection<Category> getCategoriesCollection() {
        return categoriesCollection;
    }

    /**
     *
     * @param categoriesCollection
     */
    public void setCategoriesCollection(Collection<Category> categoriesCollection) {
        this.categoriesCollection = categoriesCollection;
    }

    /**
     *
     * @return
     */
    public List<Category> getCategoriesList() {
        return categoriesList;
    }

    /**
     *
     * @param categoriesList
     */
    public void setCategoriesList(List<Category> categoriesList) {
        this.categoriesList = categoriesList;
    }

    /**
     *
     * @return
     */
    public Set<Category> getCategoriesSet() {
        return categoriesSet;
    }

    /**
     *
     * @param categoriesSet
     */
    public void setCategoriesSet(Set<Category> categoriesSet) {
        this.categoriesSet = categoriesSet;
    }

    /**
     *
     * @return
     */
    public Category getMainCategory() {
        return mainCategory;
    }

    /**
     *
     * @param mainCategory
     */
    public void setMainCategory(Category mainCategory) {
        this.mainCategory = mainCategory;
    }
}
