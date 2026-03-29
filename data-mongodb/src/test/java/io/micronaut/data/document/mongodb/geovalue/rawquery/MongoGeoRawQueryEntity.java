package io.micronaut.data.document.mongodb.geovalue.rawquery;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed;
import io.micronaut.data.mongodb.geo.MongoGeoMultiPoint;

@MappedEntity("geo_raw_query_entities")
public class MongoGeoRawQueryEntity {

    @Id
    @GeneratedValue
    private String id;

    @TypeDef(type = DataType.OBJECT)
    @MongoGeoIndexed(name = "geo_raw_query_idx")
    private MongoGeoMultiPoint locations;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MongoGeoMultiPoint getLocations() {
        return locations;
    }

    public void setLocations(MongoGeoMultiPoint locations) {
        this.locations = locations;
    }
}
