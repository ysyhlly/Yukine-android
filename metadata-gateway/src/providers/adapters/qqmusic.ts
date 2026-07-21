import {
  qqMusicSingerDetailRequest,
  qqMusicSingerSearchRequest,
  qqMusicSongLyricRequest,
  qqMusicSongSearchRequest
} from "../requests.js";
import type { MetadataProvider, ProviderSearchContext } from "../types.js";
import type { UpstreamJsonResult } from "../../types.js";
import { qqMusicResponseSchema } from "../../contracts/upstream.js";
import { validateUpstream } from "../validation.js";

export type QqMusicQuery =
  | { operation: "singer-search"; name: string }
  | { operation: "singer-detail"; singerMID: string }
  | { operation: "song-search"; title: string; artist?: string }
  | { operation: "song-lyric"; songMid: string };

export class QqMusicProvider
implements MetadataProvider<QqMusicQuery, UpstreamJsonResult> {
  readonly name = "qqmusic" as const;
  readonly capabilities = ["artist-enrichment", "lyrics-enrichment"] as const;

  async search(query: QqMusicQuery, context: ProviderSearchContext): Promise<UpstreamJsonResult> {
    const url = query.operation === "singer-search"
      ? qqMusicSingerSearchRequest(query.name)
      : query.operation === "singer-detail"
        ? qqMusicSingerDetailRequest(query.singerMID)
        : query.operation === "song-search"
          ? qqMusicSongSearchRequest(query.title, query.artist)
          : qqMusicSongLyricRequest(query.songMid);
    return validateUpstream(
      await context.requestJson(this.name, url),
      qqMusicResponseSchema
    );
  }
}
