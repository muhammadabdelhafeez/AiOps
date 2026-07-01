package org.kfh.aiops.ingestion.bmc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BmcHelixClientTest {

    private final BmcHelixClient client = new BmcHelixClient(new BmcProperties(), new ObjectMapper());

    @Test
    void parsesDirectHitsFormatAndMergesId() {
        var body = """
                {"took":45,"timed_out":false,"hits":{"total":{"value":1,"relation":"eq"},"hits":[
                  {"_index":"events-2026.01.18","_id":"abc123","_source":{
                     "creation_time":1737216000000,"severity":"CRITICAL","status":"OPEN",
                     "class":"APPLICATION_ERROR","source_hostname":"server01.domain.com",
                     "msg":"Database connection timeout","alert_name":"DB_TIMEOUT",
                     "_service_name":"Oracle DB"}}]}}
                """;

        var events = client.parseHits(body);

        assertThat(events).hasSize(1);
        assertThat(events.get(0))
                .containsEntry("_id", "abc123")
                .containsEntry("severity", "CRITICAL")
                .containsEntry("source_hostname", "server01.domain.com");
    }

    @Test
    void parsesMultiSearchResponsesFormat() {
        var body = """
                {"responses":[{"hits":{"total":{"value":1},"hits":[
                  {"_id":"xyz789","_source":{"severity":"MAJOR","source_hostname":"h2","msg":"m"}}]}}]}
                """;

        var events = client.parseHits(body);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("_id", "xyz789").containsEntry("severity", "MAJOR");
    }

    @Test
    void returnsEmptyForBlankOrEmptyBody() {
        assertThat(client.parseHits(null)).isEmpty();
        assertThat(client.parseHits("")).isEmpty();
        assertThat(client.parseHits("{\"hits\":{\"hits\":[]}}")).isEmpty();
    }

    @Test
    void buildsTimeWindowAndSeverityFilterQuery() {
        var query = BmcHelixClient.buildQuery(500, 30);

        assertThat(query).containsEntry("size", 500).containsKey("query").containsKey("sort");
        @SuppressWarnings("unchecked")
        var bool = (Map<String, Object>) ((Map<String, Object>) query.get("query")).get("bool");
        @SuppressWarnings("unchecked")
        var filters = (List<Object>) bool.get("filter");
        assertThat(filters).hasSize(2);
        assertThat(query.get("query").toString()).contains("now-30m");
    }
}
