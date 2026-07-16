package io.micronaut.data.jdbc.sqlite.jakarta_data.entity;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.AsciiCharacter;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.AsciiCharacters;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.AsciiCharactersPopulator;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.CustomRepository;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.NaturalNumber;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.NaturalNumber.NumberType;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.NaturalNumbers;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.NaturalNumbersPopulator;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.PositiveIntegers;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only._AsciiChar;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only._AsciiCharacter;
import io.micronaut.data.jdbc.sqlite.jakarta_data.utilities.DatabaseType;
import io.micronaut.data.jdbc.sqlite.jakarta_data.utilities.TestProperty;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.exceptions.EmptyResultException;
import jakarta.data.exceptions.NonUniqueResultException;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.page.PageRequest.Cursor;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.NaturalNumber.NumberType.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Execute a test with an entity that is dual annotated which means this test
 * can run against a provider that supports any Entity type.
 */
@Property(name = "datasources.default.allowConnectionPerOperation", value = "true")
@SQLiteDBProperties
@MicronautTest(transactional = false)
public class EntityTests {

    public static final Logger log = Logger.getLogger(EntityTests.class.getCanonicalName());

    @Inject
    Boxes boxes;

    @Inject
    NaturalNumbers numbers;

    @Inject
    PositiveIntegers positives; // shares same read-only data with NaturalNumbers

    @Inject
    CustomRepository customRepo; // shares same read-only data with NaturalNumbers

    @Inject
    AsciiCharacters characters;

    @Inject
    MultipleEntityRepo shared;

    @BeforeEach
    //Inject doesn't happen until after BeforeClass so this is necessary before each test
    public void setup() {
        assertNotNull(numbers);
        NaturalNumbersPopulator.get().populate(numbers);

        assertNotNull(characters);
        AsciiCharactersPopulator.get().populate(characters);
    }

    private DatabaseType type = TestProperty.databaseType.getDatabaseType();

    @Test
    public void ensureNaturalNumberPrepopulation() {
        assertEquals(100L, numbers.countAll());
        assertTrue(numbers.findById(0L).isEmpty(), "Zero should not have been in the set of natural numbers.");
        assertFalse(numbers.findById(10L).get().isOdd());
    }

    @Test
    public void ensureCharacterPrepopulation() {
        try {
            assertEquals(127L, characters.countByHexadecimalNotNull());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of the Null comparison
            } else {
                throw x;
            }
        }

        assertEquals('0', characters.findByNumericValue(48).get().getThisCharacter());
        assertTrue(characters.findByNumericValue(1).get().isControl());
    }

    @Test
    public void testBasicRepository() {

        // custom method from NaturalNumbers:
        try {
            Stream<NaturalNumber> found = numbers.findByIdBetweenOrderByNumTypeOrdinalAsc(
                50L, 59L,
                Order.by(Sort.asc("id")));
            List<Long> list = found
                .map(NaturalNumber::getId)
                .collect(Collectors.toList());
            assertEquals(List.of(53L, 59L, // first 2 must be primes
                    50L, 51L, 52L, 54L, 55L, 56L, 57L, 58L), // the remaining 8 are composite numbers
                list);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of Between.
            } else {
                throw x;
            }
        }

        // built-in method from BasicRepository:
        assertEquals(60L, numbers.findById(60L).orElseThrow().getId());
    }

    @Disabled // Pending feature
    @Test
    public void testBasicRepositoryBuiltInMethods() {

        // BasicRepository.saveAll
        Iterable<Box> saved = boxes.saveAll(List.of(Box.of("TestBasicRepositoryMethods-01", 119, 120, 169),
            Box.of("TestBasicRepositoryMethods-02", 20, 21, 29),
            Box.of("TestBasicRepositoryMethods-03", 33, 56, 65),
            Box.of("TestBasicRepositoryMethods-04", 45, 28, 53)));
        Iterator<Box> savedIt = saved.iterator();
        assertEquals(true, savedIt.hasNext());
        Box box1 = savedIt.next();
        assertEquals("TestBasicRepositoryMethods-01", box1.boxIdentifier);
        assertEquals(119, box1.length);
        assertEquals(120, box1.width);
        assertEquals(169, box1.height);
        assertEquals(true, savedIt.hasNext());
        Box box2 = savedIt.next();
        assertEquals("TestBasicRepositoryMethods-02", box2.boxIdentifier);
        assertEquals(20, box2.length);
        assertEquals(21, box2.width);
        assertEquals(29, box2.height);
        assertEquals(true, savedIt.hasNext());
        Box box3 = savedIt.next();
        assertEquals("TestBasicRepositoryMethods-03", box3.boxIdentifier);
        assertEquals(33, box3.length);
        assertEquals(56, box3.width);
        assertEquals(65, box3.height);
        assertEquals(true, savedIt.hasNext());
        Box box4 = savedIt.next();
        assertEquals("TestBasicRepositoryMethods-04", box4.boxIdentifier);
        assertEquals(45, box4.length);
        assertEquals(28, box4.width);
        assertEquals(53, box4.height);
        assertEquals(false, savedIt.hasNext());


        // BasicRepository.save
        box2.length = 21;
        box2.width = 20;
        box2 = boxes.save(box2);
        assertEquals("TestBasicRepositoryMethods-02", box2.boxIdentifier);
        assertEquals(21, box2.length);
        assertEquals(20, box2.width);
        assertEquals(29, box2.height);

        Box box5 = boxes.save(Box.of("TestBasicRepositoryMethods-05", 153, 104, 185));
        assertEquals("TestBasicRepositoryMethods-05", box5.boxIdentifier);
        assertEquals(153, box5.length);
        assertEquals(104, box5.width);
        assertEquals(185, box5.height);



        // BasicRepository.deleteAll(Iterable)
        boxes.deleteAll(List.of(box1, box2));



        assertEquals(3, boxes.findAll().count());


        // BasicRepository.delete
        boxes.delete(box4);



        // BasicRepository.findAll
        Stream<Box> stream = boxes.findAll();
        List<Box> list = stream.sorted(Comparator.comparing(b -> b.boxIdentifier)).collect(Collectors.toList());
        assertEquals(2, list.size());
        box4 = list.get(0);
        assertEquals("TestBasicRepositoryMethods-03", box3.boxIdentifier);
        assertEquals(33, box3.length);
        assertEquals(56, box3.width);
        assertEquals(65, box3.height);
        box5 = list.get(1);
        assertEquals("TestBasicRepositoryMethods-05", box5.boxIdentifier);
        assertEquals(153, box5.length);
        assertEquals(104, box5.width);
        assertEquals(185, box5.height);

        // BasicRepository.deleteById
        boxes.deleteById("TestBasicRepositoryMethods-03");



        // BasicRepository.findById
        assertEquals(false, boxes.findById("TestBasicRepositoryMethods-03").isPresent());
        box5 = boxes.findById("TestBasicRepositoryMethods-05").orElseThrow();
        assertEquals("TestBasicRepositoryMethods-05", box5.boxIdentifier);
        assertEquals(153, box5.length);
        assertEquals(104, box5.width);
        assertEquals(185, box5.height);

        // BasicRepository.deleteById
        boxes.deleteById("TestBasicRepositoryMethods-05");


        assertEquals(0, boxes.findAll().count());
    }

    @Disabled // Pending feature
    @Test
    public void testBasicRepositoryMethods() {

        // BasicRepository.saveAll
        Iterable<Box> saved = boxes.saveAll(List.of(Box.of("TestBasicRepositoryMethods-01", 119, 120, 169),
            Box.of("TestBasicRepositoryMethods-02", 20, 21, 29),
            Box.of("TestBasicRepositoryMethods-03", 33, 56, 65),
            Box.of("TestBasicRepositoryMethods-04", 45, 28, 53)));
        Iterator<Box> savedIt = saved.iterator();
        assertEquals(true, savedIt.hasNext());
        Box box1 = savedIt.next();
        assertEquals("TestBasicRepositoryMethods-01", box1.boxIdentifier);
        assertEquals(119, box1.length);
        assertEquals(120, box1.width);
        assertEquals(169, box1.height);
        assertEquals(true, savedIt.hasNext());
        Box box2 = savedIt.next();
        assertEquals("TestBasicRepositoryMethods-02", box2.boxIdentifier);
        assertEquals(20, box2.length);
        assertEquals(21, box2.width);
        assertEquals(29, box2.height);
        assertEquals(true, savedIt.hasNext());
        Box box3 = savedIt.next();
        assertEquals("TestBasicRepositoryMethods-03", box3.boxIdentifier);
        assertEquals(33, box3.length);
        assertEquals(56, box3.width);
        assertEquals(65, box3.height);
        assertEquals(true, savedIt.hasNext());
        Box box4 = savedIt.next();
        assertEquals("TestBasicRepositoryMethods-04", box4.boxIdentifier);
        assertEquals(45, box4.length);
        assertEquals(28, box4.width);
        assertEquals(53, box4.height);
        assertEquals(false, savedIt.hasNext());




        // BasicRepository.save
        box2.length = 21;
        box2.width = 20;
        box2 = boxes.save(box2);
        assertEquals("TestBasicRepositoryMethods-02", box2.boxIdentifier);
        assertEquals(21, box2.length);
        assertEquals(20, box2.width);
        assertEquals(29, box2.height);

        Box box5 = boxes.save(Box.of("TestBasicRepositoryMethods-05", 153, 104, 185));
        assertEquals("TestBasicRepositoryMethods-05", box5.boxIdentifier);
        assertEquals(153, box5.length);
        assertEquals(104, box5.width);
        assertEquals(185, box5.height);



        // BasicRepository.deleteAll(Iterable)
        boxes.deleteAll(List.of(box1, box2));



        assertEquals(3, boxes.findAll().count());


        // BasicRepository.delete
        boxes.delete(box4);



        // BasicRepository.findAll
        Stream<Box> stream = boxes.findAll();
        List<Box> list = stream.sorted(Comparator.comparing(b -> b.boxIdentifier)).collect(Collectors.toList());
        assertEquals(2, list.size());
        box4 = list.get(0);
        assertEquals("TestBasicRepositoryMethods-03", box3.boxIdentifier);
        assertEquals(33, box3.length);
        assertEquals(56, box3.width);
        assertEquals(65, box3.height);
        box5 = list.get(1);
        assertEquals("TestBasicRepositoryMethods-05", box5.boxIdentifier);
        assertEquals(153, box5.length);
        assertEquals(104, box5.width);
        assertEquals(185, box5.height);

        // BasicRepository.deleteById
        boxes.deleteById("TestBasicRepositoryMethods-03");



        // BasicRepository.findById
        assertEquals(false, boxes.findById("TestBasicRepositoryMethods-03").isPresent());
        box5 = boxes.findById("TestBasicRepositoryMethods-05").orElseThrow();
        assertEquals("TestBasicRepositoryMethods-05", box5.boxIdentifier);
        assertEquals(153, box5.length);
        assertEquals(104, box5.width);
        assertEquals(185, box5.height);

        // BasicRepository.deleteById
        boxes.deleteById("TestBasicRepositoryMethods-05");



        assertEquals(0, boxes.findAll().count());
    }

    @Test
    public void testBeyondFinalPage() {
        PageRequest sixth = PageRequest.ofPage(6).size(10);
        Page<AsciiCharacter> page;
        try {
            page = characters.findByNumericValueBetween(48, 90, sixth, Order.by(_AsciiCharacter.numericValue.asc()));
        } catch (UnsupportedOperationException x) {
            // Some NoSQL databases lack the ability to count the total results
            // and therefore cannot support a return type of Page.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of Between.
            return;
        }
        assertEquals(0, page.numberOfElements());
        assertEquals(0, page.stream().count());
        assertEquals(false, page.hasContent());
        assertEquals(false, page.iterator().hasNext());
        try {
            assertEquals(43L, page.totalElements());
            assertEquals(5L, page.totalPages());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // Some NoSQL databases lack the ability to count the total results
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testBeyondFinalSlice() {
        PageRequest sixth = PageRequest.ofPage(6).size(5).withoutTotal();
        Page<NaturalNumber> page;
        try {
            page = numbers.findByNumTypeAndFloorOfSquareRootLessThanEqual(
                NumberType.PRIME,
                8L,
                sixth,
                Sort.desc("id"));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of LessThanEqual.
                return;
            } else {
                throw x;
            }
        }
        assertEquals(0, page.numberOfElements());
        assertEquals(0, page.stream().count());
        assertEquals(false, page.hasContent());
        assertEquals(false, page.iterator().hasNext());
    }

    @Test
    public void testBy() {
        AsciiCharacter ch = characters.find('L', "4c").orElseThrow();
        assertEquals('L', ch.getThisCharacter());
        assertEquals("4c", ch.getHexadecimal());
        assertEquals(76L, ch.getId());
        assertEquals(false, ch.isControl());

        assertEquals(true, characters.find('M', "4b").isEmpty());
    }

    @Test
    public void testCommonInterfaceQueries() {

        try {
            assertEquals(4L, numbers.countByIdBetween(87L, 90L));

            assertEquals(5L, characters.countByIdBetween(86L, 90L));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                // Key-Value databases are not capable of Between
            } else {
                throw x;
            }
        }

        assertEquals(true, numbers.existsById(73L));

        assertEquals(true, characters.existsById(74L));

        assertEquals(false, numbers.existsById(-1L));

        assertEquals(false, characters.existsById(-2L));

        try {
            assertEquals(
                List.of(68L, 69L, 70L, 71L, 72L),
                characters.withIdEqualOrAbove(68L, Limit.of(5)));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                return; // Key-Value databases are not capable of >= in JDQL
            } else {
                throw x;
            }
        }

        assertEquals(List.of(71L, 72L, 73L, 74L, 75L),
            numbers.withIdEqualOrAbove(71L, Limit.of(5)));
    }

    @Test
    public void testContainsInString() {
        Collection<AsciiCharacter> found;
        try {
            found = characters.findByHexadecimalContainsAndIsControlNot("4", true);
        } catch (UnsupportedOperationException e) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of Contains.
                // Key-Value databases might not be capable of And.
                return;
            } else {
                throw e;
            }
        }

        assertEquals(List.of("24", "34",
                "40", "41", "42", "43",
                "44", "45", "46", "47",
                "48", "49", "4a", "4b",
                "4c", "4d", "4e", "4f",
                "54", "64", "74"),
            found.stream().map(AsciiCharacter::getHexadecimal).sorted().toList());
    }

    @Test
    public void testDataRepository() {
        try {
            AsciiCharacter del = characters.findByIsControlTrueAndNumericValueBetween(33, 127);
            assertEquals(127, del.getNumericValue());
            assertEquals("7f", del.getHexadecimal());
            assertEquals(true, del.isControl());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of Between.
                // Key-Value databases might not be capable of True/False comparison.
            } else {
                throw x;
            }
        }

        try {
            AsciiCharacter j = characters.findByHexadecimalIgnoreCase("6A");
            assertEquals("6a", j.getHexadecimal());
            assertEquals('j', j.getThisCharacter());
            assertEquals(106, j.getNumericValue());
            assertEquals(false, j.isControl());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of IgnoreCase
            } else {
                throw x;
            }
        }

        AsciiCharacter d = characters.findByNumericValue(100).orElseThrow();
        assertEquals(100, d.getNumericValue());
        assertEquals('d', d.getThisCharacter());
        assertEquals("64", d.getHexadecimal());
        assertEquals(false, d.isControl());

        assertEquals(true, characters.existsByThisCharacter('D'));
    }

    @Test
    public void testDefaultMethod() {
        try {
            assertEquals(List.of('W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd'),
                characters.retrieveAlphaNumericIn(87L, 100L)
                    .map(AsciiCharacter::getThisCharacter)
                    .collect(Collectors.toList()));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                return; // Key-Value databases might not be capable of Between
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testDescendingSort() {
        Stream<AsciiCharacter> stream;
        try {
            stream = characters.findByIdBetween(
                52L, 57L,
                Sort.desc("id"));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of Between.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(Arrays.toString(new Character[]{'9', '8', '7', '6', '5', '4'}),
            Arrays.toString(stream.map(AsciiCharacter::getThisCharacter).toArray()));
    }

    @Test
    public void testEmptyQuery() {

        try {
            assertEquals(List.of('a', 'b', 'c', 'd', 'e', 'f'),
                characters.all(Limit.range(97, 102), Sort.asc("id"))
                    .map(AsciiCharacter::getThisCharacter)
                    .collect(Collectors.toList()));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                return;
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testEmptyResultException() {
        try {
            AsciiCharacter ch = characters.findByHexadecimalIgnoreCase("2g");
            fail("Unexpected result of findByHexadecimalIgnoreCase(2g): " + ch.getHexadecimal());
        } catch (EmptyResultException x) {
            log.info("testEmptyResultException expected to catch exception " + x + ". Printing its stack trace:");
            x.printStackTrace(System.out);
            // test passes
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                return; // NoSQL databases might not be capable of IgnoreCase
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testFalse() {
        List<NaturalNumber> even;
        try {
            even = positives.findByIsOddFalseAndIdBetween(50L, 60L);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of Between.
                // Key-Value databases might not be capable of True/False comparison.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(6L, even.stream().count());

        assertEquals(List.of(50L, 52L, 54L, 56L, 58L, 60L),
            even.stream().map(NaturalNumber::getId).sorted().collect(Collectors.toList()));
    }

    @Test
    public void testFinalPageOfUpTo10() {
        PageRequest fifthPageRequest = PageRequest.ofPage(5).size(10);
        Page<AsciiCharacter> page;
        try {
            page = characters.findByNumericValueBetween(48, 90, fifthPageRequest,
                Order.by(_AsciiCharacter.numericValue.asc())); // 'X' to 'Z'
        } catch (UnsupportedOperationException x) {
            // Some NoSQL databases lack the ability to count the total results
            // and therefore cannot support a return type of Page.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of Between.
            return;
        }

        Iterator<AsciiCharacter> it = page.iterator();

        // first result
        assertEquals(true, it.hasNext());
        AsciiCharacter ch = it.next();
        assertEquals('X', ch.getThisCharacter());
        assertEquals("58", ch.getHexadecimal());
        assertEquals(88L, ch.getId());
        assertEquals(88, ch.getNumericValue());
        assertEquals(false, ch.isControl());

        // second result
        ch = it.next();
        assertEquals('Y', ch.getThisCharacter());
        assertEquals("59", ch.getHexadecimal());
        assertEquals(89L, ch.getId());
        assertEquals(89, ch.getNumericValue());
        assertEquals(false, ch.isControl());

        // third result
        ch = it.next();
        assertEquals('Z', ch.getThisCharacter());
        assertEquals("5a", ch.getHexadecimal());
        assertEquals(90L, ch.getId());
        assertEquals(90, ch.getNumericValue());
        assertEquals(false, ch.isControl());

        assertEquals(false, it.hasNext());

        assertEquals(5, page.pageRequest().page());
        assertEquals(true, page.hasContent());
        assertEquals(3, page.numberOfElements());
        try {
            assertEquals(43L, page.totalElements());
            assertEquals(5L, page.totalPages());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // Some NoSQL databases lack the ability to count the total results
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testFinalSliceOfUpTo5() {
        PageRequest fifth = PageRequest.ofPage(5).size(5).withoutTotal();
        Page<NaturalNumber> page;
        try {
            page = numbers.findByNumTypeAndFloorOfSquareRootLessThanEqual(
                PRIME,
                8L,
                fifth,
                Sort.desc("id"));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of LessThanEqual.
                return;
            } else {
                throw x;
            }
        }
        assertEquals(true, page.hasContent());
        assertEquals(5, page.pageRequest().page());
        assertEquals(2, page.numberOfElements());

        Iterator<NaturalNumber> it = page.iterator();

        // first result
        assertEquals(true, it.hasNext());
        NaturalNumber number = it.next();
        assertEquals(3L, number.getId());
        assertEquals(NumberType.PRIME, number.getNumType());
        assertEquals(1L, number.getFloorOfSquareRoot());
        assertEquals(true, number.isOdd());
        assertEquals(Short.valueOf((short) 2), number.getNumBitsRequired());

        // second result
        assertEquals(true, it.hasNext());
        number = it.next();
        assertEquals(2L, number.getId());
        assertEquals(NumberType.PRIME, number.getNumType());
        assertEquals(1L, number.getFloorOfSquareRoot());
        assertEquals(false, number.isOdd());
        assertEquals(Short.valueOf((short) 2), number.getNumBitsRequired());

        assertEquals(false, it.hasNext());
    }

    @Test
    public void testFindAllWithPagination() {
        PageRequest page2request = PageRequest.ofPage(2).size(12);
        Page<NaturalNumber> page2;
        try {
            page2 = positives.findAll(page2request,
                Order.by(
                    Sort.asc("floorOfSquareRoot"),
                    Sort.desc("id")));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(12, page2.numberOfElements());
        assertEquals(2, page2.pageRequest().page());

        assertEquals(List.of(11L, 10L, 9L, // square root rounds down to 3
                24L, 23L, 22L, 21L, 20L, 19L, 18L, 17L, 16L), // square root rounds down to 4
            page2.stream().map(n -> n.getId()).collect(Collectors.toList()));
    }

    @Test
    public void testFindFirst() {
        Optional<AsciiCharacter> none;
        try {
            none = characters.findFirstByHexadecimalStartsWithAndIsControlOrderByIdAsc(
                "h", false);
        } catch (UnsupportedOperationException e) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of StartsWith.
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                return;
            } else {
                throw e;
            }
        }
        assertEquals(true, none.isEmpty());

        AsciiCharacter ch = characters.findFirstByHexadecimalStartsWithAndIsControlOrderByIdAsc("4", false)
            .orElseThrow();
        assertEquals('@', ch.getThisCharacter());
        assertEquals("40", ch.getHexadecimal());
        assertEquals(64, ch.getNumericValue());
    }

    @Test
    public void testFindFirst3() {
        AsciiCharacter[] found;

        try {
            found = characters.findFirst3ByNumericValueGreaterThanEqualAndHexadecimalEndsWith(
                40, "4", Sort.asc("numericValue"));
        } catch (UnsupportedOperationException e) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of EndsWith.
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                return;
            } else {
                throw e;
            }
        }

        assertEquals(3, found.length);
        assertEquals('4', found[0].getThisCharacter());
        assertEquals('D', found[1].getThisCharacter());
        assertEquals('T', found[2].getThisCharacter());
    }

    @Test
    public void testFindList() {
        List<NaturalNumber> oddCompositeNumbers;
        try {
            oddCompositeNumbers = positives.findOdd(
                true,
                NumberType.COMPOSITE,
                Limit.of(10),
                Order.by(
                    Sort.asc("floorOfSquareRoot"),
                    Sort.desc("numBitsRequired"),
                    Sort.asc("id")));


            assertEquals(List.of(9L, 15L,  // 3 <= sqrt < 4, 4 bits
                    21L,      // 4 <= sqrt < 5, 5 bits
                    33L, 35L, // 5 <= sqrt < 6, 6 bits
                    25L, 27L, // 5 <= sqrt < 6, 5 bits
                    39L, 45L, // 6 <= sqrt < 7, 6 bits
                    49L),     // 7 <= sqrt < 8, 6 bits
                oddCompositeNumbers
                    .stream()
                    .map(NaturalNumber::getId)
                    .collect(Collectors.toList()));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
            } else {
                throw x;
            }
        }

        List<NaturalNumber> evenPrimeNumbers = positives.findOdd(false, NumberType.PRIME, Limit.of(9), Order.by());

        assertEquals(1, evenPrimeNumbers.size());
        NaturalNumber num = evenPrimeNumbers.get(0);
        assertEquals(2L, num.getId());
        assertEquals(1L, num.getFloorOfSquareRoot());
        assertEquals(Short.valueOf((short) 2), num.getNumBitsRequired());
        assertEquals(NumberType.PRIME, num.getNumType());
        assertEquals(false, num.isOdd());
    }

    @Test
    public void testFindOne() {
        AsciiCharacter j = characters.find('j');

        assertEquals("6a", j.getHexadecimal());
        assertEquals(106L, j.getId());
        assertEquals(106, j.getNumericValue());
        assertEquals('j', j.getThisCharacter());
    }

    @Test
    public void testFindOptional() {
        NaturalNumber num = positives.findNumber(67L).orElseThrow();

        assertEquals(67L, num.getId());
        assertEquals(8L, num.getFloorOfSquareRoot());
        assertEquals(Short.valueOf((short) 7), num.getNumBitsRequired());
        assertEquals(NumberType.PRIME, num.getNumType());
        assertEquals(true, num.isOdd());

        Optional<NaturalNumber> opt = positives.findNumber(-40L);

        assertEquals(false, opt.isPresent());
    }

    @Test
    public void testFindPage() {
        PageRequest page1Request = PageRequest.ofSize(7);

        Page<NaturalNumber> page1;
        try {
            page1 = positives.findMatching(
                9L,
                Short.valueOf((short) 7),
                NumberType.COMPOSITE,
                page1Request,
                Sort.desc("id"));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(List.of(99L, 98L, 96L, 95L, 94L, 93L, 92L),
            page1.stream().map(NaturalNumber::getId).collect(Collectors.toList()));

        assertEquals(true, page1.hasNext());

        Page<NaturalNumber> page2 = positives.findMatching(9L, Short.valueOf((short) 7), NumberType.COMPOSITE,
            page1.nextPageRequest(), Sort.desc("id"));

        assertEquals(List.of(91L, 90L, 88L, 87L, 86L, 85L, 84L),
            page2.stream().map(NaturalNumber::getId).collect(Collectors.toList()));

        assertEquals(true, page2.hasNext());

        Page<NaturalNumber> page3 = positives.findMatching(9L, Short.valueOf((short) 7), NumberType.COMPOSITE,
            page2.nextPageRequest(), Sort.desc("id"));

        assertEquals(List.of(82L, 81L),
            page3.stream().map(NaturalNumber::getId).collect(Collectors.toList()));

        assertEquals(false, page3.hasNext());
    }

    @Test
    public void testFirstCursoredPageOf8AndNextPages() {
        // The query for this test returns 1-15,25-32 in the following order:

        // 32 requires 6 bits
        // 25, 26, 27, 28, 29, 30, 31 requires 5 bits
        // 8, 9, 10, 11, 12, 13, 14, 15 requires 4 bits
        // 4, 5, 6, 7, 8 requires 3 bits
        // 2, 3 requires 2 bits
        // 1 requires 1 bit

        Order<NaturalNumber> order = Order.by(Sort.asc("id"));
        PageRequest first8 = PageRequest.ofSize(8);
        CursoredPage<NaturalNumber> page;

        try {
            page = positives.findByFloorOfSquareRootNotAndIdLessThanOrderByNumBitsRequiredDesc(4L, 33L, first8, order);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of And.
            return;
        }

        assertEquals(8, page.numberOfElements());

        assertEquals(Arrays.toString(new Long[]{32L, 25L, 26L, 27L, 28L, 29L, 30L, 31L}),
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));

        try {
            page = positives.findByFloorOfSquareRootNotAndIdLessThanOrderByNumBitsRequiredDesc(4L, 33L, page.nextPageRequest(), order);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L}),
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));

        assertEquals(8, page.numberOfElements());

        try {
            page = positives.findByFloorOfSquareRootNotAndIdLessThanOrderByNumBitsRequiredDesc(4L, 33L, page.nextPageRequest(), order);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(7, page.numberOfElements());

        assertEquals(Arrays.toString(new Long[]{4L, 5L, 6L, 7L, 2L, 3L, 1L}),
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));
    }

    @Test
    public void testFirstCursoredPageWithoutTotalOf6AndNextPages() {
        PageRequest first6 = PageRequest.ofSize(6).withoutTotal();
        CursoredPage<NaturalNumber> slice;

        try {
            slice = numbers.findByFloorOfSquareRootOrderByIdAsc(7L, first6);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            // Column and Key-Value databases might not be capable of sorting.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{49L, 50L, 51L, 52L, 53L, 54L}),
            Arrays.toString(slice.stream().map(number -> number.getId()).toArray()));

        assertEquals(6, slice.numberOfElements());

        try {
            slice = numbers.findByFloorOfSquareRootOrderByIdAsc(7L, slice.nextPageRequest());
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(6, slice.numberOfElements());

        assertEquals(Arrays.toString(new Long[]{55L, 56L, 57L, 58L, 59L, 60L}),
            Arrays.toString(slice.stream().map(number -> number.getId()).toArray()));

        try {
            slice = numbers.findByFloorOfSquareRootOrderByIdAsc(7L, slice.nextPageRequest());
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{61L, 62L, 63L}),
            Arrays.toString(slice.stream().map(number -> number.getId()).toArray()));

        assertEquals(3, slice.numberOfElements());
    }

    @Test
    public void testFirstPageOf10() {
        PageRequest first10 = PageRequest.ofSize(10);
        Page<AsciiCharacter> page;
        try {
            page = characters.findByNumericValueBetween(48, 90, first10,
                Order.by(_AsciiCharacter.numericValue.asc())); // '0' to 'Z'
        } catch (UnsupportedOperationException x) {
            // Some NoSQL databases lack the ability to count the total results
            // and therefore cannot support a return type of Page.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of Between.
            return;
        }

        assertEquals(1, page.pageRequest().page());
        assertEquals(true, page.hasContent());
        assertEquals(10, page.numberOfElements());
        try {
            assertEquals(43L, page.totalElements());
            assertEquals(5L, page.totalPages());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // Some NoSQL databases lack the ability to count the total results
            } else {
                throw x;
            }
        }

        assertEquals("30:0;31:1;32:2;33:3;34:4;35:5;36:6;37:7;38:8;39:9;", // '0' to '9'
            page.stream()
                .map(c -> c.getHexadecimal() + ':' + c.getThisCharacter() + ';')
                .reduce("", String::concat));
    }

    @Test
    public void testFirstSliceOf5() {
        PageRequest first5 = PageRequest.ofSize(5).withoutTotal();
        Page<NaturalNumber> page;
        try {
            page = numbers.findByNumTypeAndFloorOfSquareRootLessThanEqual(
                NumberType.PRIME,
                8L,
                first5,
                Sort.desc("id"));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                // Key-value databases might not be capable of LessThanEqual.
                return;
            } else {
                throw x;
            }
        }
        assertEquals(5, page.numberOfElements());

        Iterator<NaturalNumber> it = page.iterator();

        // first result
        assertEquals(true, it.hasNext());
        NaturalNumber number = it.next();
        assertEquals(79L, number.getId());
        assertEquals(NumberType.PRIME, number.getNumType());
        assertEquals(8L, number.getFloorOfSquareRoot());
        assertEquals(true, number.isOdd());
        assertEquals(Short.valueOf((short) 7), number.getNumBitsRequired());

        // second result
        assertEquals(true, it.hasNext());
        assertEquals(73L, it.next().getId());

        // third result
        assertEquals(true, it.hasNext());
        assertEquals(71L, it.next().getId());

        // fourth result
        assertEquals(true, it.hasNext());
        assertEquals(67L, it.next().getId());

        // fifth result
        assertEquals(true, it.hasNext());
        number = it.next();
        assertEquals(61L, number.getId());
        assertEquals(NumberType.PRIME, number.getNumType());
        assertEquals(7L, number.getFloorOfSquareRoot());
        assertEquals(true, number.isOdd());
        assertEquals(Short.valueOf((short) 6), number.getNumBitsRequired());

        assertEquals(false, it.hasNext());
    }

    @Test
    public void testGreaterThanEqualExists() {
        try {
            assertEquals(true, positives.existsByIdGreaterThan(0L));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                return; // Key-Value databases are not capable of GreaterThan
            } else {
                throw x;
            }
        }
        assertEquals(true, positives.existsByIdGreaterThan(99L));
        assertEquals(false, positives.existsByIdGreaterThan(100L)); // doesn't exist because the table only has 1 to 100
    }

    @Test
    public void testIn() {
        Stream<NaturalNumber> nonPrimes;
        try {
            nonPrimes = positives.findByNumTypeInOrderByIdAsc(
                Set.of(NumberType.COMPOSITE, NumberType.ONE),
                Limit.of(9));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of In
                // when used with entity attributes other than the Id.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(List.of(1L, 4L, 6L, 8L, 9L, 10L, 12L, 14L, 15L),
            nonPrimes.map(NaturalNumber::getId).collect(Collectors.toList()));

        Stream<NaturalNumber> primes = positives.findByNumTypeInOrderByIdAsc(Collections.singleton(NumberType.PRIME),
            Limit.of(6));
        assertEquals(List.of(2L, 3L, 5L, 7L, 11L, 13L),
            primes.map(NaturalNumber::getId).collect(Collectors.toList()));
    }

    @Test
    public void testIgnoreCase() {
        Stream<AsciiCharacter> found;
        try {
            found = characters.findByHexadecimalIgnoreCaseBetweenAndHexadecimalNotIn(
                "4c", "5A", Set.of("5"),
                Order.by(Sort.asc("hexadecimal")));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of IgnoreCase
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of Between.
                // Column and Key-Value databases might not be capable of In
                // Column and Key-Value databases might not be capable of sorting.
                // when used with entity attributes other than the Id.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(List.of(Character.valueOf('L'), // 4c
                Character.valueOf('M'), // 4d
                Character.valueOf('N'), // 4e
                Character.valueOf('O'), // 4f
                Character.valueOf('P'), // 50
                Character.valueOf('Q'), // 51
                Character.valueOf('R'), // 52
                Character.valueOf('S'), // 53
                Character.valueOf('T'), // 54
                Character.valueOf('U'), // 55
                Character.valueOf('V'), // 56
                Character.valueOf('W'), // 57
                Character.valueOf('X'), // 58
                Character.valueOf('Y'), // 59
                Character.valueOf('Z')), // 5a
            found.map(AsciiCharacter::getThisCharacter).collect(Collectors.toList()));
    }

    @Test
    public void testCursoredPageOf7FromCursor() {
        // The query for this test returns 1-35 and 49 in the following order:
        //
        // 35 34 33 32 49 24 23 22 21 20 19 18 17 16 31 30 29 28 27 26 25 08 15 14 13 12 11 10 09 07 06 05 04 03 02 01
        //                                                             ^^^^^^ page 1 ^^^^^^
        //                                        ^^^ previous page ^^
        //                                                                                  ^^^^^ next page ^^^^

        Order<NaturalNumber> order = Order.by(Sort.asc("floorOfSquareRoot"), Sort.desc("id"));
        PageRequest middle7 = PageRequest.afterCursor(
            Cursor.forKey((short) 5, 5L, 26L), // 20th result is 26; it requires 5 bits and its square root rounds down to 5.),
            4L, 7, true);

        CursoredPage<NaturalNumber> page;
        try {
            page = positives.findByFloorOfSquareRootNotAndIdLessThanOrderByNumBitsRequiredDesc(6L, 50L, middle7, order);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of And.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{25L, // 5 bits required, square root rounds down to 5
                8L, // 4 bits required, square root rounds down to 2
                15L, 14L, 13L, 12L, 11L // 4 bits required, square root rounds down to 3
            }),
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));

        assertEquals(7, page.numberOfElements());

        assertEquals(true, page.hasPrevious());

        CursoredPage<NaturalNumber> previousPage;
        try {
            previousPage = positives.findByFloorOfSquareRootNotAndIdLessThanOrderByNumBitsRequiredDesc(6L, 50L,
                page.previousPageRequest(),
                order);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{16L, // 4 bits required, square root rounds down to 4
                31L, 30L, 29L, 28L, 27L, 26L // 5 bits required, square root rounds down to 5
            }),
            Arrays.toString(previousPage.stream().map(number -> number.getId()).toArray()));

        assertEquals(7, previousPage.numberOfElements());

        CursoredPage<NaturalNumber> nextPage;
        try {
            nextPage = positives.findByFloorOfSquareRootNotAndIdLessThanOrderByNumBitsRequiredDesc(6L, 50L,
                page.nextPageRequest(),
                order);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{10L, 9L, // 4 bits required, square root rounds down to 3
                7L, 6L, 5L, 4L, // 3 bits required, square root rounds down to 2
                3L // 2 bits required, square root rounds down to 1
            }),
            Arrays.toString(nextPage.stream().map(number -> number.getId()).toArray()));

        assertEquals(7, nextPage.numberOfElements());
    }

    @Test
    public void testCursoredPageOfNothing() {

        CursoredPage<NaturalNumber> page;
        try {
            // There are no positive integers less than 4 which have a square root that rounds down to something other than 1.
            page = positives.findByFloorOfSquareRootNotAndIdLessThanOrderByNumBitsRequiredDesc(1L, 4L, PageRequest.ofPage(1L), Order.by());
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of And.
            return;
        }

        assertEquals(false, page.hasContent());
        assertEquals(false, page.hasNext());
        assertEquals(false, page.hasPrevious());
        assertEquals(0, page.content().size());
        assertEquals(0, page.numberOfElements());

        try {
            page.nextPageRequest();
            fail("nextPageRequest must raise NoSuchElementException when current page is empty.");
        } catch (NoSuchElementException x) {
            // expected
        }

        try {
            page.previousPageRequest();
            fail("previousPageRequest must raise NoSuchElementException when current page is empty.");
        } catch (NoSuchElementException x) {
            // expected
        }
    }

    @Test
    public void testCursoredPageWithoutTotalOf9FromCursor() {
        // The query for this test returns composite natural numbers under 64 in the following order:
        //
        // 49 50 51 52 54 55 56 57 58 60 62 63 36 38 39 40 42 44 45 46 48 25 26 27 28 30 32 33 34 35 16 18 20 21 22 24 09 10 12 14 15 04 06 08
        //                                                             ^^^^^^^^ slice 1 ^^^^^^^^^
        //                                  ^^^^^^^^ slice 2 ^^^^^^^^^
        //                                                                                        ^^^^^^^^ slice 3 ^^^^^^^^^

        PageRequest middle9 = PageRequest.afterCursor(
            Cursor.forKey(6L, 46L), // 20th result is 46; its square root rounds down to 6.
            4L, 9, false);
        Order<NaturalNumber> order = Order.by(Sort.desc("floorOfSquareRoot"), Sort.asc("id"));

        CursoredPage<NaturalNumber> slice;
        try {
            slice = numbers.findByNumTypeAndNumBitsRequiredLessThan(NumberType.COMPOSITE, (short) 7, order, middle9);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of And.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{48L, 25L, 26L, 27L, 28L, 30L, 32L, 33L, 34L}),
            Arrays.toString(slice.stream().map(number -> number.getId()).toArray()));

        assertEquals(9, slice.numberOfElements());

        assertEquals(true, slice.hasPrevious());
        CursoredPage<NaturalNumber> previousSlice;
        try {
            previousSlice = numbers.findByNumTypeAndNumBitsRequiredLessThan(NumberType.COMPOSITE,
                (short) 7,
                order,
                slice.previousPageRequest());
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{63L, 36L, 38L, 39L, 40L, 42L, 44L, 45L, 46L}),
            Arrays.toString(previousSlice.stream().map(number -> number.getId()).toArray()));

        assertEquals(9, previousSlice.numberOfElements());

        CursoredPage<NaturalNumber> nextSlice;
        try {
            nextSlice = numbers.findByNumTypeAndNumBitsRequiredLessThan(NumberType.COMPOSITE,
                (short) 7,
                order,
                slice.nextPageRequest());
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(Arrays.toString(new Long[]{35L, 16L, 18L, 20L, 21L, 22L, 24L, 9L, 10L}),
            Arrays.toString(nextSlice.stream().map(number -> number.getId()).toArray()));

        assertEquals(9, nextSlice.numberOfElements());
    }

    @Test
    public void testCursoredPageWithoutTotalOfNothing() {
        // There are no numbers larger than 30 which have a square root that rounds down to 3.
        PageRequest pagination = PageRequest.ofSize(33).afterCursor(Cursor.forKey(30L)).withoutTotal();

        CursoredPage<NaturalNumber> slice;
        try {
            slice = numbers.findByFloorOfSquareRootOrderByIdAsc(3L, pagination);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            // Column and Key-Value databases might not be capable of sorting.
            return;
        }

        assertEquals(false, slice.hasContent());
        assertEquals(0, slice.content().size());
        assertEquals(0, slice.numberOfElements());
    }

    @Test
    public void testLessThanWithCount() {
        try {
            assertEquals(91L, positives.countByIdLessThan(92L));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                return; // Key-Value databases are not capable of LessThan
            } else {
                throw x;
            }
        }

        assertEquals(0L, positives.countByIdLessThan(1L));
    }

    @Test
    public void testLimit() {
        Collection<NaturalNumber> nums;
        try {
            nums = numbers.findByIdGreaterThanEqual(
                60L,
                Limit.of(10),
                Order.by(
                    Sort.asc("floorOfSquareRoot"),
                    Sort.desc("id")));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases are not capable of GreaterThanEqual
                return;
            } else {
                throw x;
            }
        }

        assertEquals(Arrays.toString(new Long[]{63L, 62L, 61L, 60L, // square root rounds down to 7
                80L, 79L, 78L, 77L, 76L, 75L}), // square root rounds down to 8
            Arrays.toString(nums.stream().map(number -> number.getId()).toArray()));
    }

    @Test
    public void testLimitedRange() {
        // Primes above 40 are:
        // 41, 43, 47, 53, 59,
        // 61, 67, 71, 73, 79,
        // 83, 89, ...

        Collection<NaturalNumber> nums;
        try {
            nums = numbers.findByIdGreaterThanEqual(
                40L,
                Limit.range(6, 10),
                Order.by(
                    Sort.asc("numTypeOrdinal"), // primes first
                    Sort.asc("id")));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases are not capable of GreaterThanEqual
                return;
            } else {
                throw x;
            }
        }

        assertEquals(Arrays.toString(new Long[]{61L, 67L, 71L, 73L, 79L}),
            Arrays.toString(nums.stream().map(number -> number.getId()).toArray()));
    }

    @Test
    public void testLimitToOneResult() {
        Collection<NaturalNumber> nums;
        try {
            nums = numbers.findByIdGreaterThanEqual(80L, Limit.of(1), Order.by());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                return; // Key-Value databases are not capable of GreaterThanEqual
            } else {
                throw x;
            }
        }

        Iterator<NaturalNumber> it = nums.iterator();
        assertEquals(true, it.hasNext());

        NaturalNumber num = it.next();
        assertEquals(true, num.getId() >= 80L);

        assertEquals(false, it.hasNext());
    }

    @Test
    public void testLiteralEnumAndLiteralFalse() {

        NaturalNumber two;
        try {
            two = numbers.two().orElseThrow();
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                return; // Key-Value databases are not capable of JDQL TRUE/FALSE
            } else {
                throw x;
            }
        }

        assertEquals(2L, two.getId());
        assertEquals(NumberType.PRIME, two.getNumType());
        assertEquals(Short.valueOf((short) 2), two.getNumBitsRequired());
    }

    @Test
    public void testLiteralInteger() {

        try {
            assertEquals(24, characters.twentyFour());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                // Key-Value databases are not capable of <= in JDQL.
                // Key-Value databases might not be capable of JDQL AND.
                return;
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testLiteralString() {

        try {
            assertEquals(List.of('J', 'K', 'L', 'M'),
                characters.jklOr("4d")
                    .map(AsciiCharacter::getThisCharacter)
                    .sorted()
                    .collect(Collectors.toList()));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Key-Value databases might not be capable of JDQL AND.
                // Column and Key-Value databases might not be capable of JDQL IN
                // when used with entity attributes other than the Id.
                return;
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testLiteralTrue() {
        Page<Long> page1;
        try {
            page1 = numbers.oddsFrom21To(40L, PageRequest.ofSize(5));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                // Key-Value databases are not capable of JDQL BETWEEN
                // Key-Value databases are not capable of JDQL TRUE/FALSE
                return;
            } else {
                throw x;
            }
        }

        try {
            assertEquals(10L, page1.totalElements());
            assertEquals(2L, page1.totalPages());

        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // Some NoSQL databases lack the ability to count the total results
            } else {
                throw x;
            }
        }
        assertEquals(List.of(21L, 23L, 25L, 27L, 29L), page1.content());

        assertEquals(true, page1.hasNext());

        Page<Long> page2 = numbers.oddsFrom21To(40L, page1.nextPageRequest());

        assertEquals(List.of(31L, 33L, 35L, 37L, 39L), page2.content());

        if (page2.hasNext()) {
            Page<Long> page3 = numbers.oddsFrom21To(40L, page2.nextPageRequest());
            assertEquals(false, page3.hasContent());
            assertEquals(false, page3.hasNext());
        }
    }

    @Test
    public void testMixedSort() {
        NaturalNumber[] nums;
        try {
            nums = numbers.findByIdLessThan(
                15L,
                Sort.asc("numBitsRequired"),
                Sort.desc("id"));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of LessThan.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(Arrays.toString(new Long[]{1L, // 1 bit
                3L, 2L, // 2 bits
                7L, 6L, 5L, 4L, // 3 bits
                14L, 13L, 12L, 11L, 10L, 9L, 8L}), // 4 bits
            Arrays.toString(Stream.of(nums).map(number -> number.getId()).toArray()));
    }

    @Disabled // Pending feature
    @Test
    public void testNonUniqueResultException() {
        try {
            AsciiCharacter ch = characters.findByIsControlTrueAndNumericValueBetween(10, 15);
            fail("Unexpected result of findByIsControlTrueAndNumericValueBetween(10, 15): " + ch.getHexadecimal());
        } catch (NonUniqueResultException x) {
            log.info("testNonUniqueResultException expected to catch exception " + x + ". Printing its stack trace:");
            x.printStackTrace(System.out);
            // test passes
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of Between.
                // Key-Value databases might not be capable of True/False comparison.
                return;
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testNot() {
        NaturalNumber[] n;
        try {
            n = numbers.findByNumTypeNot(
                NumberType.COMPOSITE,
                Limit.of(8),
                Order.by(Sort.asc("id")));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                return;
            } else {
                throw x;
            }
        }
        assertEquals(8, n.length);
        assertEquals(1L, n[0].getId());
        assertEquals(2L, n[1].getId());
        assertEquals(3L, n[2].getId());
        assertEquals(5L, n[3].getId());
        assertEquals(7L, n[4].getId());
        assertEquals(11L, n[5].getId());
        assertEquals(13L, n[6].getId());
        assertEquals(17L, n[7].getId());
    }

    @Test
    public void testOr() {
        Stream<NaturalNumber> found;
        try {
            found = positives.findByNumTypeOrFloorOfSquareRoot(NumberType.ONE, 2L);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of Or.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(List.of(1L, 4L, 5L, 6L, 7L, 8L),
            found.map(NaturalNumber::getId).sorted().collect(Collectors.toList()));
    }

    @Test
    public void testOrderByHasPrecedenceOverPageRequestSorts() {
        PageRequest pagination = PageRequest.ofSize(8);
        Order<NaturalNumber> order = Order.by(Sort.asc("numTypeOrdinal"), Sort.desc("id"));

        Page<NaturalNumber> page;
        try {
            page = numbers.findByIdLessThanOrderByFloorOfSquareRootDesc(
                25L, pagination, order);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of LessThan.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(Arrays.toString(new Long[]{23L, 19L, 17L, // square root rounds down to 4; prime
                24L, 22L, 21L, 20L, 18L}), // square root rounds down to 4; composite
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));

        assertEquals(true, page.hasNext());
        pagination = page.nextPageRequest();
        page = numbers.findByIdLessThanOrderByFloorOfSquareRootDesc(25L, pagination, order);

        assertEquals(Arrays.toString(new Long[]{16L, // square root rounds down to 4; composite
                13L, 11L, // square root rounds down to 3; prime
                15L, 14L, 12L, 10L, 9L}), // square root rounds down to 3; composite
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));

        assertEquals(true, page.hasNext());
        pagination = page.nextPageRequest();
        page = numbers.findByIdLessThanOrderByFloorOfSquareRootDesc(25L, pagination, order);

        assertEquals(Arrays.toString(new Long[]{7L, 5L, // square root rounds down to 2; prime
                8L, 6L, 4L, // square root rounds down to 2; composite
                1L, // square root rounds down to 1; one
                3L, 2L}), // square root rounds down to 1; prime
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));

        if (page.hasNext()) {
            pagination = page.nextPageRequest();
            page = numbers.findByIdLessThanOrderByFloorOfSquareRootDesc(25L, pagination, order);
            assertEquals(false, page.hasContent());
        }
    }

    @Test
    public void testOrderByHasPrecedenceOverSorts() {
        Stream<NaturalNumber> nums;
        try {
            nums = numbers.findByIdBetweenOrderByNumTypeOrdinalAsc(
                5L, 24L,
                Order.by(Sort.desc("floorOfSquareRoot"), Sort.asc("id")));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of Between.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(Arrays.toString(new Long[]{17L, 19L, 23L, // prime; square root rounds down to 4
                11L, 13L, // prime; square root rounds down to 3
                5L, 7L, // prime; square root rounds down to 2
                16L, 18L, 20L, 21L, 22L, 24L, // composite; square root rounds down to 4
                9L, 10L, 12L, 14L, 15L, // composite; square root rounds down to 3
                6L, 8L}), // composite; square root rounds down to 2
            Arrays.toString(nums.map(number -> number.getId()).toArray()));
    }

    @Test
    public void testPageOfNothing() {
        PageRequest pagination = PageRequest.ofSize(6);
        Page<AsciiCharacter> page;
        try {
            page = characters.findByNumericValueBetween(150, 160, pagination,
                Order.by(_AsciiCharacter.id.asc()));
        } catch (UnsupportedOperationException x) {
            // Some NoSQL databases lack the ability to count the total results
            // and therefore cannot support a return type of Page.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of Between.
            return;
        }

        assertEquals(0, page.numberOfElements());
        assertEquals(0, page.stream().count());
        assertEquals(0, page.content().size());
        assertEquals(false, page.hasContent());
        assertEquals(false, page.iterator().hasNext());
        try {
            assertEquals(0L, page.totalElements());
            assertEquals(0L, page.totalPages());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // Some NoSQL databases lack the ability to count the total results
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testPartialQueryOrderBy() {

        assertEquals(List.of('A', 'B', 'C', 'D', 'E', 'F'),
            characters.alphabetic(Limit.range(65, 70))
                .map(AsciiCharacter::getThisCharacter)
                .collect(Collectors.toList()));
    }

    @Test
    public void testPartialQuerySelectAndOrderBy() {

        Character[] chars = characters.reverseAlphabetic(Limit.range(6, 13));
        for (int i = 0; i < chars.length; i++) {
            assertEquals("zyxwvuts".charAt(i), chars[i]);
        }
    }

    @Test
    public void testPrimaryEntityClassDeterminedByLifeCycleMethods() {
        assertEquals(4L, customRepo.countByIdIn(Set.of(2L, 15L, 37L, -5L, 60L)));

        assertEquals(true, customRepo.existsByIdIn(Set.of(17L, 14L, -1L)));

        assertEquals(false, customRepo.existsByIdIn(Set.of(-10L, -12L, -14L)));
    }

    @Test
    public void testQueryWithNot() {

        // 'NOT LIKE' excludes '@'
        // 'NOT IN' excludes 'E' and 'G'
        // 'NOT BETWEEN' excludes 'H' through 'N'.
        Character[] abcdfo;
        try {
            abcdfo = characters.getABCDFO();
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of Like
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of Between.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(6, abcdfo.length);
        for (int i = 0; i < abcdfo.length; i++) {
            assertEquals("ABCDFO".charAt(i), abcdfo[i]);
        }
    }

    @Test
    public void testQueryWithNull() {
        try {
            assertEquals("4a", characters.hex('J').orElseThrow());
            assertEquals("44", characters.hex('D').orElseThrow());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of Contains.
                // Key-Value databases might not be capable of And.
                return;
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testQueryWithOr() {
        PageRequest page1Request = PageRequest.ofSize(4);
        CursoredPage<NaturalNumber> page1;

        try {
            page1 = positives.withBitCountOrOfTypeAndBelow((short) 4,
                NumberType.COMPOSITE, 20L,
                Sort.desc("numBitsRequired"),
                Sort.asc("id"),
                page1Request);
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            // Column and Key-Value databases might not be capable of JPQL OR.
            // Column and Key-Value databases might not be capable of sorting.
            return;
        }

        assertEquals(List.of(16L, 18L, 8L, 9L),
            page1.stream()
                .map(NaturalNumber::getId)
                .collect(Collectors.toList()));

        assertEquals(true, page1.hasTotals());
        assertEquals(true, page1.hasNext());
        try {
            assertEquals(3L, page1.totalPages());
            assertEquals(12L, page1.totalElements());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // Some NoSQL databases lack the ability to count the total results
            } else {
                throw x;
            }
        }

        CursoredPage<NaturalNumber> page2;

        try {
            page2 = positives.withBitCountOrOfTypeAndBelow((short) 4,
                NumberType.COMPOSITE, 20L,
                Sort.desc("numBitsRequired"),
                Sort.asc("id"),
                page1.nextPageRequest());
        } catch (UnsupportedOperationException x) {
            // Test passes: Jakarta Data providers must raise UnsupportedOperationException when the database
            // is not capable of cursor-based pagination.
            return;
        }

        assertEquals(List.of(10L, 11L, 12L, 13L),
            page2.stream()
                .map(NaturalNumber::getId)
                .collect(Collectors.toList()));

        assertEquals(true, page2.hasNext());

        CursoredPage<NaturalNumber> page3 = positives.withBitCountOrOfTypeAndBelow((short) 4,
            NumberType.COMPOSITE, 20L,
            Sort.desc("numBitsRequired"),
            Sort.asc("id"),
            page2.nextPageRequest());

        assertEquals(List.of(14L, 15L, 4L, 6L),
            page3.stream()
                .map(NaturalNumber::getId)
                .collect(Collectors.toList()));

        if (page3.hasNext()) {
            CursoredPage<NaturalNumber> page4 = positives.withBitCountOrOfTypeAndBelow((short) 4,
                NumberType.COMPOSITE, 20L,
                Sort.desc("numBitsRequired"),
                Sort.asc("id"),
                page3.nextPageRequest());
            assertEquals(false, page4.hasContent());
        }
    }

    @Test
    public void testQueryWithParenthesis() {

        try {
            assertEquals(
                List.of(15L, 7L, 5L, 3L, 1L),
                positives.oddAndEqualToOrBelow(15L, 9L));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.DOCUMENT)) {
                // Document, Column, and Key-Value databases might not be capable of parentheses.
                // Column and Key-Value databases might not be capable of JDQL OR.
                // Key-Value databases might not be capable of < in JDQL.
                // Key-Value databases might not be capable of JDQL AND.
                return;
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testSingleEntity() {
        AsciiCharacter ch;
        try {
            ch = characters.findByHexadecimalIgnoreCase("2B");
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                return; // NoSQL databases might not be capable of IgnoreCase
            } else {
                throw x;
            }
        }

        assertEquals('+', ch.getThisCharacter());
        assertEquals("2b", ch.getHexadecimal());
        assertEquals(43, ch.getNumericValue());
        assertEquals(false, ch.isControl());
    }

    @Test
    public void testSliceOfNothing() {
        PageRequest pagination = PageRequest.ofSize(5).withoutTotal();
        Page<NaturalNumber> page;
        try {
            page = numbers.findByNumTypeAndFloorOfSquareRootLessThanEqual(
                NumberType.COMPOSITE, 1L, pagination, Sort.desc("id"));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of LessThanEqual.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(false, page.hasContent());
        assertEquals(0, page.content().size());
        assertEquals(0, page.numberOfElements());
    }

    @Test
    public void testStaticMetamodelAscendingSorts() {
        assertEquals(Sort.asc("id"), _AsciiChar.id.asc());
        assertEquals(Sort.ascIgnoreCase(_AsciiChar.HEXADECIMAL), _AsciiChar.hexadecimal.ascIgnoreCase());
        assertEquals(Sort.ascIgnoreCase("thisCharacter"), _AsciiChar.thisCharacter.ascIgnoreCase());

        PageRequest pageRequest = PageRequest.ofSize(6);
        Page<AsciiCharacter> page1;
        try {
            page1 = characters.findByNumericValueBetween(
                68, 90, pageRequest,
                Order.by(_AsciiChar.numericValue.asc()));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of Between.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(List.of('D', 'E', 'F', 'G', 'H', 'I'),
            page1.stream()
                .map(AsciiCharacter::getThisCharacter)
                .collect(Collectors.toList()));
    }

    @Test
    public void testStaticMetamodelAscendingSortsPreGenerated() {
        assertEquals(Sort.asc("id"), _AsciiCharacter.id.asc());
        assertEquals(Sort.asc("isControl"), _AsciiCharacter.isControl.asc());
        assertEquals(Sort.ascIgnoreCase(_AsciiCharacter.HEXADECIMAL), _AsciiCharacter.hexadecimal.ascIgnoreCase());
        assertEquals(Sort.ascIgnoreCase("thisCharacter"), _AsciiCharacter.thisCharacter.ascIgnoreCase());

        PageRequest pageRequest = PageRequest.ofSize(7);
        Page<AsciiCharacter> page1;
        try {
            page1 = characters.findByNumericValueBetween(
                100, 122, pageRequest,
                Order.by(_AsciiCharacter.numericValue.asc()));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of Between.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(List.of('d', 'e', 'f', 'g', 'h', 'i', 'j'),
            page1.stream()
                .map(AsciiCharacter::getThisCharacter)
                .collect(Collectors.toList()));
    }

    @Test
    public void testStaticMetamodelAttributeNames() {
        assertEquals(_AsciiChar.HEXADECIMAL, _AsciiChar.hexadecimal.name());
        assertEquals(_AsciiChar.ID, _AsciiChar.id.name());
        assertEquals("isControl", _AsciiChar.isControl.name());
        assertEquals(_AsciiChar.NUMERICVALUE, _AsciiChar.numericValue.name());
        assertEquals("thisCharacter", _AsciiChar.thisCharacter.name());
    }

    @Test
    public void testStaticMetamodelAttributeNamesPreGenerated() {
        assertEquals(_AsciiCharacter.HEXADECIMAL, _AsciiCharacter.hexadecimal.name());
        assertEquals(_AsciiCharacter.ID, _AsciiCharacter.id.name());
        assertEquals("isControl", _AsciiCharacter.isControl.name());
        assertEquals(_AsciiChar.NUMERICVALUE, _AsciiCharacter.numericValue.name());
        assertEquals("thisCharacter", _AsciiCharacter.thisCharacter.name());
    }

    @Test
    public void testStaticMetamodelDescendingSorts() {
        assertEquals(Sort.desc(_AsciiChar.ID), _AsciiChar.id.desc());
        assertEquals(Sort.descIgnoreCase("hexadecimal"), _AsciiChar.hexadecimal.descIgnoreCase());
        assertEquals(Sort.descIgnoreCase("thisCharacter"), _AsciiChar.thisCharacter.descIgnoreCase());

        Sort<AsciiCharacter> sort = _AsciiChar.numericValue.desc();
        AsciiCharacter[] found;
        try {
            found = characters.findFirst3ByNumericValueGreaterThanEqualAndHexadecimalEndsWith(
                30, "1", sort);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of EndsWith.
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of GreaterThanEqual.
                return;
            } else {
                throw x;
            }
        }
        assertEquals(3, found.length);
        assertEquals('q', found[0].getThisCharacter());
        assertEquals('a', found[1].getThisCharacter());
        assertEquals('Q', found[2].getThisCharacter());
    }

    @Test
    public void testStaticMetamodelDescendingSortsPreGenerated() {
        assertEquals(Sort.desc(_AsciiCharacter.ID), _AsciiCharacter.id.desc());
        assertEquals(Sort.desc("isControl"), _AsciiCharacter.isControl.desc());
        assertEquals(Sort.descIgnoreCase("hexadecimal"), _AsciiCharacter.hexadecimal.descIgnoreCase());
        assertEquals(Sort.descIgnoreCase("thisCharacter"), _AsciiCharacter.thisCharacter.descIgnoreCase());

        Sort<AsciiCharacter> sort = _AsciiCharacter.numericValue.desc();
        AsciiCharacter[] found;
        try {
            found = characters.findFirst3ByNumericValueGreaterThanEqualAndHexadecimalEndsWith(
                30, "4", sort);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of EndsWith.
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of GreaterThanEqual.
                return;
            } else {
                throw x;
            }
        }
        assertEquals(3, found.length);
        assertEquals('t', found[0].getThisCharacter());
        assertEquals('d', found[1].getThisCharacter());
        assertEquals('T', found[2].getThisCharacter());
    }

    @Test
    public void testStreamsFromList() {
        List<AsciiCharacter> chars;
        try {
            chars = characters.findByNumericValueLessThanEqualAndNumericValueGreaterThanEqual(
                109, 101);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of GTE/LTE.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(Arrays.toString(new Character[]{Character.valueOf('e'),
                Character.valueOf('f'),
                Character.valueOf('g'),
                Character.valueOf('h'),
                Character.valueOf('i'),
                Character.valueOf('j'),
                Character.valueOf('k'),
                Character.valueOf('l'),
                Character.valueOf('m')}),
            Arrays.toString(chars.stream().map(ch -> ch.getThisCharacter()).sorted().toArray()));

        assertEquals(101 + 102 + 103 + 104 + 105 + 106 + 107 + 108 + 109,
            chars.stream().mapToInt(AsciiCharacter::getNumericValue).sum());

        Set<String> sorted = new TreeSet<>();
        chars.forEach(ch -> sorted.add(ch.getHexadecimal()));
        assertEquals(new TreeSet<>(Set.of("65", "66", "67", "68", "69", "6a", "6b", "6c", "6d")),
            sorted);

        List<AsciiCharacter> empty = characters.findByNumericValueLessThanEqualAndNumericValueGreaterThanEqual(115, 120);
        assertEquals(false, empty.iterator().hasNext());
        assertEquals(0L, empty.stream().count());
    }

    @Test
    public void testThirdAndFourthPagesOf10() {
        Order<AsciiCharacter> order = Order.by(_AsciiCharacter.numericValue.asc());
        PageRequest third10 = PageRequest.ofPage(3).size(10);
        Page<AsciiCharacter> page;
        try {
            page = characters.findByNumericValueBetween(48, 90, third10, order); // 'D' to 'M'
        } catch (UnsupportedOperationException x) {
            // Some NoSQL databases lack the ability to count the total results
            // and therefore cannot support a return type of Page.
            // Column and Key-Value databases might not be capable of sorting.
            // Key-Value databases might not be capable of Between.
            return;
        }

        assertEquals(3, page.pageRequest().page());
        assertEquals(true, page.hasContent());
        assertEquals(10, page.numberOfElements());
        try {
            assertEquals(43L, page.totalElements());
            assertEquals(5L, page.totalPages());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // Some NoSQL databases lack the ability to count the total results
            } else {
                throw x;
            }
        }

        assertEquals("44:D;45:E;46:F;47:G;48:H;49:I;4a:J;4b:K;4c:L;4d:M;",
            page.stream()
                .map(c -> c.getHexadecimal() + ':' + c.getThisCharacter() + ';')
                .reduce("", String::concat));

        assertEquals(true, page.hasNext());
        PageRequest fourth10 = page.nextPageRequest();
        page = characters.findByNumericValueBetween(48, 90, fourth10, order); // 'N' to 'W'

        assertEquals(4, page.pageRequest().page());
        assertEquals(true, page.hasContent());
        assertEquals(10, page.numberOfElements());
        try {
            assertEquals(43L, page.totalElements());
            assertEquals(5L, page.totalPages());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // Some NoSQL databases lack the ability to count the total results
            } else {
                throw x;
            }
        }

        assertEquals("4e:N;4f:O;50:P;51:Q;52:R;53:S;54:T;55:U;56:V;57:W;",
            page.stream()
                .map(c -> c.getHexadecimal() + ':' + c.getThisCharacter() + ';')
                .reduce("", String::concat));
    }

    @Test
    public void testThirdAndFourthSlicesOf5() {
        PageRequest third5 = PageRequest.ofPage(3).size(5).withoutTotal();
        Sort<NaturalNumber> sort = Sort.desc("id");
        Page<NaturalNumber> page;
        try {
            page = numbers.findByNumTypeAndFloorOfSquareRootLessThanEqual(
                NumberType.PRIME, 8L, third5, sort);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of LessThanEqual.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(3, page.pageRequest().page());
        assertEquals(5, page.numberOfElements());

        assertEquals(Arrays.toString(new Long[]{37L, 31L, 29L, 23L, 19L}),
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));

        assertEquals(true, page.hasNext());
        PageRequest fourth5 = page.nextPageRequest();

        page = numbers.findByNumTypeAndFloorOfSquareRootLessThanEqual(NumberType.PRIME, 8L, fourth5, sort);

        assertEquals(4, page.pageRequest().page());
        assertEquals(5, page.numberOfElements());

        assertEquals(Arrays.toString(new Long[]{17L, 13L, 11L, 7L, 5L}),
            Arrays.toString(page.stream().map(number -> number.getId()).toArray()));
    }

    @Test
    public void testTrue() {
        Iterable<NaturalNumber> odd;
        try {
            odd = positives.findByIsOddTrueAndIdLessThanEqualOrderByIdDesc(10L);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                // Key-Value databases might not be capable of And.
                // Key-Value databases might not be capable of LessThanEqual.
                // Key-Value databases might not be capable of True/False comparison.
                return;
            } else {
                throw x;
            }
        }

        Iterator<NaturalNumber> it = odd.iterator();

        assertEquals(true, it.hasNext());
        assertEquals(9L, it.next().getId());

        assertEquals(true, it.hasNext());
        assertEquals(7L, it.next().getId());

        assertEquals(true, it.hasNext());
        assertEquals(5L, it.next().getId());

        assertEquals(true, it.hasNext());
        assertEquals(3L, it.next().getId());

        assertEquals(true, it.hasNext());
        assertEquals(1L, it.next().getId());

        assertEquals(false, it.hasNext());
    }

    @Test
    public void testUpdateQueryWithoutWhereClause() {
        // Ensure there is no data left over from other tests:

        try {
            shared.removeAll();
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH) && TestProperty.delay.isSet()) {
                // NoSQL databases with eventual consistency might not be capable
                // of counting removed entities.
                // Use alternative approach for ensuring no data is present:
                boxes.deleteAll(boxes.findAll().toList());
            } else {
                throw x;
            }
        }



        boxes.saveAll(List.of(Box.of("TestUpdateQueryWithoutWhereClause-01", 125, 117, 44),
            Box.of("TestUpdateQueryWithoutWhereClause-02", 173, 165, 52),
            Box.of("TestUpdateQueryWithoutWhereClause-03", 229, 221, 60)));



        boolean resized;
        try {
            // increases length by 12, decreases width by 12, and doubles the height
            assertEquals(3L, shared.resizeAll(12, 2));
            resized = true;
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of arithmetic in updates.
                resized = false;
            } else {
                throw x;
            }
        }



        if (resized) {
            Box b1 = boxes.findById("TestUpdateQueryWithoutWhereClause-01").orElseThrow();
            assertEquals(137, b1.length); // increased by 12
            assertEquals(105, b1.width); // decreased by 12
            assertEquals(88, b1.height); // increased by factor of 2

            Box b2 = boxes.findById("TestUpdateQueryWithoutWhereClause-02").orElseThrow();
            assertEquals(185, b2.length); // increased by 12
            assertEquals(153, b2.width); // decreased by 12
            assertEquals(104, b2.height); // increased by factor of 2

            Box b3 = boxes.findById("TestUpdateQueryWithoutWhereClause-03").orElseThrow();
            assertEquals(241, b3.length); // increased by 12
            assertEquals(209, b3.width); // decreased by 12
            assertEquals(120, b3.height); // increased by factor of 2
        }

        try {
            var removeAllResult = shared.removeAll();
            assertEquals(3, removeAllResult);
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH) && TestProperty.delay.isSet()) {
                // NoSQL databases with eventual consistency might not be capable
                // of counting removed entities.
                // Use alternative approach for removing entities.
                boxes.deleteAll(boxes.findAll().toList());
            } else {
                throw x;
            }
        }



        try {
            assertEquals(0L, shared.resizeAll(2, 1));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of arithmetic in updates.
            } else {
                throw x;
            }
        }
    }

    @Test
    public void testUpdateQueryWithWhereClause() {
        try {
            // Ensure there is no data left over from other tests:
            shared.deleteIfPositive();
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                return; // Key-Value databases might not be capable of And.
            } else if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH) && TestProperty.delay.isSet()) {
                // NoSQL databases with eventual consistency might not be capable
                // of counting removed entities.
                // Use alternative approach for ensuring no data is present:
                shared.deleteIfPositiveWithoutReturnRecords();
            } else {
                throw x;
            }
        }

        UUID id1 = shared.create(Coordinate.of("first", 1.41d, 5.25f)).id;
        UUID id2 = shared.create(Coordinate.of("second", 2.2d, 2.34f)).id;



        float c1yExpected;
        double c1xExpected;
        try {
            assertEquals(true, shared.move(id1, 1.23d, 1.5f));
            c1yExpected = 3.5f; // 5.25 / 1.5 = 3.5
            c1xExpected = 1.23D;
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH)) {
                // NoSQL databases might not be capable of arithmetic in updates.
                c1yExpected = 5.25f;
                c1xExpected = 1.41D;// no change
            } else {
                throw x;
            }
        }



        Coordinate c1 = shared.withUUID(id1).orElseThrow();
        assertEquals(c1xExpected, c1.x, 0.001d);
        assertEquals(c1yExpected, c1.y, 0.001f);

        Coordinate c2 = shared.withUUID(id2).orElseThrow();
        assertEquals(2.2d, c2.x, 0.001d);
        assertEquals(2.34f, c2.y, 0.001f);

        try {
            assertEquals(2, shared.deleteIfPositive());
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.KEY_VALUE)) {
                return; // Key-Value databases might not be capable of And.
            } else if (type.isKeywordSupportAtOrBelow(DatabaseType.GRAPH) && TestProperty.delay.isSet()) {
                // NoSQL databases with eventual consistency might not be capable
                // of counting removed entities.
                // Use alternative approach for ensuring no data is present:
                shared.deleteIfPositiveWithoutReturnRecords();
            } else {
                throw x;
            }
        }


        assertEquals(false, shared.withUUID(id1).isPresent());
        assertEquals(false, shared.withUUID(id2).isPresent());
    }

    @Test
    public void testVarargsSort() {
        List<NaturalNumber> list;
        try {
            list = numbers.findByIdLessThanEqual(
                12L,
                Sort.asc("floorOfSquareRoot"),
                Sort.desc("numBitsRequired"),
                Sort.asc("id"));
        } catch (UnsupportedOperationException x) {
            if (type.isKeywordSupportAtOrBelow(DatabaseType.COLUMN)) {
                // Column and Key-Value databases might not be capable of sorting.
                // Key-Value databases might not be capable of LessThanEqual.
                return;
            } else {
                throw x;
            }
        }

        assertEquals(Arrays.toString(new Long[]{2L, 3L, // square root rounds down to 1; 2 bits
                1L, // square root rounds down to 1; 1 bit
                8L, // square root rounds down to 2; 4 bits
                4L, 5L, 6L, 7L, // square root rounds down to 2; 3 bits
                9L, 10L, 11L, 12L}), // square root rounds down to 3; 4 bits
            Arrays.toString(list.stream().map(number -> number.getId()).toArray()));
    }
}
