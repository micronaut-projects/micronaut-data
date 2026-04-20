package io.micronaut.data.processor.jdql

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.jpa.criteria.CriteriaSpec
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaDelete
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl
import io.micronaut.inject.ast.ClassElement
import spock.lang.Specification

import java.util.function.Function

class JakartaDataQueryLanguageBuilderSpec extends Specification {

    SqlQueryBuilder queryBuilder = new SqlQueryBuilder(Dialect.POSTGRES)

    SourcePersistentEntityCriteriaBuilder criteriaBuilder

    SourcePersistentEntityCriteriaQuery criteriaQuery

    SourcePersistentEntityCriteriaDelete criteriaDelete

    SourcePersistentEntityCriteriaUpdate criteriaUpdate

    Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<ClassElement, SourcePersistentEntity>() {

        private Map<String, SourcePersistentEntity> entityMap = new HashMap<>()

        @Override
        SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), { it ->
                new SourcePersistentEntity(classElement, this)
            })
        }
    }

    Function<String, ClassElement> classElementResolver = new Function<String, ClassElement>() {

        private Map<String, ClassElement> cache = new HashMap<>()

        @Override
        ClassElement apply(String name) {
            return cache.computeIfAbsent(name, { it ->
                if (name == "Box") {
                    return buildBoxElement()
                }
                if (name == "Coordinate") {
                    return buildCoordinateElement()
                }
                if (name == "AsciiCharacter") {
                    return buildAsciiCharacter()
                }
                if (name == "NaturalNumber") {
                    return buildNaturalNumber()
                }
                throw new IllegalStateException("Unknown entity: " + name)
            })
        }
    }

    void setup() {
        criteriaBuilder = new SourcePersistentEntityCriteriaBuilderImpl(entityResolver)
        criteriaQuery = criteriaBuilder.createQuery()
        criteriaDelete = criteriaBuilder.createCriteriaDelete(null)
        criteriaUpdate = criteriaBuilder.createCriteriaUpdate(null)
    }

    String transform(String q) {
        return transform(null, q)
    }

    String transform(String rootEntityName, String q) {
        def root = new SourcePersistentEntity(classElementResolver.apply(rootEntityName ?: "Box"), (x) -> null)
        def query = JDQLCriteriaBuilderUtils.build(
                q, root, null, classElementResolver, criteriaBuilder
        )
        return query.build(AnnotationMetadata.EMPTY_METADATA, queryBuilder).query
    }

    def 'test delete query'() {
        when:
            def result = transform(jdql)
        then:
            result == sql
        where:
            jdql << [
                    "DELETE FROM Box",
                    "DELETE FROM Coordinate WHERE x > 0.0d AND y > 0.0f"
            ]
            sql << [
                    """DELETE  FROM "box" """,
                    """DELETE  FROM "coordinate"  WHERE ("x" > 0 AND "y" > 0)"""
            ]
    }

    def 'test update query'() {
        when:
            def result = transform(jdql)
        then:
            result == sql
        where:
            jdql << [
                    "UPDATE Coordinate SET x = :newX, y = y / :yDivisor WHERE id = :id",
                    "UPDATE Box SET length = length + ?1, width = width - ?1, height = height * ?2"
            ]
            sql << [
                    """UPDATE "coordinate" SET "x"=?,"y"=("y" / ?) WHERE ("id" = ?)""",
                    """UPDATE "box" SET "length"=("length" + ?),"width"=("width" - ?),"height"=("height" * ?)"""
            ]
    }

    def 'test select'() {
        when:
            def result = transform(rootEntityName, jdql)
        then:
            result == sql
        where:
            rootEntityName << ["Box", "AsciiCharacter", "NaturalNumber", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box"]
            jdql << [
                    "WHERE id = :id",
                    "select thisCharacter where hexadecimal like '4_' and hexadecimal not like '%0' and thisCharacter not in ('E', 'G') and id not between 72 and 78 order by id asc",
                    "WHERE isOdd = false AND numType = test.NaturalNumber.NumberType.PRIME",
                    "WHERE LENGTH(name) = ?1 AND length < ?2 ORDER BY name",
                    "SELECT ID(THIS) WHERE id = :id",
                    "SELECT id, name WHERE length > 10",
                    "WHERE ID(THIS) = :entityId",
                    "SELECT name ORDER BY ID(THIS) ASC",
                    "WHERE ID(THIS) IN (1,2)",
                    "WHERE ID(THIS) IS NULL",
                    "WHERE ID(THIS) > 10",
                    "WHERE ID(THIS) IN (:name1, :name1)",
                    "WHERE ID(THIS) IN :names"
            ]
            sql << [
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."id" = ?)""",
                    """SELECT ascii_character_."this_character" FROM "ascii_character" ascii_character_ WHERE (ascii_character_."hexadecimal" LIKE '4_' AND ascii_character_."hexadecimal" NOT LIKE '%0' AND ascii_character_."this_character" NOT IN ('E','G') AND NOT((ascii_character_."id" >= 72 AND ascii_character_."id" <= 78))) ORDER BY ascii_character_."id" ASC""",
                    """SELECT natural_number_."id",natural_number_."odd",natural_number_."num_bits_required",natural_number_."num_type",natural_number_."num_type_ordinal",natural_number_."floor_of_square_root",natural_number_."is_odd" FROM "natural_number" natural_number_ WHERE (natural_number_."is_odd" = FALSE AND natural_number_."num_type" = 'PRIME')""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (LENGTH(box_."name") = ? AND box_."length" < ?) ORDER BY box_."name" ASC""",
                    """SELECT box_."id" FROM "box" box_ WHERE (box_."id" = ?)""",
                    """SELECT box_."id" AS id,box_."name" AS name FROM "box" box_ WHERE (box_."length" > 10)""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."id" = ?)""",
                    """SELECT box_."name" FROM "box" box_ ORDER BY box_."id" ASC""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."id" IN (1,2))""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."id" IS NULL)""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."id" > 10)""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."id" IN (?,?))""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."id" IN (?))"""
            ]
    }

    private static ClassElement buildBoxElement() {
        new CriteriaSpec.CustomAbstractDataSpec().buildClassElement("""
package test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
class Box {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private long length;
    private long width;
    private long height;

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

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public long getWidth() {
        return width;
    }

    public void setWidth(long width) {
        this.width = width;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }
}

""")
    }

    private static ClassElement buildCoordinateElement() {
        new CriteriaSpec.CustomAbstractDataSpec().buildClassElement("""
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.UUID;

@Introspected(accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD}, visibility = Introspected.Visibility.ANY)
@jakarta.persistence.Entity
class Coordinate {
    @jakarta.persistence.Id
    public UUID id;

    public double x;

    public float y;

    public static Coordinate of(String id, double x, float y) {
        Coordinate c = new Coordinate();
        c.id = UUID.nameUUIDFromBytes(id.getBytes());
        c.x = x;
        c.y = y;
        return c;
    }

    @Override
    public String toString() {
        return "Coordinate@" + Integer.toHexString(hashCode()) + "(" + x + "," + y + ")" + ":" + id;
    }
}

""")
    }

    private static ClassElement buildAsciiCharacter() {
        new CriteriaSpec.CustomAbstractDataSpec().buildClassElement("""
package test;

import io.micronaut.core.annotation.Introspected;

import java.io.Serializable;

@jakarta.persistence.Entity
@Introspected(accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD}, visibility = Introspected.Visibility.ANY)
class AsciiCharacter implements Serializable {
    private static final long serialVersionUID = 1L;

    @jakarta.persistence.Id
    private long id;

    private int numericValue;

    private String hexadecimal;

    private char thisCharacter;

    private boolean isControl;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getNumericValue() {
        return numericValue;
    }

    public void setNumericValue(int numericValue) {
        this.numericValue = numericValue;
    }

    public String getHexadecimal() {
        return hexadecimal;
    }

    public void setHexadecimal(String hexadecimal) {
        this.hexadecimal = hexadecimal;
    }

    public char getThisCharacter() {
        return thisCharacter;
    }

    public void setThisCharacter(char thisCharacter) {
        this.thisCharacter = thisCharacter;
    }

    public boolean isControl() {
        return isControl;
    }

    public void setControl(boolean isControl) {
        this.isControl = isControl;
    }

}
""")
    }

    private static ClassElement buildNaturalNumber() {
        new CriteriaSpec.CustomAbstractDataSpec().buildClassElement("""
package test;

import io.micronaut.core.annotation.Introspected;

import java.io.Serializable;

@jakarta.persistence.Entity
@Introspected(accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD}, visibility = Introspected.Visibility.ANY)
class NaturalNumber implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum NumberType {
        ONE, PRIME, COMPOSITE
    }

    @jakarta.persistence.Id
    private long id; //AKA the value

    private boolean isOdd;

    private Short numBitsRequired;

    // Sorting on enum types is vendor-specific in Jakarta Data.
    // Use numTypeOrdinal for sorting instead.
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private NumberType numType; // enum of ONE | PRIME | COMPOSITE

    private int numTypeOrdinal; // ordinal value of numType

    private long floorOfSquareRoot;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isOdd() {
        return isOdd;
    }

    public void setOdd(boolean isOdd) {
        this.isOdd = isOdd;
    }

    public Short getNumBitsRequired() {
        return numBitsRequired;
    }

    public void setNumBitsRequired(Short numBitsRequired) {
        this.numBitsRequired = numBitsRequired;
    }

    public NumberType getNumType() {
        return numType;
    }

    public void setNumType(NumberType numType) {
        this.numType = numType;
    }

    public int getNumTypeOrdinal() {
        return numTypeOrdinal;
    }

    public void setNumTypeOrdinal(int value) {
        numTypeOrdinal = value;
    }

    public long getFloorOfSquareRoot() {
        return floorOfSquareRoot;
    }

    public void setFloorOfSquareRoot(long floorOfSquareRoot) {
        this.floorOfSquareRoot = floorOfSquareRoot;
    }
}
""")
    }

}
