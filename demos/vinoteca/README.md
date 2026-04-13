# Vinoteca Demo

A comprehensive wine cellar and vineyard management platform showcasing the **full power of EclipseStore GigaMap** — combining **bitmap**, **spatial**, **vector**, and **Lucene full-text** indices in a single Spring Boot application with a Vaadin UI, GraphQL API, and REST/OpenAPI endpoints.

## Highlights

- **GigaMap with four index types** working side by side on the same dataset:
  - **Bitmap indices** for exact-match filters (wine type, grape variety, country, region, …)
  - **Spatial index** for geographic winery proximity queries
  - **Vector index** (JVector / HNSW, cosine similarity, 384 dim) for semantic "find similar wines" search
  - **Lucene index** for full-text search across wine names, tasting notes, aroma, and food pairings
- **Three API layers** over the same data: Vaadin UI, GraphQL, and REST (OpenAPI)
- **Embedded persistence** via EclipseStore — no external database required

## Prerequisites

- Java 17+
- [Ollama](https://ollama.com/) running locally (used to compute wine embeddings for the vector index)
- Pull the embedding model:
  ```
  ollama pull all-minilm
  ```

## Run

```
mvn spring-boot:run
```

The application starts on `http://localhost:8082` and, on first launch, generates an initial dataset of 300 wines (configurable via `vinoteca.initial-data-count`) along with wineries, customers, and orders. Data is persisted in `demos/vinoteca/data/`.

## What's inside

### Domain model

- `Wine` — name, winery, grape variety, type, vintage, price, rating, tasting notes, aroma, food pairing, stock, reviews
- `Winery` — name, region, country, geo-coordinates, founding year
- `Customer`, `Order`, `OrderItem`, `Review`

### Indexing layout (`DataRoot`)

| Collection | Index type | Purpose                                                  |
|------------|------------|----------------------------------------------------------|
| `wines`    | Bitmap     | Filter by name, type, grape, winery, country, region     |
| `wines`    | Lucene     | Full-text search over name, notes, aroma, food pairing   |
| `wines`    | Vector     | Semantic similarity ("wine-embeddings", 384-dim cosine)  |
| `wineries` | Bitmap     | Filter by name, region, country                          |
| `wineries` | Spatial    | Proximity / radius queries on lat/lon                    |

## Vaadin UI

Available at `http://localhost:8082/`:

- **Wine Catalog** — paged grid with bitmap-index-backed filters
- **Wineries** — Leaflet map, proximity search around a clicked location
- **Similarity Search** — natural-language query → vector similarity over wine embeddings
- **Full-Text Search** — Lucene-powered keyword search
- **Customers** / **Orders** — basic CRUD over the customer and order lists
- **Analytics** — aggregated stats (counts, distributions, averages)
- **Data Generator** — bulk-generate additional sample data

## GraphQL

- GraphiQL playground: `http://localhost:8082/graphiql`
- Endpoint: `http://localhost:8082/graphql`
- Schema: `src/main/resources/graphql/schema.graphqls`

Example query:

```graphql
{
  similarWines(query: "bold red wine with notes of black cherry", k: 5) {
    score
    wine { name wineryName grapeVariety vintage rating }
  }
  nearbyWineries(lat: 45.4, lon: 11.0, radiusKm: 100) {
    distanceKm
    winery { name region country }
  }
}
```

## REST API

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI spec: `http://localhost:8082/v3/api-docs`

Top-level endpoints:

| Endpoint              | Description                                                 |
|-----------------------|-------------------------------------------------------------|
| `/api/wines`          | CRUD + filters (type, grape, country, region, vintage, …)  |
| `/api/wines/search`   | Full-text (Lucene) search                                   |
| `/api/wines/similar`  | Semantic (vector) similarity search                         |
| `/api/wineries`       | CRUD + spatial proximity queries                            |
| `/api/customers`      | Customer CRUD                                               |
| `/api/orders`         | Order CRUD + status updates                                 |
| `/api/data`           | Data generator and metrics                                  |

## Configuration

Key `application.properties` entries:

```
server.port=8082
org.eclipse.store.storage-directory=demos/vinoteca/data
vinoteca.initial-data-count=300
```

The vector index targets a local Ollama instance at `http://localhost:11434` using the `all-minilm` model (see `DataRoot`).

## Architecture

```
Vaadin UI ─┐
GraphQL ───┼──> Service layer ──> GigaMap<Wine> + GigaMap<Winery> + Lists
REST ──────┘                              │
                                          └─> EclipseStore EmbeddedStorageManager
                                              (auto-configured via integrations-spring-boot3)
```

- **EclipseStore** — embedded object-graph persistence (`integrations-spring-boot3` auto-starts the storage manager)
- **GigaMap** — indexed collections with pluggable bitmap / spatial / vector / Lucene indexers
- **LangChain4j + Ollama** — wine text → embedding vectors for the vector index
- **Spring Boot** — REST and GraphQL controllers
- **Vaadin Flow** — server-side UI with a Leaflet map integration