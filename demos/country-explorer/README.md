# Country Explorer Demo

A Spring Boot application demonstrating **GigaMap with Spatial Index** for geographic queries on all 195 countries.
Uses the `SpatialIndexer` to index capital city coordinates (latitude/longitude) and supports proximity searches, bounding box queries, hemisphere filters, and distance calculations — all powered by GigaMap's bitmap-based spatial indexing with Haversine distance.

## Prerequisites

- Java 17+

No external services required.

## Run

```
mvn spring-boot:run
```

The application starts on `http://localhost:8081` and loads 195 countries from `countries.json` into a GigaMap with spatial and bitmap indices.

## REST API

| Method | Endpoint                    | Description                              | Parameters                                                     |
|--------|-----------------------------|------------------------------------------|----------------------------------------------------------------|
| GET    | `/countries`                | List countries (paged)                   | `page` (default 0), `size` (default 20)                       |
| GET    | `/countries/{alpha2}`       | Get country by ISO code                  | `alpha2` (path)                                                |
| GET    | `/countries/nearby`         | Find neighbours within radius            | `alpha2`, `radiusKm` (default 1000)                            |
| GET    | `/countries/near`           | Find countries near coordinates          | `lat`, `lon`, `radiusKm` (default 1000)                       |
| GET    | `/countries/nearest`        | K nearest countries                      | `alpha2`, `k` (default 5)                                     |
| GET    | `/countries/continent/{name}` | All countries on a continent           | `name` (path, e.g. `Europe`)                                  |
| GET    | `/countries/within-box`     | Bounding box query                       | `minLat`, `maxLat`, `minLon`, `maxLon`                        |
| GET    | `/countries/hemisphere`     | Filter by hemisphere                     | `hemisphere` (`north`, `south`, `east`, `west`)                |
| GET    | `/countries/tropical`       | Countries in the tropics                 | —                                                              |
| GET    | `/countries/distance`       | Distance between two countries           | `from`, `to` (alpha2 codes)                                   |

## Examples

```
# Germany's neighbours within 1000 km
GET http://localhost:8081/countries/nearby?alpha2=DE&radiusKm=1000

# 5 nearest countries to Australia
GET http://localhost:8081/countries/nearest?alpha2=AU&k=5

# Distance from Berlin to Tokyo
GET http://localhost:8081/countries/distance?from=DE&to=JP

# All European countries
GET http://localhost:8081/countries/continent/Europe

# Bounding box: Middle East
GET http://localhost:8081/countries/within-box?minLat=12&maxLat=42&minLon=25&maxLon=63

# Southern hemisphere
GET http://localhost:8081/countries/hemisphere?hemisphere=south

# Tropical countries
GET http://localhost:8081/countries/tropical
```

You can also use the included `requests.http` file to run these directly from IntelliJ.

## Swagger UI

Available at http://localhost:8081/swagger-ui.html

## Architecture

- **GigaMap** — indexed collection storing country entities
- **SpatialIndexer** — lat/lon index with `near()`, `withinBox()`, `withinRadius()`, and `haversineDistance()` queries
- **BitmapIndices** — string indices on continent, name, and alpha2 code for exact lookups
- **Spring Boot** — REST layer with Swagger/OpenAPI documentation
