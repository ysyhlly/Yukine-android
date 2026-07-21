import {
  qqMusicSingerDetailRequest,
  qqMusicSingerSearchRequest
} from "../requests.js";
import type { MetadataProvider, ProviderSearchContext } from "../types.js";
import type { UpstreamJsonResult } from "../../types.js";
import { qqMusicResponseSchema } from "../../contracts/upstream.js";
import { validateUpstream } from "../validation.js";

export type QqMusicQuery =
  | { operation: "singer-search"; name: string }
  | { operation: "singer-detail"; singerMID: string };

export class QqMusicProvider
implements MetadataProvider<QqMusicQuery, UpstreamJsonResult> {
  readonly name = "qqmusic" as const;
  readonly capabilities = ["artist-enrichment"] as const;

  async search(query: QqMusicQuery, context: ProviderSearchContext): Promise<UpstreamJsonResult> {
    return validateUpstream(
      await context.requestJson(
        this.name,
        query.operation === "singer-search"
          ? qqMusicSingerSearchRequest(query.name)
          : qqMusicSingerDetailRequest(query.singerMID)
      ),
      qqMusicResponseSchema
    );
  }
}
