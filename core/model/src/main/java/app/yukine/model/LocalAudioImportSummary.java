package app.yukine.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalAudioImportSummary {
    public static final LocalAudioImportSummary EMPTY =
            new LocalAudioImportSummary(0, 0, Collections.emptyMap(), Collections.emptyMap());

    private final int importedCount;
    private final int skippedCount;
    private final Map<String, Integer> skippedFormatCounts;
    private final Map<LocalAudioSkipReason, Integer> skippedReasonCounts;

    public LocalAudioImportSummary(
            int importedCount,
            int skippedCount,
            Map<String, Integer> skippedFormatCounts,
            Map<LocalAudioSkipReason, Integer> skippedReasonCounts
    ) {
        this.importedCount = Math.max(0, importedCount);
        this.skippedCount = Math.max(0, skippedCount);
        this.skippedFormatCounts = immutableCopy(skippedFormatCounts);
        this.skippedReasonCounts = Collections.unmodifiableMap(
                new LinkedHashMap<>(skippedReasonCounts == null ? Collections.emptyMap() : skippedReasonCounts)
        );
    }

    public int importedCount() {
        return importedCount;
    }

    public int skippedCount() {
        return skippedCount;
    }

    public Map<String, Integer> skippedFormatCounts() {
        return skippedFormatCounts;
    }

    public Map<LocalAudioSkipReason, Integer> skippedReasonCounts() {
        return skippedReasonCounts;
    }

    public boolean isEmpty() {
        return importedCount == 0 && skippedCount == 0;
    }

    private static Map<String, Integer> immutableCopy(Map<String, Integer> source) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(source == null ? Collections.emptyMap() : source)
        );
    }

    public static final class Builder {
        private int importedCount;
        private int skippedCount;
        private final LinkedHashMap<String, Integer> skippedFormats = new LinkedHashMap<>();
        private final LinkedHashMap<LocalAudioSkipReason, Integer> skippedReasons =
                new LinkedHashMap<>();

        public Builder record(LocalAudioDecision decision) {
            if (decision == null || decision.support() == LocalAudioSupport.NOT_AUDIO) {
                return this;
            }
            if (decision.shouldImport()) {
                importedCount++;
                return this;
            }
            skippedCount++;
            skippedFormats.merge(decision.format().displayName(), 1, Integer::sum);
            LocalAudioSkipReason reason = decision.skipReason() == null
                    ? LocalAudioSkipReason.UNSUPPORTED_FORMAT
                    : decision.skipReason();
            skippedReasons.merge(reason, 1, Integer::sum);
            return this;
        }

        public LocalAudioImportSummary build() {
            if (importedCount == 0 && skippedCount == 0) {
                return EMPTY;
            }
            return new LocalAudioImportSummary(
                    importedCount,
                    skippedCount,
                    skippedFormats,
                    skippedReasons
            );
        }
    }
}
