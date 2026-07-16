package app.yukine.data.room;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative, network-free parser for raw track artist credits. */
public final class ArtistCreditParser {
    private static final String UNKNOWN_ARTIST = "未知艺人";
    private static final Pattern FEATURE_MARKER = Pattern.compile(
            "(?iu)\\s+(feat\\.?|ft\\.?|featuring|with)\\s+"
    );
    private static final Pattern PARALLEL_SEPARATOR = Pattern.compile(
            "(?iu)\\s*(?:/|、|,|×|\\band\\b)\\s*"
    );
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ArtistCreditParser() {
    }

    public static List<Credit> parse(String rawValue) {
        String raw = clean(rawValue);
        if (raw.isEmpty() || isUnknown(raw)) {
            return List.of(new Credit(raw.isEmpty() ? UNKNOWN_ARTIST : raw, "UNKNOWN", 0, ""));
        }

        Matcher feature = FEATURE_MARKER.matcher(raw);
        String primaryPart = raw;
        String featuredPart = "";
        String featureJoin = "";
        if (feature.find()) {
            primaryPart = clean(raw.substring(0, feature.start()));
            featuredPart = clean(raw.substring(feature.end()));
            featureJoin = " " + clean(feature.group(1)) + " ";
        }

        Map<String, Credit> credits = new LinkedHashMap<>();
        appendCredits(credits, primaryPart, "PRIMARY", "");
        appendCredits(credits, featuredPart, "FEATURED", featureJoin);
        if (credits.isEmpty()) {
            credits.put(normalizeAlias(raw), new Credit(raw, "UNKNOWN", 0, ""));
        }

        ArrayList<Credit> positioned = new ArrayList<>(credits.size());
        int position = 0;
        for (Credit credit : credits.values()) {
            positioned.add(new Credit(credit.name, credit.role, position++, credit.joinPhrase));
        }
        return positioned;
    }

    public static String normalizeAlias(String value) {
        String normalized = Normalizer.normalize(clean(value), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('，', ',')
                .replace('／', '/')
                .replace('＆', '&');
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

    private static void appendCredits(
            Map<String, Credit> credits,
            String value,
            String role,
            String joinPhrase
    ) {
        if (value.isEmpty()) {
            return;
        }
        for (String token : PARALLEL_SEPARATOR.split(value)) {
            String name = clean(token);
            String normalized = normalizeAlias(name);
            if (normalized.isEmpty() || credits.containsKey(normalized)) {
                continue;
            }
            credits.put(
                    normalized,
                    new Credit(name, isUnknown(name) ? "UNKNOWN" : role, 0, joinPhrase)
            );
        }
    }

    private static boolean isUnknown(String value) {
        String normalized = normalizeAlias(value);
        return normalized.equals("various artists")
                || normalized.equals("unknown artist")
                || normalized.equals("<unknown>")
                || normalized.equals("未知艺人")
                || normalized.equals("未知艺术家");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Credit {
        public final String name;
        public final String role;
        public final int position;
        public final String joinPhrase;

        Credit(String name, String role, int position, String joinPhrase) {
            this.name = name;
            this.role = role;
            this.position = position;
            this.joinPhrase = joinPhrase;
        }
    }
}
