# Keep-alive Worker

Pings the Render backend health endpoint every 5 minutes via a Cloudflare Workers
Cron Trigger, so the free-tier instance never sits idle long enough to spin down.

Replaces the old GitHub Actions `*/5` schedule, which GitHub could silently
skip under load — Cloudflare's cron scheduler doesn't share that job queue
and fires far more consistently.

## Setup (one-time)

```bash
cd cloudflare/keep-alive-worker
npm install
npx wrangler login
```

If the backend's URL changes, update `HEALTH_URL` in `wrangler.toml`.

## Deploy

```bash
npm run deploy
```

## Verify

```bash
npm run tail
```

Wait for the next 5-minute mark and confirm a `Keep-alive ping: 200` log line
appears. You can also check recent invocations in the Cloudflare dashboard
under Workers & Pages > riftchallenge-keep-alive > Logs.
