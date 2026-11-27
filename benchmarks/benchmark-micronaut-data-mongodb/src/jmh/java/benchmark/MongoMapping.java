package benchmark;

import com.mongodb.MongoClientSettings;
import example.BeanMovie;
import example.RecordMovie;
import io.micronaut.context.ApplicationContext;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.ObjectId;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads entities from BSON documents and writes entities to BSON documents through the codec Micronaut Data
 * registers with the MongoDB driver. The documents are built in memory, so the benchmark measures mapping alone and
 * needs no database. Run it on two commits to compare the runtime serde with source-generated serdes.
 */
@State(Scope.Benchmark)
public class MongoMapping {

    private static final DecoderContext DECODER_CONTEXT = DecoderContext.builder().build();
    private static final EncoderContext ENCODER_CONTEXT = EncoderContext.builder().build();

    @Param({"record", "bean"})
    String shape;

    @Param({"100"})
    int documents;

    private ApplicationContext applicationContext;
    private Codec<Object> codec;
    private List<Object> entities;
    private List<byte[]> bson;

    @Setup
    @SuppressWarnings("unchecked")
    public void prepare() {
        // The client settings, and with them the codec registry, are created without connecting
        applicationContext = ApplicationContext.run(Map.of("mongodb.uri", "mongodb://localhost:27017"));
        MongoClientSettings settings = applicationContext.getBean(MongoClientSettings.class);
        Class<?> type = "record".equals(shape) ? RecordMovie.class : BeanMovie.class;
        codec = (Codec<Object>) settings.getCodecRegistry().get(type);
        entities = new ArrayList<>(documents);
        bson = new ArrayList<>(documents);
        for (int i = 0; i < documents; i++) {
            Object entity = "record".equals(shape) ? recordMovie(i) : beanMovie(i);
            entities.add(entity);
            bson.add(encode(entity));
        }
        Object decoded = decode(bson.get(0));
        if (!type.isInstance(decoded)) {
            throw new IllegalStateException("Decoded " + decoded + " is not a " + type);
        }
    }

    @TearDown
    public void close() {
        applicationContext.close();
    }

    @Benchmark
    public void read(Blackhole blackhole) {
        for (byte[] document : bson) {
            blackhole.consume(decode(document));
        }
    }

    @Benchmark
    public void write(Blackhole blackhole) {
        for (Object entity : entities) {
            blackhole.consume(encode(entity));
        }
    }

    private Object decode(byte[] document) {
        try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(document))) {
            return codec.decode(reader, DECODER_CONTEXT);
        }
    }

    private byte[] encode(Object entity) {
        BasicOutputBuffer buffer = new BasicOutputBuffer(512);
        try (BsonBinaryWriter writer = new BsonBinaryWriter(buffer)) {
            codec.encode(writer, entity, ENCODER_CONTEXT);
        }
        return buffer.toByteArray();
    }

    private static RecordMovie recordMovie(int i) {
        return new RecordMovie(new ObjectId().toHexString(), "Movie " + i, "Tagline of movie " + i, 1990 + i % 30,
            1000L + i, 7.5, true, "Director " + i, "Studio " + i % 7, 10_000_000L + i,
            LocalDate.of(2000, 1, 1).plusDays(i));
    }

    private static BeanMovie beanMovie(int i) {
        BeanMovie movie = new BeanMovie();
        movie.setId(new ObjectId().toHexString());
        movie.setTitle("Movie " + i);
        movie.setTagline("Tagline of movie " + i);
        movie.setReleased(1990 + i % 30);
        movie.setVotes(1000L + i);
        movie.setRating(7.5);
        movie.setColour(true);
        movie.setDirector("Director " + i);
        movie.setStudio("Studio " + i % 7);
        movie.setBudget(10_000_000L + i);
        movie.setPremiere(LocalDate.of(2000, 1, 1).plusDays(i));
        return movie;
    }
}
