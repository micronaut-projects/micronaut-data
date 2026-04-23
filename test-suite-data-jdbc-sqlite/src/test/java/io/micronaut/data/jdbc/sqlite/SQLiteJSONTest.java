package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.tck.entities.Discount;
import io.micronaut.data.tck.entities.JsonEntity;
import io.micronaut.data.tck.entities.Sale;
import io.micronaut.data.tck.entities.SaleDTO;
import io.micronaut.data.tck.entities.SaleItem;
import io.micronaut.data.tck.entities.SampleData;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@SQLiteDBProperties
class SQLiteJSONTest {

    @Inject
    SQLiteSaleRepository saleRepository;

    @Inject
    SQLiteSaleItemRepository saleItemRepository;

    @Inject
    SQLiteJsonEntityRepository jsonEntityRepository;

    @AfterEach
    void cleanup() {
        saleRepository.deleteAll();
        saleItemRepository.deleteAll();
        jsonEntityRepository.deleteAll();
    }

    @Test
    void testReadAndWriteJson() {
        Sale sale = new Sale();
        sale.setName("test 1");
        sale.setData(new LinkedHashMap<>(java.util.Map.of("foo", "bar")));
        sale.setQuantities(new LinkedHashMap<>(java.util.Map.of("foo", 10)));
        saleRepository.save(sale);
        sale = saleRepository.findById(sale.getId()).orElse(null);

        assertNotNull(sale);
        assertEquals("test 1", sale.getName());
        assertEquals(java.util.Map.of("foo", "bar"), sale.getData());
        assertEquals(java.util.Map.of("foo", 10), sale.getQuantities());

        sale.getData().put("foo2", "bar2");
        saleRepository.update(sale);
        sale = saleRepository.findById(sale.getId()).orElse(null);
        assertNotNull(sale);
        assertTrue(sale.getData().containsKey("foo2"));

        sale.setData(new LinkedHashMap<>(java.util.Map.of("foo", "changed")));
        saleRepository.update(sale);
        sale = saleRepository.findById(sale.getId()).orElse(null);

        assertNotNull(sale);
        assertEquals("test 1", sale.getName());
        assertEquals(java.util.Map.of("foo", "changed"), sale.getData());
        assertEquals(java.util.Map.of("foo", 10), sale.getQuantities());

        SaleDTO dto = saleRepository.getById(sale.getId());
        assertEquals("test 1", dto.getName());
        assertEquals(java.util.Map.of("foo", "changed"), dto.getData());
    }

    @Test
    void testReadAndWriteWithUpdated() {
        Sale sale = new Sale();
        sale.setName("test 1");
        sale.setData(new LinkedHashMap<>(java.util.Map.of("foo", "bar")));
        sale.setDataList(List.of("abc1", "abc2"));
        saleRepository.save(sale);
        sale = saleRepository.findById(sale.getId()).orElse(null);

        assertNotNull(sale);
        assertEquals("test 1", sale.getName());
        assertEquals(java.util.Map.of("foo", "bar"), sale.getData());
        assertEquals(List.of("abc1", "abc2"), sale.getDataList());

        saleRepository.updateData(sale.getId(), java.util.Map.of("foo", "changed"), List.of("changed1", "changed2", "changed3"));
        sale = saleRepository.findById(sale.getId()).orElse(null);

        assertNotNull(sale);
        assertEquals("test 1", sale.getName());
        assertEquals(java.util.Map.of("foo", "changed"), sale.getData());
        assertEquals(List.of("changed1", "changed2", "changed3"), sale.getDataList());
    }

    @Test
    void testReadWriteJsonWithStringField() {
        Sale sale = new Sale();
        sale.setName("sale");
        String extraData = "{\"color\":\"blue\"}";
        sale.setExtraData(extraData);

        saleRepository.save(sale);

        assertEquals(extraData, saleRepository.findById(sale.getId()).orElseThrow().getExtraData());
    }

    @Test
    void testReadAndWriteJsonWithChildRows() {
        Sale sale = new Sale();
        sale.setName("test 1");
        sale = saleRepository.save(sale);
        List<SaleItem> items = saleItemRepository.saveAll(List.of(
            new SaleItem(null, sale, "item 1", java.util.Map.of("count", "1")),
            new SaleItem(null, sale, "item 2", java.util.Map.of("count", "2")),
            new SaleItem(null, sale, "item 3", java.util.Map.of("count", "3"))
        ));

        Sale saleById = saleRepository.findById(sale.getId()).orElse(null);
        assertNotNull(saleById);
        assertEquals("test 1", saleById.getName());
        assertEquals(new java.util.HashSet<>(items), saleById.getItems());
    }

    @Test
    void testReadAndWriteJsonWithConstructorArgs() {
        Sale sale = new Sale();
        sale.setName("test 1");
        sale = saleRepository.save(sale);
        SaleItem item = saleItemRepository.save(new SaleItem(null, sale, "item 1", java.util.Map.of("count", "1")));

        SaleItem itemById = saleItemRepository.findById(item.getId()).orElse(null);
        assertNotNull(itemById);
        assertEquals("item 1", itemById.getName());
        assertEquals(java.util.Map.of("count", "1"), itemById.getData());
        assertEquals(sale.getId(), itemById.getSale().getId());
    }

    @Test
    void testReadDtoFromJsonStringField() {
        Discount discount = new Discount();
        discount.setAmount(12d);
        discount.setNumberOfDays(5);
        discount.setNote("Valid since April 1st");

        Sale sale = new Sale();
        sale.setName("sale");
        sale.setExtraData("{\"amount\":12.0,\"numberOfDays\":5,\"note\":\"Valid since April 1st\"}");

        sale = saleRepository.save(sale);
        Optional<Sale> optSale = saleRepository.findById(sale.getId());
        Optional<Discount> optLoadedDiscount = saleRepository.getDiscountById(sale.getId());

        assertTrue(optSale.isPresent());
        assertTrue(optLoadedDiscount.isPresent());
        Discount loadedDiscount = optLoadedDiscount.orElseThrow();
        assertEquals(discount.getAmount(), loadedDiscount.getAmount());
        assertEquals(discount.getNote(), loadedDiscount.getNote());
        assertEquals(discount.getNumberOfDays(), loadedDiscount.getNumberOfDays());
    }

    @Test
    void testReadEntityFromJsonStringField() {
        Sale sale1 = new Sale();
        sale1.setName("sale1");
        sale1.setData(new LinkedHashMap<>(java.util.Map.of("sale1_field1", "value1")));
        sale1.setDataList(List.of("sale1_data1", "sale2_data2"));
        sale1.setQuantities(new LinkedHashMap<>(java.util.Map.of("sale1_item1", 3, "sale1_item2", 2)));
        sale1 = saleRepository.save(sale1);
        SaleItem item1 = saleItemRepository.save(new SaleItem(null, sale1, "sale1 item 1", java.util.Map.of("count", "1")));
        SaleItem item2 = saleItemRepository.save(new SaleItem(null, sale1, "sale1 item 2", java.util.Map.of("count", "2")));

        sale1 = saleRepository.findById(sale1.getId()).orElseThrow();
        sale1.setExtraData("{"
            + "\"id\":" + sale1.getId() + ","
            + "\"name\":\"sale1\","
            + "\"data\":{\"sale1_field1\":\"value1\"},"
            + "\"quantities\":{\"sale1_item1\":3,\"sale1_item2\":2},"
            + "\"dataList\":[\"sale1_data1\",\"sale2_data2\"],"
            + "\"items\":["
            + "{\"id\":" + item1.getId() + ",\"name\":\"sale1 item 1\",\"data\":{\"count\":\"1\"}},"
            + "{\"id\":" + item2.getId() + ",\"name\":\"sale1 item 2\",\"data\":{\"count\":\"2\"}}"
            + "]"
            + "}");
        saleRepository.update(sale1);

        List<Sale> loadedSales = saleRepository.findAllByNameFromJson(sale1.getName());
        assertEquals(1, loadedSales.size());
        verifySale(sale1, loadedSales.getFirst());

        Optional<Sale> optLoadedSale = saleRepository.findByNameFromJson(sale1.getName());
        assertTrue(optLoadedSale.isPresent());
        verifySale(sale1, optLoadedSale.orElseThrow());

        optLoadedSale = saleRepository.findByName(sale1.getName());
        assertTrue(optLoadedSale.isPresent());
        verifySale(sale1, optLoadedSale.orElseThrow());
    }

    @Test
    void testSaveUpdateIterable() {
        JsonEntity a = new JsonEntity();
        a.setId(1L);
        a.setValues(List.of("item1", "item2"));
        jsonEntityRepository.save(a);

        JsonEntity loaded = jsonEntityRepository.findById(1L).orElseThrow();
        List<String> loadedValues = new ArrayList<>();
        loaded.getValues().forEach(loadedValues::add);
        assertEquals(List.of("item1", "item2"), loadedValues);

        loadedValues.add("item3");
        loaded.setValues(loadedValues);
        jsonEntityRepository.update(loaded);
        loaded = jsonEntityRepository.findById(1L).orElseThrow();
        loadedValues = new ArrayList<>();
        loaded.getValues().forEach(loadedValues::add);
        assertEquals(List.of("item1", "item2", "item3"), loadedValues);

        JsonEntity b = jsonEntityRepository.save(2L, List.of("newitem1", "newitem2", "newitem3"));
        loaded = jsonEntityRepository.findById(2L).orElseThrow();
        List<String> bValues = new ArrayList<>();
        b.getValues().forEach(bValues::add);
        assertEquals(2L, b.getId());
        assertEquals(List.of("newitem1", "newitem2", "newitem3"), bValues);
        loadedValues = new ArrayList<>();
        loaded.getValues().forEach(loadedValues::add);
        assertEquals(List.of("newitem1", "newitem2", "newitem3"), loadedValues);

        loadedValues.set(1, "newitem2_updated");
        jsonEntityRepository.update(loaded.getId(), loadedValues);
        loaded = jsonEntityRepository.findById(2L).orElseThrow();
        loadedValues = new ArrayList<>();
        loaded.getValues().forEach(loadedValues::add);
        assertEquals(List.of("newitem1", "newitem2_updated", "newitem3"), loadedValues);
    }

    @Disabled("JSON FORMAT not supported")
    @Test
    void testJsonFieldsRetrieval() {
        JsonEntity jsonEntity = new JsonEntity();
        jsonEntity.setId(1L);
        SampleData sampleData = new SampleData();
        sampleData.setEtag(UUID.randomUUID().toString());
        sampleData.setMemo("memo".getBytes(Charset.defaultCharset()));
        sampleData.setUuid(UUID.randomUUID());
        sampleData.setDuration(Duration.ofHours(15));
        sampleData.setLocalDateTime(LocalDateTime.now());
        sampleData.setDescription("sample description");
        sampleData.setGrade(1);
        sampleData.setRating(9.75d);
        jsonEntity.setJsonDefault(sampleData);
        jsonEntity.setJsonBlob(sampleData);
        jsonEntity.setJsonString(sampleData);
        jsonEntityRepository.save(jsonEntity);

        Optional<SampleData> optSampleDataFromJsonDefault = jsonEntityRepository.findJsonDefaultById(jsonEntity.getId());
        Optional<SampleData> optSampleDataFromJsonString = jsonEntityRepository.findJsonStringById(jsonEntity.getId());
        Optional<SampleData> optSampleDataFromJsonBlob = jsonEntityRepository.findJsonBlobById(jsonEntity.getId());

        assertTrue(optSampleDataFromJsonDefault.isPresent() && optSampleDataFromJsonDefault.orElseThrow().equals(sampleData));
        assertTrue(optSampleDataFromJsonString.isPresent() && optSampleDataFromJsonString.orElseThrow().equals(sampleData));
        assertTrue(optSampleDataFromJsonBlob.isPresent() && optSampleDataFromJsonBlob.orElseThrow().equals(sampleData));

        JsonEntity loadedJsonEntity = jsonEntityRepository.findById(jsonEntity.getId()).orElseThrow();
        assertEquals(jsonEntity.getId(), loadedJsonEntity.getId());
        assertEquals(jsonEntity.getJsonString(), loadedJsonEntity.getJsonString());
        assertEquals(jsonEntity.getJsonBlob(), loadedJsonEntity.getJsonBlob());
        assertEquals(jsonEntity.getJsonDefault(), loadedJsonEntity.getJsonDefault());

        jsonEntity.getJsonString().setDescription("Updated via param");
        jsonEntity.getJsonBlob().setGrade(15);
        jsonEntityRepository.updateJsonStringById(jsonEntity.getId(), jsonEntity.getJsonString());
        jsonEntityRepository.updateJsonBlobById(jsonEntity.getId(), jsonEntity.getJsonBlob());
        optSampleDataFromJsonString = jsonEntityRepository.findJsonStringById(jsonEntity.getId());
        optSampleDataFromJsonBlob = jsonEntityRepository.findJsonBlobById(jsonEntity.getId());

        assertTrue(optSampleDataFromJsonString.isPresent());
        assertEquals("Updated via param", optSampleDataFromJsonString.orElseThrow().getDescription());
        assertTrue(optSampleDataFromJsonBlob.isPresent());
        assertEquals(15, optSampleDataFromJsonBlob.orElseThrow().getGrade());
    }

    private void verifySale(Sale actualSale, Sale expectedSale) {
        assertEquals(actualSale.getId(), expectedSale.getId());
        assertEquals(actualSale.getName(), expectedSale.getName());
        assertEquals(actualSale.getDataList(), expectedSale.getDataList());
        assertEquals(actualSale.getQuantities(), expectedSale.getQuantities());
        assertEquals(actualSale.getItems().size(), expectedSale.getItems().size());
    }
}
