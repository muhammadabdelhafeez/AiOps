package org.kfh.aiops.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.springframework.stereotype.Component;

/**
 * Append-only segment I/O for one shard. Each shard directory holds a single {@code segment.jsonl}
 * of newline-delimited JSON documents — sequential appends (fast, no random IO), read back by a full
 * scan. The in-shard inverted index that turns this scan into O(1) term lookups is increment 2.
 */
@Component
public class SegmentStore {

    public static final String SEGMENT_FILE = "segment.jsonl";

    private final ObjectMapper mapper;
    private final Map<Path, Object> locks = new ConcurrentHashMap<>();

    public SegmentStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Append a batch of documents to the shard's segment, creating the directory if needed. */
    public void append(Path shardDir, List<TelemetryDocument> docs) {
        if (docs.isEmpty()) {
            return;
        }
        var segment = shardDir.resolve(SEGMENT_FILE);
        synchronized (lockFor(segment)) {
            try {
                Files.createDirectories(shardDir);
                var sb = new StringBuilder();
                for (var doc : docs) {
                    sb.append(mapper.writeValueAsString(doc)).append('\n');
                }
                Files.writeString(segment, sb.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to append index segment " + segment, ex);
            }
        }
    }

    /** Byte size of the shard's segment (-1 if none) — used for index-cache invalidation. */
    public long segmentSize(Path shardDir) {
        var segment = shardDir.resolve(SEGMENT_FILE);
        try {
            return Files.isRegularFile(segment) ? Files.size(segment) : -1L;
        } catch (IOException ex) {
            return -1L;
        }
    }

    /** Read every document in a shard (empty if the shard has no segment yet). */
    public List<TelemetryDocument> readShard(Path shardDir) {
        var segment = shardDir.resolve(SEGMENT_FILE);
        if (!Files.isRegularFile(segment)) {
            return List.of();
        }
        var docs = new ArrayList<TelemetryDocument>();
        try (Stream<String> lines = Files.lines(segment, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (!line.isBlank()) {
                    var doc = parse(line);
                    if (doc != null) {
                        docs.add(doc);
                    }
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read index segment " + segment, ex);
        }
        return docs;
    }

    private TelemetryDocument parse(String line) {
        try {
            return mapper.readValue(line, TelemetryDocument.class);
        } catch (IOException ex) {
            // Skip a corrupt line rather than fail the whole shard read.
            return null;
        }
    }

    private Object lockFor(Path segment) {
        return locks.computeIfAbsent(segment.toAbsolutePath().normalize(), key -> new Object());
    }
}
