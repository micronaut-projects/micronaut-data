package io.micronaut.data.jdbc.sqlite.jakarta_data.persistence;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.data.Order;
import jakarta.data.repository.By;
import jakarta.data.repository.DataRepository;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Save;
import jakarta.data.repository.Update;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static jakarta.data.repository.By.ID;

@Repository
@JdbcRepository(dialect = Dialect.SQLITE)
public interface Catalog extends DataRepository<CatalogProduct, String> {

    @Insert
    CatalogProduct add(CatalogProduct product);

    @Insert
    CatalogProduct[] addMultiple(CatalogProduct... products);

    @Find
    Optional<CatalogProduct> get(String productNum);

    @Update
    CatalogProduct modify(CatalogProduct product);

    @Update
    CatalogProduct[] modifyMultiple(CatalogProduct... products);

    @Delete
    void remove(CatalogProduct product);

    @Delete
    void removeMultiple(CatalogProduct... products);

    @Save
    void customSave(CatalogProduct product);

    @Delete
    void deleteById(@By(ID) String productNum);

    long deleteByProductNumLike(String pattern);

    long countByPriceGreaterThanEqual(Double price);

    @Query("WHERE LENGTH(name) = ?1 AND price < ?2 ORDER BY name")
    List<CatalogProduct> findByNameLengthAndPriceBelow(int nameLength, double maxPrice);

    @OrderBy("name")
    @Query("WHERE LENGTH(name) = ?1 AND price < ?2")
    List<CatalogProduct> findByNameLengthAndPriceBelowNameAsc(int nameLength, double maxPrice);

    @OrderBy(value = "name", descending = true)
    @Query("WHERE LENGTH(name) = ?1 AND price < ?2")
    List<CatalogProduct> findByNameLengthAndPriceBelowNameDesc(int nameLength, double maxPrice);

    @Find
    @OrderBy(value = "name")
    List<CatalogProduct> allSortedByNameAsc();

    @Find
    @OrderBy(value = "name", descending = true)
    List<CatalogProduct> allSortedByNameDesc();

    @Find
    @OrderBy(value = "name", ignoreCase = true)
    List<CatalogProduct> allSortedByNameAscIgnoreCase();

    @Find
    @OrderBy(value = "name", descending = true, ignoreCase = true)
    List<CatalogProduct> allSortedByNameDescIgnoreCase();

    @Find
    @OrderBy(value = "name", descending = true, ignoreCase = true)
    List<CatalogProduct> findAll();

    List<CatalogProduct> findByNameLike(String name);

    @OrderBy(value = "price", descending = true)
    Stream<CatalogProduct> findByPriceNotNullAndPriceLessThanEqual(double maxPrice);

    List<CatalogProduct> findByPriceNull();

    List<CatalogProduct> findByProductNumBetween(String first, String last, Order<CatalogProduct> sorts);

    List<CatalogProduct> findByProductNumLike(String productNum);

//    EntityManager getEntityManager();
//
//    default double sumPrices(Department... departments) {
//        StringBuilder jpql = new StringBuilder("SELECT SUM(o.price) FROM Product o");
//        for (int d = 1; d <= departments.length; d++) {
//            jpql.append(d == 1 ? " WHERE " : " OR ");
//            jpql.append('?').append(d).append(" MEMBER OF o.departments");
//        }
//
//        EntityManager em = getEntityManager();
//        TypedQuery<Double> query = em.createQuery(jpql.toString(), Double.class);
//        for (int d = 1; d <= departments.length; d++) {
//            query.setParameter(d, departments[d - 1]);
//        }
//        return query.getSingleResult();
//    }

    @Query("FROM CatalogProduct WHERE (:rate * price <= :max AND :rate * price >= :min) ORDER BY name")
    Stream<CatalogProduct> withTaxBetween(@Param("min") double mininunTaxAmount,
                                          @Param("max") double maximumTaxAmount,
                                          @Param("rate") double taxRate);
}
