package io.micronaut.data.jdbc.sqlite.many2one;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.JavaSQLiteDBProperties;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.ManyToOne;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.data.model.query.builder.sql.Dialect.ANSI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.many2one")
class MultiManyToOneJoinTest {

    @Inject
    RefARepository refARepository;

    @Inject
    CustomBookRepository customBookRepository;

    @Inject
    UserGroupMembershipRepository userGroupMembershipRepository;

    @Inject
    MyEntityRepository myEntityRepository;

    @Inject
    MyOtherRepository myOtherRepository;

    @Test
    void testManyToOneHierarchy() {
        RefC refC = new RefC();
        refC.setName("TestXyz");
        RefB refB = new RefB();
        refB.setRefC(refC);
        RefA refA = new RefA();
        refA.setRefB(refB);

        refARepository.save(refA);
        refA = refARepository.findById(refA.getId()).orElseThrow();

        assertNotNull(refA.getId());
        assertEquals("TestXyz", refA.getRefB().getRefC().getName());

        List<RefA> list = refARepository.queryAll(Pageable.from(0, 10));
        assertEquals(1, list.size());
        assertEquals("TestXyz", list.getFirst().getRefB().getRefC().getName());

        Page<RefA> page = refARepository.findAll(Pageable.from(0, 10));
        assertEquals(1, page.getContent().size());
        assertEquals("TestXyz", page.getContent().getFirst().getRefB().getRefC().getName());

        refARepository.update(refA);
        refA = refARepository.findById(refA.getId()).orElseThrow();

        assertNotNull(refA.getId());
        assertEquals("TestXyz", refA.getRefB().getRefC().getName());
    }

    @Test
    void testJoinViaNonIdentityJoinColumn() {
        BookType bookType = new BookType();
        bookType.setId(1L);
        bookType.setName("Fantasy");
        customBookRepository.insertBookType(bookType.getId(), bookType.getName());

        CustomAuthor customAuthor = new CustomAuthor();
        customAuthor.setName("author1");
        customAuthor.setId2(20L);

        CustomBook customBook = new CustomBook();
        customBook.setTitle("book1");
        customBook.setPages(100);
        customBook.setAuthor(customAuthor);
        customBook.setType(bookType);
        customBookRepository.save(customBook);

        List<CustomBook> books = customBookRepository.findAll();
        assertEquals(1, books.size());
        assertEquals(20L, books.getFirst().getAuthor().getId2());

        customBook = customBookRepository.findById(customBook.getId()).orElse(null);
        assertNotNull(customBook);
        assertNotNull(customBook.getType());
        assertEquals(1L, customBook.getType().getId());
        assertNull(customBook.getType().getName());

        customBook.setTitle("book1-updated");
        customBookRepository.update(customBook);
        customBook = customBookRepository.findById(customBook.getId()).orElse(null);

        assertNotNull(customBook);
        assertEquals("book1-updated", customBook.getTitle());

        customBookRepository.deleteAll();
        customBookRepository.deleteBookType(bookType.getId());
    }

    @Test
    void testManyToOneWithTwoPropertiesStartingWithSamePrefix() {
        User user = new User();
        user.setLogin("login1");
        Area area = new Area();
        area.setName("area51");
        UserGroup userGroup = new UserGroup();
        userGroup.setArea(area);
        UserGroupMembership membership = new UserGroupMembership();
        membership.setUser(user);
        membership.setUserGroup(userGroup);
        userGroup.getUserAuthorizations().add(membership);

        userGroupMembershipRepository.save(membership);
        List<UserGroupMembership> listByUserLogin = userGroupMembershipRepository.findAllByUserLogin(user.getLogin());
        List<UserGroupMembership> listByUserLoginAndAreaId = userGroupMembershipRepository.findAllByUserLoginAndUserGroup_AreaId(user.getLogin(), area.getId());

        assertFalse(listByUserLogin.isEmpty());
        assertFalse(listByUserLoginAndAreaId.isEmpty());
        assertEquals(listByUserLogin, listByUserLoginAndAreaId);
        assertEquals(userGroup.getId(), listByUserLogin.getFirst().getUserGroup().getId());
        assertEquals(user.getId(), listByUserLogin.getFirst().getUser().getId());
        assertEquals(userGroup.getId(), listByUserLoginAndAreaId.getFirst().getUserGroup().getId());
        assertEquals(user.getId(), listByUserLoginAndAreaId.getFirst().getUser().getId());
    }

    @Test
    void testManyToOneWithEntityHavingOnlyIdField() {
        MyEntity ent = new MyEntity(-1L, null);
        ent = myEntityRepository.save(ent);
        assertNotEquals(-1L, ent.getLid());

        Optional<MyEntity> optFound = myEntityRepository.findById(ent.getLid());
        assertTrue(optFound.isPresent());
        MyEntity myEntity = optFound.get();
        assertNull(myEntity.getOther());

        myEntityRepository.update(myEntity);

        MyOther myOther = new MyOther("foo");
        myOtherRepository.save(myOther);
        MyEntity newMyEntity = new MyEntity(-1L, myOther);
        newMyEntity = myEntityRepository.save(newMyEntity);
        optFound = myEntityRepository.findById(newMyEntity.getLid());

        assertTrue(optFound.isPresent());
        assertNotNull(optFound.get().getOther());
        assertEquals(myOther.getLid(), optFound.get().getOther().getLid());
    }
}

@JdbcRepository(dialect = ANSI)
interface RefARepository extends CrudRepository<RefA, Long> {

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    Page<RefA> findAll(Pageable pageable);

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    List<RefA> queryAll(Pageable pageable);

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<RefA> findById(Long id);
}

@MappedEntity
class RefA {
    @Id
    @GeneratedValue
    private Long id;
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    private RefB refB;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    RefB getRefB() { return refB; }
    void setRefB(RefB refB) { this.refB = refB; }
}

@MappedEntity
class RefB {
    @Id
    @GeneratedValue
    private Long id;
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    private RefC refC;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    RefC getRefC() { return refC; }
    void setRefC(RefC refC) { this.refC = refC; }
}

@MappedEntity
class RefC {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
}

@JdbcRepository(dialect = ANSI)
@Join("author")
interface CustomBookRepository extends CrudRepository<CustomBook, Long> {

    @Query("INSERT INTO custbooktype (id, name) VALUES (:id, :name)")
    void insertBookType(Long id, String name);

    @Query("DELETE FROM custbooktype WHERE id = :id")
    void deleteBookType(Long id);
}

@MappedEntity("custauthor1")
class CustomAuthor {
    @GeneratedValue
    @Id
    private Long id;
    private Long id2;
    private String name;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    Long getId2() { return id2; }
    void setId2(Long id2) { this.id2 = id2; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
}

@MappedEntity("custbooktype")
class BookType {
    @Id
    private Long id;
    private String name;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
}

@MappedEntity("custbook1")
class CustomBook {
    @GeneratedValue
    @Id
    private Long id;
    private String title;
    private int pages;
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    @JoinColumn(name = "author_id2", referencedColumnName = "id2")
    private CustomAuthor author;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private BookType type;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getTitle() { return title; }
    void setTitle(String title) { this.title = title; }
    int getPages() { return pages; }
    void setPages(int pages) { this.pages = pages; }
    CustomAuthor getAuthor() { return author; }
    void setAuthor(CustomAuthor author) { this.author = author; }
    BookType getType() { return type; }
    void setType(BookType type) { this.type = type; }
}

@MappedEntity(value = "ugm", alias = "ugm_")
class UserGroupMembership {
    @Id
    @GeneratedValue
    private Long id;
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    private UserGroup userGroup;
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    private User user;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    UserGroup getUserGroup() { return userGroup; }
    void setUserGroup(UserGroup userGroup) { this.userGroup = userGroup; }
    User getUser() { return user; }
    void setUser(User user) { this.user = user; }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UserGroupMembership other) {
            return Objects.equals(id, other.id);
        }
        return false;
    }
}

@MappedEntity(value = "ug", alias = "ug_")
class UserGroup {
    @Id
    @GeneratedValue
    private Long id;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "userGroup")
    private Set<UserGroupMembership> userAuthorizations = new HashSet<>();
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    private Area area;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    Set<UserGroupMembership> getUserAuthorizations() { return userAuthorizations; }
    void setUserAuthorizations(Set<UserGroupMembership> userAuthorizations) { this.userAuthorizations = userAuthorizations; }
    Area getArea() { return area; }
    void setArea(Area area) { this.area = area; }
}

@MappedEntity(value = "a", alias = "a_")
class Area {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
}

@MappedEntity(value = "u", alias = "u_")
class User {
    @Id
    @GeneratedValue
    private Long id;
    private String login;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getLogin() { return login; }
    void setLogin(String login) { this.login = login; }
}

@JdbcRepository(dialect = ANSI)
interface UserGroupMembershipRepository extends CrudRepository<UserGroupMembership, Long> {
    List<UserGroupMembership> findAllByUserLogin(String login);

    @Join(value = "userGroup.area", type = Join.Type.FETCH)
    List<UserGroupMembership> findAllByUserLoginAndUserGroup_AreaId(String login, Long uid);
}

@MappedEntity
class MyEntity {
    @Id
    @GeneratedValue
    private long lid;
    @ManyToOne
    private MyOther other;

    MyEntity() {
    }

    MyEntity(long lid, @Nullable MyOther other) {
        this.lid = lid;
        this.other = other;
    }

    long getLid() { return lid; }
    void setLid(long lid) { this.lid = lid; }
    @Nullable MyOther getOther() { return other; }
    void setOther(@Nullable MyOther other) { this.other = other; }
}

@MappedEntity
class MyOther {
    @Id
    private String lid;

    MyOther() {
    }

    MyOther(String lid) {
        this.lid = lid;
    }

    String getLid() { return lid; }
    void setLid(String lid) { this.lid = lid; }
}

@JdbcRepository(dialect = ANSI)
interface MyEntityRepository extends CrudRepository<MyEntity, Long> {
}

@JdbcRepository(dialect = ANSI)
interface MyOtherRepository extends CrudRepository<MyOther, String> {
}
