package example.metamodel;

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

    public Collection<Category> getCategoriesCollection() {
        return categoriesCollection;
    }

    public void setCategoriesCollection(Collection<Category> categoriesCollection) {
        this.categoriesCollection = categoriesCollection;
    }

    public List<Category> getCategoriesList() {
        return categoriesList;
    }

    public void setCategoriesList(List<Category> categoriesList) {
        this.categoriesList = categoriesList;
    }

    public Set<Category> getCategoriesSet() {
        return categoriesSet;
    }

    public void setCategoriesSet(Set<Category> categoriesSet) {
        this.categoriesSet = categoriesSet;
    }

    public Category getMainCategory() {
        return mainCategory;
    }

    public void setMainCategory(Category mainCategory) {
        this.mainCategory = mainCategory;
    }
}
