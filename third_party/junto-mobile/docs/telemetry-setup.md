# Telemetry backend setup

> **Status: not built.** The client side is fully shipped — every `junto
> create`/`junto join` fires one anonymous POST to
> [`internal/telemetry/telemetry.go`](../internal/telemetry/telemetry.go)'s
> `Endpoint` (`https://telemetry.junto.watch/v1/event`), blocking up to 3s so
> delivery actually happens before the process exits. But **nothing has ever
> been deployed to receive it** — there's no DNS record, no server, no
> database, no dashboard. This doc is the checklist for standing that up. It
> is infrastructure/ops work, not a code change to this repo (except possibly
> a tiny collector service, which could live here or in the separate web
> repo — see [Where the collector lives](#where-the-collector-lives)).

Until this is done, telemetry is silently a no-op in effect: the client
POSTs, gets a DNS failure or connection refused, and swallows it exactly as
designed (`Fire`'s failure path is deliberately silent — see
`internal/telemetry/telemetry.go`). Nothing breaks for users; the feature
just collects nothing.

---

## What has to exist

1. **A DNS record** for `telemetry.junto.watch` pointing at whatever you
   pick below.
2. **A server that accepts `POST /v1/event`** over HTTPS and persists the
   body.
3. **Something to view what landed** — a dashboard, a SQL client, or a
   scheduled report — whatever fits the backend you choose.
4. **A decision on retention/access**, even though the payload is
   anonymous (see [Privacy constraints](#privacy-constraints-already-baked-in)).

Nothing else in the client needs to change. `Endpoint` is a package-level
`var` specifically so it's swappable without a code change if the URL ever
needs to move (`internal/telemetry/telemetry.go:26`).

---

## The wire contract (already shipped, don't change lightly)

`POST https://telemetry.junto.watch/v1/event`, `Content-Type:
application/json`, `User-Agent: junto/<version>`, JSON body:

```jsonc
{
  "event": "create",            // or "join" — which command fired it
  "version": "1.2.0",           // junto binary version
  "os": "darwin",                // runtime.GOOS
  "arch": "arm64",                // runtime.GOARCH
  "outcome": "success",          // "success" | "failure"
  "failure_reason": "nat_failure", // omitted on success; see categories below
  "relay_latency_ms": [42.1, 55.0], // omitted if no relay stats collected
  "relay_healthy": [true, true],    // same length/order as relay_latency_ms
  "ttff_ms": 850,                // omitted unless a streaming joiner; time-to-first-frame
  "transfer_failed": false       // omitted unless a P2P transfer was attempted
}
```

`failure_reason` is always one of a fixed, pre-classified set — never raw
error text, a file path, or a room code (`telemetry.ClassifyError`,
`internal/telemetry/telemetry.go`):

- `relay_unreachable`
- `nat_failure`
- `disk_full`
- `bad_room_code`
- `mpv_error`
- `user_canceled`
- `other` (anything that doesn't match a known category)

There is no auth, no cookie, no session identifier, and no room secret in
this payload or in any header — the collector should not try to correlate
requests across events. That's intentional (see below), not an oversight to
"fix" on the backend.

A response body is never read by the client (`resp.Body.Close()` is the only
thing done with it) — return any 2xx and you're done. Non-2xx / timeout /
DNS failure are all silently swallowed client-side, so there's no retry
pressure to worry about and no dead-letter handling required.

---

## Privacy constraints (already baked in — respect them)

The client was deliberately built so the collector *cannot* deanonymize a
user even if it wanted to:

- No IP-adjacent identifier, no persistent client ID, no cookies.
- No file names, file paths, or room codes ever appear in the payload
  (`ClassifyError` exists specifically to strip raw error text down to a
  fixed category before it leaves the machine).
- First run prints an explicit disclosure before ever sending anything
  (`telemetry.PrintNoticeIfFirstRun`): *"junto collects anonymous usage data
  (version, OS, outcome, relay latency). Disable: `--no-telemetry` or add
  `telemetry = false` to config.toml"*.
- Fully opt-out via `--no-telemetry` or `telemetry = false` in
  `~/.config/junto/config.toml` (now correctly enforced — see
  [CHANGELOG.md](../CHANGELOG.md) for the fix to that opt-out being
  silently droppable).

Whatever you build on the receiving end should uphold the same bar:

- **Don't log source IPs long-term.** If your platform logs them at the
  edge (most do, briefly, for abuse mitigation), don't pipe them into the
  same store as the event payload, and set the shortest retention your
  platform allows for those edge logs.
- **Don't add anything to the payload that isn't already in the contract
  above** (e.g. no IP geolocation join, no request timing correlation
  across events) — that would quietly undo the anonymity the client design
  promises.
- Keep the launch-strategy doc's framing in mind: junto is marketed as
  "privacy-first, serverless" (`docs/research/junto-foss-launch-strategy.md`)
  — a telemetry backend that looks like conventional product analytics
  undercuts that positioning even if the payload itself stays clean.

---

## Backend options (pick one)

Ranked by how little there is to operate, given the expected volume (one
small JSON POST per session, likely low-thousands/month at this stage — this
does not need anything built for scale).

### 1. Cloudflare Worker + D1 or Analytics Engine (recommended)

If `junto.watch`'s DNS is already on Cloudflare (it likely is, given the
existing `junto.watch/join` and `junto.watch/install` setup), this is the
lowest-effort path:

- The Worker *is* the DNS target — no separate server to provision, patch,
  or restart.
- **Analytics Engine** is purpose-built for exactly this shape (small
  events, queried in aggregate later) and has a generous free tier with
  zero ops.
- **D1** (SQLite at the edge) is the alternative if you want normal SQL and
  don't need Analytics Engine's time-series-specific query API.
- Setup: `telemetry.junto.watch` → CNAME to the Worker's route, Worker
  validates method/content-type/size and writes one row/data point per
  request, returns `204`.
- Viewing: Analytics Engine's SQL API (queryable via `wrangler` or a small
  script), or a static page hitting the same API for a lightweight
  dashboard later.

### 2. Small Go service on Fly.io / Railway + SQLite or Postgres

More control, still cheap, and this is already a Go codebase so the
handler is genuinely small — an `http.HandlerFunc` that decodes the JSON,
does minimal shape validation, and inserts a row:

```go
type event struct {
    Event, Version, OS, Arch, Outcome, FailureReason string
    RelayLatencyMs []float64
    RelayHealthy   []bool
    TTFFMs         *int64
    TransferFailed *bool
}
```

- Setup: `telemetry.junto.watch` → A/AAAA record to the instance (or a
  CNAME if the platform gives you one), instance runs the handler behind
  TLS (most of these platforms terminate TLS for you).
- Viewing: `litecli`/`psql` directly at first; a `/admin` summary page or a
  Grafana/Metabase pointed at the DB once there's enough data to make that
  worthwhile.
- This is the path if you'd rather own the schema and not depend on a
  vendor-specific query API.

### 3. Supabase (hosted Postgres + built-in table UI)

If you want a dashboard "for free" without writing a backend at all:

- A tiny edge function (Supabase Edge Functions, or a Cloudflare
  Worker/Vercel Function in front) validates and forwards the POST into a
  Supabase table insert — don't point the client directly at Supabase's
  REST API, since that would require exposing a project API key in the
  open-source client binary.
- Viewing: Supabase's built-in table editor / SQL editor dashboard,
  no extra tooling.
- Good fit if the priority is "see the data quickly" over "own the stack."

---

## Where the collector lives

Two reasonable choices, pick based on where you want the deploy pipeline:

- **The separate web repo** — it already owns the `junto.watch` DNS zone
  and the `/join`, `/install` routes (see `ROADMAP.md`'s Shipped section),
  so a Worker/function living there keeps all of junto.watch's
  infrastructure in one place and one deploy pipeline.
- **A new standalone repo** just for the collector — cleaner separation if
  you expect this to grow into something with its own release cadence
  (e.g. a dashboard UI, alerting, etc.) independent of the marketing site.

Either way, nothing in *this* repo (the CLI) needs to change — `Endpoint`
already points at the production URL and is a `var` specifically so it can
be repointed without a client release if you ever need to.

---

## Checklist

- [ ] Pick a backend (Cloudflare Worker+D1/Analytics Engine recommended;
      see options above).
- [ ] Decide which repo the collector code lives in.
- [ ] Implement `POST /v1/event`: validate method + `Content-Type:
      application/json` + a sane max body size, decode the JSON per the
      contract above, persist it, return `2xx`. No auth needed, no
      response body needed.
- [ ] Add the `telemetry.junto.watch` DNS record pointing at it.
- [ ] Verify against the real client: `go build -o junto ./cmd/junto &&
      ./junto create <file>` (with telemetry left enabled) and confirm one
      row/event lands in your store within a few seconds.
- [ ] Verify opt-out still works: `./junto create <file> --no-telemetry`
      sends nothing.
- [ ] Set a retention policy for raw edge/access logs (see
      [Privacy constraints](#privacy-constraints-already-baked-in)) —
      separate from the event payload's own (presumably indefinite,
      since it's anonymous) retention.
- [ ] Set up whatever "viewing" means for the backend you picked (query
      script, dashboard, or scheduled digest).
- [ ] Optional: a cheap alert if the event rate drops to ~0 unexpectedly
      (would have caught the client-side bug that made telemetry dead in
      production for the entire time it's existed so far, see
      [CHANGELOG.md](../CHANGELOG.md)).
