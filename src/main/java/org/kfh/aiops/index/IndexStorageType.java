package org.kfh.aiops.index;

import java.util.Locale;

/**
 * Where the custom index shards physically live. Filesystem types ({@link #LOCAL}, {@link #NFS},
 * {@link #SMB}, {@link #PVC}) all resolve to a {@code java.nio.file.Path}, so the engine works
 * unchanged across Linux, Windows (UNC/SMB), and OpenShift (PVC-mounted volume). Object-storage
 * types ({@link #S3}, {@link #AZURE_BLOB}) require a dedicated client (follow-up).
 */
public enum IndexStorageType {
    /** Local disk (Linux or Windows) or an OpenShift emptyDir/hostPath. */
    LOCAL,
    /** NFS mount (Linux or Windows client). */
    NFS,
    /** Windows SMB/CIFS share (UNC path), e.g. a Samba export. */
    SMB,
    /** OpenShift/Kubernetes PersistentVolumeClaim mount path. */
    PVC,
    /** Amazon S3 (or S3-compatible, e.g. MinIO). */
    S3,
    /** Azure Blob Storage. */
    AZURE_BLOB;

    public boolean isFilesystem() {
        return this == LOCAL || this == NFS || this == SMB || this == PVC;
    }

    /** Parse a provider string; fall back to {@code fallback}, then {@link #LOCAL}. */
    public static IndexStorageType from(String value, String fallback) {
        var parsed = parse(value);
        if (parsed != null) {
            return parsed;
        }
        var fb = parse(fallback);
        return fb != null ? fb : LOCAL;
    }

    private static IndexStorageType parse(String value) {
        if (value == null) {
            return null;
        }
        var v = value.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) {
            return null;
        }
        if (v.equals("FILESYSTEM") || v.equals("FILE") || v.equals("DISK")) {
            return LOCAL;
        }
        if (v.equals("AZURE") || v.equals("AZUREBLOB")) {
            return AZURE_BLOB;
        }
        try {
            return IndexStorageType.valueOf(v);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
