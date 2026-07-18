package app.yukine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.identity.ArtistAlias;
import app.yukine.identity.ArtistType;
import app.yukine.identity.CanonicalArtist;
import app.yukine.model.Track;

/** Builds the artist page from Room only; remote enrichment belongs to background workers. */
final class RoomArtistLocalInfoSource implements ArtistLocalInfoSource {
    private static final int MAX_VISIBLE_ALIASES = 6;

    private final MusicLibraryRepository repository;

    RoomArtistLocalInfoSource(MusicLibraryRepository repository) {
        this.repository = repository;
    }

    @Override
    public ArtistInfo load(String languageMode, String artistId, List<Track> tracks) {
        CanonicalArtist artist = repository.loadCanonicalArtist(artistId);
        if (artist == null) {
            return null;
        }
        boolean english = AppLanguage.MODE_ENGLISH.equals(languageMode);
        List<String> aliases = visibleAliases(artist, repository.loadArtistAliases(artistId));
        String summary = summary(artist, aliases, english);
        String source = english ? "Local identity database" : "本地身份数据库";
        if (!artist.getMetadataSource().isBlank()) {
            source += " · " + artist.getMetadataSource();
        }
        return new ArtistInfo(
                artist.getDisplayName(),
                source,
                summary,
                Collections.emptyList(),
                false,
                artist.getAvatarUrl().isBlank() ? null : artist.getAvatarUrl()
        );
    }

    private static List<String> visibleAliases(CanonicalArtist artist, List<ArtistAlias> values) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String primary = artist.getDisplayName().trim().toLowerCase(Locale.ROOT);
        for (ArtistAlias value : values) {
            String alias = value.getAlias().trim();
            if (!alias.isEmpty() && !alias.toLowerCase(Locale.ROOT).equals(primary)) {
                aliases.add(alias);
            }
            if (aliases.size() >= MAX_VISIBLE_ALIASES) {
                break;
            }
        }
        return new ArrayList<>(aliases);
    }

    private static String summary(CanonicalArtist artist, List<String> aliases, boolean english) {
        ArrayList<String> details = new ArrayList<>();
        if (artist.getArtistType() != ArtistType.UNKNOWN) {
            details.add(english
                    ? "Type: " + artist.getArtistType().name().toLowerCase(Locale.ROOT)
                    : "类型：" + chineseType(artist.getArtistType()));
        }
        if (!artist.getCountryCode().isBlank()) {
            details.add((english ? "Country/region: " : "国家/地区：") + artist.getCountryCode());
        }
        if (!aliases.isEmpty()) {
            details.add((english ? "Aliases: " : "别名：") + String.join("、", aliases));
        }
        String description = artist.getDescription().trim();
        String prefix = description.isEmpty()
                ? (english
                    ? artist.getDisplayName() + " is linked to a stable local artist identity."
                    : artist.getDisplayName() + " 已关联稳定的本地艺人身份。")
                : description;
        if (details.isEmpty()) {
            if (!description.isEmpty()) {
                return prefix;
            }
            return prefix + (english
                    ? " Background enrichment can add verified metadata later without blocking this page."
                    : "后台增强可在后续补充已验证资料，不会阻塞当前页面。");
        }
        return prefix + " " + String.join(english ? "; " : "；", details) + "。";
    }

    private static String chineseType(ArtistType type) {
        switch (type) {
            case PERSON:
                return "个人";
            case GROUP:
                return "组合";
            case VIRTUAL:
                return "虚拟艺人";
            case CHARACTER:
                return "角色";
            case ORCHESTRA:
                return "管弦乐团";
            case CHOIR:
                return "合唱团";
            default:
                return "未知";
        }
    }
}
