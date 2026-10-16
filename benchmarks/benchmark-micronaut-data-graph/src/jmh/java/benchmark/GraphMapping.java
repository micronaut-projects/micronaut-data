package benchmark;

import example.BeanMovie;
import example.MixedMovie;
import example.RecordMovie;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.graph.runtime.GraphEntityMapper;
import io.micronaut.data.graph.runtime.GraphNode;
import io.micronaut.data.graph.runtime.serde.GraphSerde;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.serde.SerdeRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads nodes into entities with GraphEntityMapper and with Micronaut Serialization. The nodes are built in memory,
 * so the benchmark measures mapping alone. A record and a bean with a no-argument constructor use source-generated
 * deserializers; a class mixing constructor arguments with setters uses the runtime deserializer.
 */
@State(Scope.Benchmark)
public class GraphMapping {

    @Param({"record", "bean", "mixed"})
    String shape;

    @Param({"100"})
    int nodes;

    private ApplicationContext applicationContext;
    private GraphEntityMapper mapper;
    private GraphSerde serde;
    private RuntimePersistentEntity<?> entity;
    private List<GraphNode> values;

    @Setup
    public void prepare() {
        applicationContext = ApplicationContext.run();
        RuntimeEntityRegistry registry = applicationContext.getBean(RuntimeEntityRegistry.class);
        DataConversionService conversionService = applicationContext.getBean(DataConversionService.class);
        mapper = new GraphEntityMapper(registry, conversionService, value -> value);
        serde = new GraphSerde(applicationContext.getBean(SerdeRegistry.class), conversionService);
        Class<?> type = switch (shape) {
            case "record" -> RecordMovie.class;
            case "bean" -> BeanMovie.class;
            default -> MixedMovie.class;
        };
        entity = registry.getEntity(type);
        values = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; i++) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("title", "Movie " + i);
            properties.put("tagline", "Tagline of movie " + i);
            properties.put("released", 1990 + i % 30);
            properties.put("votes", 1000L + i);
            properties.put("rating", 7.5);
            properties.put("colour", true);
            properties.put("director", "Director " + i);
            properties.put("studio", "Studio " + i % 7);
            properties.put("budget", 10_000_000L + i);
            properties.put("premiere", LocalDate.of(2000, 1, 1).plusDays(i));
            values.add(new GraphNode("4:db:" + i, List.of("Movie"), properties));
        }
    }

    @TearDown
    public void close() {
        applicationContext.close();
    }

    @Benchmark
    public void mapper(Blackhole blackhole) {
        for (GraphNode node : values) {
            blackhole.consume(mapper.readNode(entity, node, null));
        }
    }

    @Benchmark
    public void serde(Blackhole blackhole) {
        for (GraphNode node : values) {
            blackhole.consume(serde.readNode(entity, node, null));
        }
    }
}
