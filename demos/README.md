# EclipseStore Demos

Spring Boot demo applications showcasing **GigaMap** capabilities — from spatial queries to vector-based similarity search.

## Demos

### [Country Explorer](country-explorer/)

Demonstrates **GigaMap with Spatial Index** for geographic queries on 195 countries.
Supports proximity searches, bounding box queries, hemisphere filters, and Haversine distance calculations using bitmap-based spatial indexing.

- **Port:** 8081
- **Prerequisites:** Java 17+
- **Key features:** Spatial indexing, radius search, k-nearest-neighbor, bounding box queries

### [Product Recommendations](product-recommendations/)

Demonstrates **GigaMap with Vector Index** for semantic product similarity search.
Uses LangChain4j with a local Ollama embedding model to vectorize products and find matches via cosine similarity.

- **Port:** 8080
- **Prerequisites:** Java 17+, Ollama with `all-minilm` model
- **Key features:** Vector similarity search, HNSW indexing, embedding generation

### [Vinoteca](vinoteca/)

Comprehensive wine-cellar and vineyard platform that combines **all four GigaMap index types** — bitmap, spatial, Lucene full-text and JVector vector — in a single application, exposed through a Vaadin UI, GraphQL and REST/OpenAPI.

- **Port:** 8082
- **Prerequisites:** Java 17+, Ollama with `all-minilm` model
- **Key features:** Bitmap + spatial + Lucene + vector indices on the same dataset, Vaadin UI with Leaflet map, GraphQL + REST APIs

## Quick Start

```
# Country Explorer
cd country-explorer
mvn spring-boot:run

# Product Recommendations (requires Ollama)
ollama pull all-minilm
cd product-recommendations
mvn spring-boot:run

# Vinoteca (requires Ollama)
ollama pull all-minilm
cd vinoteca
mvn spring-boot:run
```

Each demo includes a Swagger UI for interactive exploration and a `requests.http` file for IntelliJ; the Vinoteca demo additionally exposes a Vaadin UI and a GraphiQL playground.