package org.kfh.aiops.ingestion.bmc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Thin client for the BMC Helix Events REST API (docs/BMC_Helix_response.md). Two calls:
 * <ol>
 *   <li>{@code POST {loginEndpoint}} with the access key/secret → JWT;</li>
 *   <li>{@code POST {eventsEndpoint}} (Bearer JWT) with an Elasticsearch-style query → events.</li>
 * </ol>
 * The WebClient uses TCP keep-alive + background eviction so idle-killed connections don't surface as
 * sporadic 504s between the 20-minute polls. Response parsing tolerates both the direct {@code hits}
 * and the multi-search {@code responses[0].hits} shapes.
 */
@Component
public class BmcHelixClient {

    private static final Logger log = LoggerFactory.getLogger(BmcHelixClient.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 120;
    private static final int WRITE_TIMEOUT_SECONDS = 30;

    private final BmcProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public BmcHelixClient(BmcProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient(properties);
    }

    private static WebClient buildWebClient(BmcProperties properties) {
        var connectionProvider = ConnectionProvider.builder("bmc-ingest-pool")
                .maxConnections(10)
                .maxIdleTime(Duration.ofSeconds(60))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        var httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_SECONDS * 1000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .responseTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .keepAlive(true)
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    conn.addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                })
                .compress(true);

        var builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        return builder.build();
    }

    /** Authenticate and return the JWT for subsequent event calls. */
    public String authenticate() {
        var request = Map.of(
                "access_key", properties.getAccessKey(),
                "access_secret_key", properties.getAccessSecretKey());
        String body = webClient.post()
                .uri(properties.getLoginEndpoint())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .block();
        try {
            var jwt = objectMapper.readTree(body == null ? "" : body).path("json_web_token").asText(null);
            if (jwt == null || jwt.isBlank()) {
                throw new IllegalStateException("BMC login returned no json_web_token");
            }
            return jwt;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("BMC authentication failed: " + ex.getMessage(), ex);
        }
    }

    /** Authenticate, then fetch raw event {@code _source} maps for the last {@code minutesBack}. */
    public List<Map<String, Object>> fetchRawEvents(int minutesBack, int maxEvents) {
        var jwt = authenticate();
        String body = webClient.post()
                .uri(properties.getEventsEndpoint())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .bodyValue(buildQuery(maxEvents, minutesBack))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .block();
        var events = parseHits(body);
        log.info("BMC Helix returned {} events (window={}m, max={})", events.size(), minutesBack, maxEvents);
        return events;
    }

    /** Extract {@code _source} maps (with {@code _id} merged in) from either response shape. */
    List<Map<String, Object>> parseHits(String body) {
        List<Map<String, Object>> events = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return events;
        }
        try {
            var root = objectMapper.readTree(body);
            var hits = root.path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) {
                hits = root.path("responses").path(0).path("hits").path("hits");
            }
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    var source = hit.path("_source");
                    if (source.isMissingNode() || !source.isObject()) {
                        continue;
                    }
                    Map<String, Object> event = objectMapper.convertValue(source, new TypeReference<>() {
                    });
                    var id = hit.path("_id").asText(null);
                    if (id != null && !id.isBlank()) {
                        event.putIfAbsent("_id", id);
                    }
                    events.add(event);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse BMC events response: " + ex.getMessage(), ex);
        }
        return events;
    }

    /** Elasticsearch-style query: last {@code minutes}, exclude severity OK, newest first. */
    static Map<String, Object> buildQuery(int size, int minutes) {
        var rangeFilter = Map.<String, Object>of("range",
                Map.of("creation_time", Map.of("gte", "now-" + minutes + "m", "lte", "now")));
        var severityFilter = Map.<String, Object>of("query_string",
                Map.of("analyze_wildcard", true, "query", "!severity:OK"));
        var bool = Map.<String, Object>of("bool", Map.of("filter", List.of(rangeFilter, severityFilter)));
        var sort = Map.<String, Object>of("creation_time", Map.of("order", "desc", "unmapped_type", "boolean"));

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("size", size);
        query.put("query", bool);
        query.put("sort", sort);
        query.put("script_fields", Map.of());
        return query;
    }
}
