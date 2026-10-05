# Bruno API Test Suite

Manual test collection covering the full chain: ingestion from the French open-data portals through the nearby search API.

## Layout

```
bruno/
├── environments/
│   └── local.bru                  ← local environment (http://localhost:8080)
│
├── realtime/                      ← real-time cities (Poitiers / Nantes / Nantes P+R)
│   ├── 01 - Poitiers (realtime availability).bru
│   ├── 02 - Nantes (realtime availability).bru
│   └── 03 - Nantes P+R (realtime availability).bru
│
├── static/                        ← static cities (Toulouse / Paris)
│   ├── 01 - Toulouse (static capacity).bru
│   └── 02 - Paris (static capacity).bru
│
├── freshness/                     ← data freshness
│   └── 01 - Data freshness check.bru
│
├── cross-city/                    ← cross-city isolation
│   └── 01 - Nantes does not return Paris.bru
│
└── errors/                        ← error handling
    ├── 01 - 400 lat out of range.bru
    ├── 02 - 400 missing lat.bru
    ├── 03 - 400 invalid ranking.bru
    ├── 04 - 400 lat not a number.bru
    └── 05 - 405 POST not allowed.bru
```

## Prerequisites

1. Start the application:

   ```bash
   java -Dfile.encoding=UTF-8 -jar where-to-park-rest/target/where-to-park-rest-0.0.1-SNAPSHOT.jar
   ```

   On Windows, if the network intercepts TLS with a CA the JDK does not know, HTTPS ingestion fails with `PKIX path building failed` (the API stays up but returns empty `results`); in that case add `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` so the JVM also trusts the Windows root CA store. Not needed on Linux/containers.

   Wait until every city has been ingested once — typically under a minute on a normal network.

2. Check the ingestion logs for one registration line and one success line per city:

   ```
   Registered 5 connector(s): [...]
   Ingested N parkings for city poitiers
   Ingested N parkings for city nantes
   ...
   ```

   Record counts and the registration order are examples: counts drift with the source data, and the order is not significant.

## Test groups

### 1. Realtime cities (realtime/)

Assertions per city: results are non-empty; at least one parking reports `availableSpots`; status follows each platform's semantics (spots > 0 → `OPEN` for Poitiers, never `FULL` for Nantes); `sourceUpdatedAt` is less than 5 minutes old; every result's `cityId` and `id` prefix match the queried city; attribution credits that city; results sort by ascending distance.

### 2. Static cities (static/)

Toulouse and Paris publish no real-time feed: `availableSpots` is `null`, `status` is `UNKNOWN`, `capacity` is present.

### 3. Freshness (freshness/)

Run the test, record the ETag, wait 90 seconds (past the 60 s refresh cycle), run again: the ETag changes when the source data changed. Quick alternative: `GET /v1/cities` and check `fresh: true` per city.

### 4. Cross-city isolation (cross-city/)

A query in Nantes with radius 5000 m must return only `nantes` / `nantes-pr` results (Paris is ~340 km away), and the attribution must list only the cities present in the results.

### 5. Error handling (errors/)

Parameter validation (`400` with a message naming the offending parameter) and the read-only contract (`405` on POST).

## Test coordinates

| City | Coordinates | Location |
|---|---|---|
| Poitiers | `lat=46.5802, lon=0.3404` | City center (near Hôtel de Ville) |
| Nantes | `lat=47.2138, lon=-1.5536` | City center (near Decré-Bouffay) |
| Nantes P+R | `lat=47.1914, lon=-1.5852` | Near P+R Trentemoult-Sablières |
| Toulouse | `lat=43.6047, lon=1.4442` | City center (near Capitole) |
| Paris | `lat=48.8566, lon=2.3522` | City center (Marais) |

## Data sources

| City | Platform | Freshness | Notes |
|---|---|---|---|
| Poitiers | Data Fair (Grand Poitiers) | 60 s real-time | Records without usable coordinates are dropped at ingestion |
| Nantes | Opendatasoft (Nantes Métropole) | 60 s real-time | `grp_statut` code table: 5 → OPEN_OR_FULL, 1/2 → CLOSED |
| Nantes P+R | Opendatasoft (Nantes Métropole) | 60 s real-time | Same mapping as Nantes |
| Toulouse | Opendatasoft (Toulouse Métropole) | Static (5 min poll) | Capacity only |
| Paris | Opendatasoft (Ville de Paris) | Static (5 min poll) | Capacity only |

## Troubleshooting

**A city returns empty results.**

1. Look for ingestion failures in the application log:
   ```
   Ingestion failed for city nantes: ... (serving stale data, retry in Xs)
   ```
2. If the failure message mentions `SSLHandshakeException` / `PKIX path building failed`, the JVM does not trust the network's CA — restart with `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` (see Prerequisites). A quick way to confirm: `GET /actuator/prometheus` shows `parking_ingestion_last_success_timestamp{city="..."} 0.0` for every city when no fetch has ever succeeded.
3. On first start, wait for the warm-up poll to finish and retry.
4. On persistent failure, check network access to the source portal.

**`sourceUpdatedAt` is old.**

1. Check whether the source itself has stopped updating (open the dataset URL).
2. Check the log for the per-city poll (every 60 s for real-time cities).
3. If the source updates but the served data is old, the ingestion is failing and the API is serving the last good snapshot (stale serving by design).

**The ETag does not change.**

- Real-time cities: expected whenever the source data did not change between polls.
- Static cities: expected almost always; those datasets update rarely.

## Bruno CLI

Run these commands from this directory (`bruno/`), which the CLI uses as the collection root.

```bash
# Run the whole collection
bru run --env local

# Run one group
bru run realtime --env local
bru run static --env local

# Run a single test
bru run "realtime/01 - Poitiers (realtime availability).bru" --env local
```
