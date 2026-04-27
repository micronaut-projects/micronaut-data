package io.micronaut.data.processor.jq

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

class JakartaQueryBuilderSpec extends Specification {

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
                if (name == "Event") {
                    return buildEvent()
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
        def query = JQCriteriaBuilderUtils.build(
                q, root, null, classElementResolver, criteriaBuilder
        )
        return query.build(AnnotationMetadata.EMPTY_METADATA, queryBuilder).query
    }

    def 'test delete query'() {
        when:
            def result = transform(jq)
        then:
            result == sql
        where:
            jq << [
                    "DELETE FROM Box",
                    "DELETE FROM Coordinate WHERE x > 0.0d AND y > 0.0f",
                    "DELETE FROM Box b WHERE b.id = ?1"
            ]
            sql << [
                    """DELETE  FROM "box" """,
                    """DELETE  FROM "coordinate"  WHERE ("x" > 0 AND "y" > 0)""",
                    """DELETE  FROM "box"  WHERE ("id" = ?)"""
            ]
    }

    def 'test update query'() {
        when:
            def result = transform(jq)
        then:
            result == sql
        where:
            jq << [
                    "UPDATE Coordinate SET x = :newX, y = y / :yDivisor WHERE id = :id",
                    "UPDATE Box SET length = length + ?1, width = width - ?1, height = height * ?2",
                    "UPDATE Box SET name = NULL WHERE id = ?1",
                    "UPDATE Box b SET b.name = CONCAT(b.name, ' v2') WHERE b.id = ?1"
            ]
            sql << [
                    """UPDATE "coordinate" SET "x"=?,"y"=("y" / ?) WHERE ("id" = ?)""",
                    """UPDATE "box" SET "length"=("length" + ?),"width"=("width" - ?),"height"=("height" * ?)""",
                    """UPDATE "box" SET "name"=NULL WHERE ("id" = ?)""",
                    """UPDATE "box" SET "name"=CONCAT("name",' v2') WHERE ("id" = ?)"""
            ]
    }

    def 'test select'() {
        when:
            def result = transform(rootEntityName, jq)
        then:
            result == sql
        where:
            rootEntityName << ["Box", "AsciiCharacter", "NaturalNumber", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Event", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box", "Box"]
            jq << [
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
                    "WHERE ID(THIS) IN :names",
                    "WHERE ABS(length - ?1) > 10",
                    "WHERE LEFT(name, 2) = 'ab' AND RIGHT(name, 1) = 'z'",
                    "WHERE LOWER(name) = 'abc' OR UPPER(name) = 'ABC'",
                    "WHERE name LIKE 'A!_%' ESCAPE '!' AND name = 'Furry''s theorem'",
                    "WHERE eventDate < LOCAL DATE AND eventTime < LOCAL TIME AND eventDateTime < LOCAL DATETIME",
                    "SELECT COUNT(THIS) FROM Box WHERE length >= 1_000L",
                    "SELECT name FROM Box WHERE width <> height OR length <= 10 ORDER BY name DESC, ID(THIS)",
                    "WHERE name LIKE :pattern AND name NOT LIKE :excluded",
                    "SELECT DISTINCT b.name FROM Box b WHERE b.length > 0 ORDER BY b.name DESC",
                    "SELECT b.length + b.width AS footprint FROM Box b WHERE b.id = ?1",
                    "SELECT AVG(b.length), MAX(b.width), MIN(b.height), SUM(b.length), COUNT(DISTINCT b.name) FROM Box b WHERE b.height > 0",
                    "SELECT CONCAT(SUBSTRING(b.name, 1, 2), RIGHT(b.name, 1)) AS code FROM Box b WHERE MOD(b.length, 2) = 0 AND POWER(b.width, 2) > 10 AND SQRT(b.height) >= 3",
                    "SELECT AVG(DISTINCT b.length), SUM(DISTINCT b.width), MAX(DISTINCT b.height), MIN(DISTINCT b.height) FROM Box b WHERE b.height > 0",
                    "SELECT b.name FROM Box b WHERE b.name IS NOT NULL ORDER BY b.name ASC NULLS LAST, b.id DESC NULLS FIRST",
                    "FROM Box WHERE length BETWEEN :min AND :max"
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
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."id" IN (?))""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (ABS((box_."length" - ?)) > 10)""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (LEFT(box_."name",2) = 'ab' AND RIGHT(box_."name",1) = 'z')""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (LOWER(box_."name") = 'abc' OR UPPER(box_."name") = 'ABC')""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."name" LIKE 'A!_%' ESCAPE '!' AND box_."name" = 'Furry''s theorem')""",
                    """SELECT event_."id",event_."event_date",event_."event_time",event_."event_date_time" FROM "event" event_ WHERE (event_."event_date" < ? AND event_."event_time" < ? AND event_."event_date_time" < ?)""",
                    """SELECT COUNT(*) FROM "box" box_ WHERE (box_."length" >= 1000)""",
                    """SELECT box_."name" FROM "box" box_ WHERE (box_."width" != box_."height" OR box_."length" <= 10) ORDER BY box_."name" DESC,box_."id" ASC""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE (box_."name" LIKE ? AND box_."name" NOT LIKE ?)""",
                    """SELECT DISTINCT box_."name" FROM "box" box_ WHERE (box_."length" > 0) ORDER BY box_."name" DESC""",
                    """SELECT box_."length" + box_."width" AS footprint FROM "box" box_ WHERE (box_."id" = ?)""",
                    """SELECT AVG(box_."length"),MAX(box_."width"),MIN(box_."height"),SUM(box_."length"),COUNT(DISTINCT(box_."name")) FROM "box" box_ WHERE (box_."height" > 0)""",
                    """SELECT CONCAT(SUBSTRING(box_."name",1,2),RIGHT(box_."name",1)) AS code FROM "box" box_ WHERE (MOD(box_."length",2) = 0 AND POWER(box_."width",2) > 10 AND SQRT(box_."height") >= 3)""",
                    """SELECT AVG(DISTINCT(box_."length")),SUM(DISTINCT(box_."width")),MAX(DISTINCT(box_."height")),MIN(DISTINCT(box_."height")) FROM "box" box_ WHERE (box_."height" > 0)""",
                    """SELECT box_."name" FROM "box" box_ WHERE (box_."name" IS NOT NULL) ORDER BY box_."name" ASC NULLS LAST,box_."id" DESC NULLS FIRST""",
                    """SELECT box_."id",box_."name",box_."length",box_."width",box_."height" FROM "box" box_ WHERE ((box_."length" >= ? AND box_."length" <= ?))"""
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

    private static ClassElement buildEvent() {
        new CriteriaSpec.CustomAbstractDataSpec().buildClassElement("""
package test;

import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@jakarta.persistence.Entity
@Introspected(accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD}, visibility = Introspected.Visibility.ANY)
class Event {
    @jakarta.persistence.Id
    private long id;

    private LocalDate eventDate;

    private LocalTime eventTime;

    private LocalDateTime eventDateTime;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public LocalDateTime getEventDateTime() {
        return eventDateTime;
    }

    public void setEventDateTime(LocalDateTime eventDateTime) {
        this.eventDateTime = eventDateTime;
    }
}
""")
    }

}
