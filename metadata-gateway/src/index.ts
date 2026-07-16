interface Env {
  ACOUSTID_API_KEY?: string;
  APP_USER_AGENT?: string;
}

interface ArtistEvidence {
  id: string;
  name: string;
  sortName?: string;
}

interface RecordingEvidence {
  provider: string;
  id: string;
  title: string;
  artists: ArtistEvidence[];
  album?: string;
  durationMs?: number;
  isrc?: string;
  recordingMbid?: string;
  workMbid?: string;
  acoustId?: string;
  fingerprintVerified?: boolean;
  score: number;
}

const MB = "https://musicbrainz.org/ws/2/";
const ITUNES = "https://itunes.apple.com/search";
const ACOUSTID = "https://api.acoustid.org/v2/lookup";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== "GET") return json({ error: "method_not_allowed" }, 405);
    const url = new URL(request.url);
    try {
      if (url.pathname === "/health") return json({ ok: true, acoustid: Boolean(env.ACOUSTID_API_KEY) });
      if (url.pathname === "/v1/recordings/search") return recordings(url.searchParams, env);
      if (url.pathname === "/v1/artists/search") return artists(url.searchParams, env);
      return json({ error: "not_found" }, 404);
    } catch (error) {
      return json({ error: "upstream_failure", detail: safeError(error) }, 502);
    }
  }
};

async function recordings(params: URLSearchParams, env: Env): Promise<Response> {
  const title = bounded(params.get("title"), 300);
  const artist = bounded(params.get("artist"), 300);
  const recordingMbid = uuid(params.get("recordingMbid"));
  const isrc = bounded(params.get("isrc"), 32).replace(/[^a-z0-9]/gi, "").toUpperCase();
  const fingerprint = bounded(params.get("fingerprint"), 16_384);
  const fingerprintDuration = integer(params.get("fingerprintDuration"), 1, 7_200);
  const limit = integer(params.get("limit"), 1, 25) || 12;
  if (!title && !recordingMbid && !isrc && !fingerprint) return json({ error: "missing_query" }, 400);

  const headers = upstreamHeaders(env);
  const evidence: RecordingEvidence[] = [];
  if (recordingMbid) {
    const body = await fetchJson(`${MB}recording/${recordingMbid}?inc=artists+isrcs+releases+work-rels&fmt=json`, headers);
    if (body) evidence.push(...mapMbRecordings([body], true));
  }
  if (evidence.length === 0 && isrc) {
    const body = await fetchJson(`${MB}isrc/${encodeURIComponent(isrc)}?inc=artist-credits+releases+work-rels&fmt=json`, headers);
    evidence.push(...mapMbRecordings(array(body, "recordings"), true));
  }
  if (evidence.length === 0 && fingerprint && fingerprintDuration && env.ACOUSTID_API_KEY) {
    evidence.push(...await acoustIdLookup(fingerprint, fingerprintDuration, env.ACOUSTID_API_KEY, headers));
  }
  if (evidence.length === 0 && title) {
    const clauses = [`recording:\"${escapeLucene(title)}\"`];
    if (artist) clauses.push(`artist:\"${escapeLucene(artist)}\"`);
    const body = await fetchJson(
      `${MB}recording/?query=${encodeURIComponent(clauses.join(" AND "))}&limit=${limit}&fmt=json`,
      headers
    );
    evidence.push(...mapMbRecordings(array(body, "recordings"), false));
  }
  if (title && evidence.length === 0) evidence.push(...await itunesLookup(title, artist, limit, headers));
  return json({ recordings: dedupeRecordings(evidence).slice(0, limit) }, 200, 86_400);
}

async function artists(params: URLSearchParams, env: Env): Promise<Response> {
  const name = bounded(params.get("name"), 300);
  const artistMbid = uuid(params.get("artistMbid"));
  const limit = integer(params.get("limit"), 1, 25) || 10;
  if (!name && !artistMbid) return json({ error: "missing_query" }, 400);
  const headers = upstreamHeaders(env);
  let values: unknown[] = [];
  if (artistMbid) {
    const body = await fetchJson(`${MB}artist/${artistMbid}?inc=aliases+url-rels&fmt=json`, headers);
    if (body) values = [body];
  }
  if (values.length === 0 && name) {
    const query = encodeURIComponent(`artist:\"${escapeLucene(name)}\"`);
    values = array(await fetchJson(`${MB}artist/?query=${query}&limit=${limit}&fmt=json`, headers), "artists");
  }
  const result = values.map((raw) => {
    const item = object(raw);
    const relations = array(item, "relations");
    const wikidata = relations.map(object).find((relation) => relation.type === "wikidata");
    return {
      provider: "musicbrainz",
      id: string(item.id),
      name: string(item.name),
      sortName: string(item["sort-name"]),
      aliases: array(item, "aliases").map((value) => string(object(value).name)).filter(Boolean),
      country: string(item.country),
      type: string(item.type).toUpperCase(),
      artistMbid: string(item.id),
      wikidataUrl: string(object(wikidata?.url).resource),
      score: number(item.score, artistMbid ? 100 : 0) / 100
    };
  }).filter((item) => item.id && item.name);
  return json({ artists: result }, 200, 86_400);
}

async function acoustIdLookup(
  fingerprint: string,
  duration: number,
  key: string,
  headers: HeadersInit
): Promise<RecordingEvidence[]> {
  const query = new URLSearchParams({
    client: key,
    duration: String(duration),
    fingerprint,
    meta: "recordings+recordingids+releasegroups+compress",
    format: "json"
  });
  const root = object(await fetchJson(`${ACOUSTID}?${query}`, headers));
  if (root.status !== "ok") return [];
  const output: RecordingEvidence[] = [];
  for (const rawResult of array(root, "results")) {
    const result = object(rawResult);
    const confidence = number(result.score, 0);
    const acoustId = string(result.id);
    for (const rawRecording of array(result, "recordings")) {
      const recording = object(rawRecording);
      const mbid = string(recording.id);
      if (!mbid || !string(recording.title)) continue;
      output.push({
        provider: "acoustid",
        id: acoustId || mbid,
        title: string(recording.title),
        artists: array(recording, "artists").map((raw) => {
          const artist = object(raw);
          return { id: string(artist.id), name: string(artist.name) };
        }),
        album: string(object(array(recording, "releasegroups")[0]).title),
        durationMs: duration * 1_000,
        recordingMbid: mbid,
        acoustId,
        fingerprintVerified: true,
        score: confidence
      });
    }
  }
  return output;
}

function mapMbRecordings(values: unknown[], exact: boolean): RecordingEvidence[] {
  return values.map((raw) => {
    const item = object(raw);
    const credits = array(item, "artist-credit");
    const relations = array(item, "relations").map(object);
    const work = relations.find((relation) => relation.type === "performance");
    return {
      provider: "musicbrainz",
      id: string(item.id),
      title: string(item.title),
      artists: credits.map((rawCredit) => {
        const credit = object(rawCredit);
        const artist = object(credit.artist);
        return { id: string(artist.id), name: string(credit.name) || string(artist.name), sortName: string(artist["sort-name"]) };
      }),
      album: string(object(array(item, "releases")[0]).title),
      durationMs: number(item.length, 0),
      isrc: string(array(item, "isrcs")[0]),
      recordingMbid: string(item.id),
      workMbid: string(object(work?.work).id),
      score: exact ? 1 : number(item.score, 0) / 100
    };
  }).filter((item) => item.id && item.title);
}

async function itunesLookup(title: string, artist: string, limit: number, headers: HeadersInit): Promise<RecordingEvidence[]> {
  const term = [title, artist].filter(Boolean).join(" ");
  const url = `${ITUNES}?media=music&entity=song&limit=${limit}&term=${encodeURIComponent(term)}`;
  const body = await fetchJson(url, headers);
  return array(body, "results").map((raw, index) => {
    const item = object(raw);
    return {
      provider: "itunes",
      id: string(item.trackId),
      title: string(item.trackName),
      artists: [{ id: string(item.artistId), name: string(item.artistName) }],
      album: string(item.collectionName),
      durationMs: number(item.trackTimeMillis, 0),
      score: Math.max(0, 0.75 - index * 0.01)
    };
  }).filter((item) => item.id && item.title);
}

function dedupeRecordings(values: RecordingEvidence[]): RecordingEvidence[] {
  const seen = new Set<string>();
  return values
    .sort((a, b) => b.score - a.score)
    .filter((value) => {
      const key = value.recordingMbid || `${value.provider}:${value.id}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

async function fetchJson(url: string, headers: HeadersInit): Promise<unknown | null> {
  const response = await fetch(url, { headers, cf: { cacheTtl: 3_600, cacheEverything: true } });
  if (!response.ok) return null;
  return response.json();
}

function upstreamHeaders(env: Env): HeadersInit {
  return { Accept: "application/json", "User-Agent": env.APP_USER_AGENT || "ECHO-Metadata-Gateway/1.0" };
}

function json(body: unknown, status = 200, maxAge = 0): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": maxAge > 0 ? `public, max-age=${maxAge}` : "no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

function bounded(value: string | null, max: number): string { return (value || "").trim().slice(0, max); }
function uuid(value: string | null): string { const v = bounded(value, 64).toLowerCase(); return /^[0-9a-f-]{36}$/.test(v) ? v : ""; }
function integer(value: string | null, min: number, max: number): number { const v = Number.parseInt(value || "", 10); return Number.isFinite(v) && v >= min && v <= max ? v : 0; }
function escapeLucene(value: string): string { return value.replace(/\\/g, "\\\\").replace(/\"/g, "\\\""); }
function object(value: unknown): Record<string, unknown> { return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function array(value: unknown, key?: string): unknown[] { const target = key ? object(value)[key] : value; return Array.isArray(target) ? target : []; }
function string(value: unknown): string { return value === null || value === undefined ? "" : String(value); }
function number(value: unknown, fallback: number): number { const result = Number(value); return Number.isFinite(result) ? result : fallback; }
function safeError(error: unknown): string { return error instanceof Error ? error.message.slice(0, 300) : "unknown"; }
