# ECHO Metadata Gateway

This Cloudflare Worker exposes the normalized API consumed by ECHO Android. It keeps provider
credentials off the phone and sends only title/artist/identifier metadata plus an optional
Chromaprint and duration. Raw audio is never accepted.

## Deploy

```bash
npm install
npx wrangler secret put ACOUSTID_API_KEY
npm run check
npm run deploy
```

Then build Android with:

```bash
./gradlew :app:assembleDebug -PECHO_METADATA_GATEWAY_URL=https://your-worker.example/
```

The compatible endpoints are:

- `GET /v1/recordings/search`
- `GET /v1/artists/search`
- `GET /health`

The recording route evaluates exact Recording MBID, ISRC, Chromaprint/AcoustID, MusicBrainz
title/artist and iTunes release metadata in that order. Responses are evidence only; the Android
client still applies hard conflicts, `0.92` confidence and `0.08` margin locally.
