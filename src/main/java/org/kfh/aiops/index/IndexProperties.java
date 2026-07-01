package org.kfh.aiops.index;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code kfh.index.*} tunables (docs/CAUSAL_PIPELINE.md §10). Storage path is the filesystem
 * root for shards; shards-per-day, write-batch-size, and search-parallelism tune throughput/latency;
 * retention-days is per telemetry kind (used by the increment-2 retention job).
 */
@Component
@ConfigurationProperties(prefix = "kfh.index")
public class IndexProperties {

    private Storage storage = new Storage();
    private int shardsPerDay = 4;
    private int writeBatchSize = 1000;
    private int searchParallelism = 8;
    private RetentionDays retentionDays = new RetentionDays();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public int getShardsPerDay() {
        return shardsPerDay;
    }

    public void setShardsPerDay(int shardsPerDay) {
        this.shardsPerDay = shardsPerDay;
    }

    public int getWriteBatchSize() {
        return writeBatchSize;
    }

    public void setWriteBatchSize(int writeBatchSize) {
        this.writeBatchSize = writeBatchSize;
    }

    public int getSearchParallelism() {
        return searchParallelism;
    }

    public void setSearchParallelism(int searchParallelism) {
        this.searchParallelism = searchParallelism;
    }

    public RetentionDays getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(RetentionDays retentionDays) {
        this.retentionDays = retentionDays;
    }

    /** Filesystem/object-storage location for index shards. */
    public static class Storage {
        private String provider = "FILESYSTEM";
        private String path = "/data/aiops-index";
        private String bucket = "";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    /** Retention window per telemetry kind (days). */
    public static class RetentionDays {
        private int alerts = 30;
        private int logs = 14;
        private int metrics = 7;
        private int traces = 7;
        private int changes = 90;

        public int getAlerts() {
            return alerts;
        }

        public void setAlerts(int alerts) {
            this.alerts = alerts;
        }

        public int getLogs() {
            return logs;
        }

        public void setLogs(int logs) {
            this.logs = logs;
        }

        public int getMetrics() {
            return metrics;
        }

        public void setMetrics(int metrics) {
            this.metrics = metrics;
        }

        public int getTraces() {
            return traces;
        }

        public void setTraces(int traces) {
            this.traces = traces;
        }

        public int getChanges() {
            return changes;
        }

        public void setChanges(int changes) {
            this.changes = changes;
        }
    }
}
