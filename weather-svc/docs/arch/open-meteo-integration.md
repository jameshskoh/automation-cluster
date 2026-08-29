---
status: ACCEPTED
---

# open-meteo integration: geocoding resolution + forecast aggregation

How weather-svc turns a `{city, state}` request into the aggregated block data it publishes. Grounded
entirely in the provided usage guide at `/Users/jameshskoh/knowledgebase/tools/apis/open-meteo/` — no
independent API research. See [`../architecture.md`](../architecture.md) for where this sits.

Two open-meteo APIs, called once each per request (no caching in v1 — the guide's caching is for a
single-location daily checker; here each request may be a different location):

| API | Endpoint | Role |
|---|---|---|
| Geocoding | `https://geocoding-api.open-meteo.com/v1/search` | city name → coordinates |
| Forecast | `https://api.open-meteo.com/v1/forecast` | coordinates → hourly forecast |

## Geocoding + resolution

### Query

```
GET /v1/search?name=<city>&countryCode=MY&count=10&language=en
```

- `countryCode=MY` is a confirmed server-side filter (v1: Malaysia only). No server-side `admin1`
  filter exists — state narrowing is client-side.
- `count=10` so duplicates surface for disambiguation (the guide's "Ayer Hitam" appears in several
  states).

### Client-side resolution (deterministic)

Applied to the `results` array in order:

1. **State filter.** Keep results whose `admin1` equals the requested `state` (normalized: trim +
   case-insensitive). `admin1` is the state / federal territory and is consistently formatted in the
   guide's live testing (unlike `admin2`, which the guide rules out as a filter).
2. **Fuzzy over-match rejection.** Fuzzy search can return a near-miss name — the guide's `Ayer Hitam`
   query also returned `"Kampung Ayer Itam"` (missing "H"). Require the returned `name` to
   **normalized-exactly** match the requested `city` (trim + case-insensitive equality), rejecting
   `"Kampung Ayer Itam"`. (Deliberately strict to avoid false accepts; a legitimate spelling variant
   the user types differently is rejected and surfaces as a no-match `FAILED` — acceptable for v1.)
3. **Deterministic pick among survivors** (the accepted case: Kedah has two distinct "Ayer Hitam"
   towns in the guide): highest `population`; **`population` may be absent** — the guide confirms one of
   five live results omitted the key and warns tiebreak logic needs a fallback, so treat missing as
   **lowest** priority; final tiebreak **lowest `id`** (always present in the guide's data, so a total
   order and a single choice are guaranteed).
4. **No survivor → terminal `FAILED`** with a specific reason, e.g. `no match for "<city>" in
   "<state>", Malaysia` — the geocoding-match failure branch of the error short-circuit.

**Accepted limitation (from `weather.md`):** among two same-name towns in the same state the pick is
deterministic but arbitrary; `admin1` cannot disambiguate them. Not solved in v1.

### Relevant response fields

`name`, `latitude`, `longitude`, `admin1` (state), `population` (may be omitted), `id` (always
present), `timezone`. Missing fields are omitted from the JSON entirely (not null/blank) — parsing must
tolerate absent keys.

## Forecast + block aggregation

### Query

```
GET /v1/forecast
  ?latitude=<lat>&longitude=<lon>
  &hourly=temperature_2m,apparent_temperature,precipitation_probability,weather_code
  &timezone=Asia/Kuala_Lumpur
  &forecast_days=<FORECAST_DAYS>
```

- `timezone=Asia/Kuala_Lumpur` is **required**: block bucketing keys off the local hour of the `time`
  array, so without it the blocks would be built on GMT and be wrong.
- **Hourly variables (exact open-meteo names, confirmed against the forecast guide):** `temperature_2m`
  (°C), `apparent_temperature` (feels-like °C), `precipitation_probability` (%), `weather_code` (WMO
  code). v1 fetches only these four — the metrics the block table needs. (The guide also documents
  `precipitation` and `uv_index`; not required by the block spec, so v1 omits them to keep the payload
  small. Add later if the report grows.)

### Blocks — four 6-hour buckets

Boundaries at **00 / 06 / 12 / 18 local time**, per day, no wraparound:

| Block | Local hours |
|---|---|
| midnight | 00:00–06:00 |
| morning | 06:00–12:00 |
| afternoon | 12:00–18:00 |
| night | 18:00–00:00 |

Bucket each hourly index into its block by the local hour of its `time` entry, grouped by date.

### Per-block statistics

Computed deterministically by weather-svc (not by the LLM):

| Stat | Source | Aggregation |
|---|---|---|
| temp high / low | `temperature_2m` | max / min over the block's hours |
| feels-like high / low | `apparent_temperature` | max / min over the block's hours |
| raining probability | `precipitation_probability` | max over the block's hours (chance it rains at all in the block) |
| sky condition | `weather_code` | dominant condition (see below) |

Skip hours with missing values within a block; if a block has no usable hours (e.g. edge of the
forecast horizon), omit the block rather than emitting nulls.

### WMO `weather_code` → sky condition (`WmoCodeMapper`)

The block's condition is the **most significant** `weather_code` present. WMO codes are roughly ordered
by severity (drizzle < rain < showers < thunderstorm), so the **max** code surfaces the worst weather —
the signal that matters for planning — rather than averaging it away. Map it to a professional
description and emoji:

| WMO code(s) | Description | Emoji |
|---|---|---|
| 0 | Sunny / clear | ☀️ |
| 1, 2, 3 | Cloudy (mainly clear → overcast) | ☁️ |
| 45, 48 | Fog | 🌫️ |
| 51, 53, 55 (56, 57) | Drizzle | 🌦️ |
| 61, 63, 65 (66, 67) | Rain | 🌧️ |
| 80, 81, 82 | Rain showers | 🌧️ |
| 95, 96, 99 | Thunderstorm | ⛈️ |

- weather-svc emits **both** description and emoji per block in `FETCHED.payload`, so claude renders the
  emoji directly and the deterministic classification stays server-side.
- Snow codes (71–77, 85, 86) are not applicable to Malaysia; if ever returned they map to the nearest
  category or a neutral default (phase-3 detail).

## Attribution / licensing note

The guide records the Geocoding API as CC BY-NC 4.0 (non-commercial) and the Forecast API as CC BY 4.0.
This is a personal, non-commercial cluster, so neither constrains today; flagged only so it is not
forgotten if usage ever changes.
