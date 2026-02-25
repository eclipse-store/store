# Product Recommendations Demo

A Spring Boot application demonstrating **GigaMap with Vector Index** for product similarity search.
Uses [LangChain4j](https://docs.langchain4j.dev/) with a local [Ollama](https://ollama.com/) `all-minilm` embedding model to vectorize product names and find similar products via cosine similarity.

## Prerequisites

- Java 17+
- [Ollama](https://ollama.com/) running locally
- Pull the embedding model:
  ```
  ollama pull all-minilm
  ```

## Run

```
mvn spring-boot:run
```

The application starts on `http://localhost:8080` and initializes a GigaMap with 100 products from `products.json`.

## REST API

| Method | Endpoint              | Description                      | Parameters                              |
|--------|-----------------------|----------------------------------|-----------------------------------------|
| GET    | `/products`           | List products (paged)            | `page` (default 0), `size` (default 20) |
| GET    | `/products/search`    | Find similar products by query   | `query` (required), `k` (default 5)     |
| POST   | `/products/add`       | Add random products from catalog | `count` (default 1)                     |

## Examples

```
# List first page of products
GET http://localhost:8080/products

# Search for products similar to "headphones"
GET http://localhost:8080/products/search?query=headphones&k=5

# Add 10 random products
POST http://localhost:8080/products/add?count=10
```

You can also use the included `requests.http` file to run these directly from IntelliJ.

## Swagger UI

Available at http://localhost:8080/swagger-ui.html

## Architecture

- **GigaMap** — indexed collection storing product entities
- **VectorIndex** (JVector HNSW) — 384-dimensional cosine similarity index on product names
- **Vectorizer** — uses Ollama `all-minilm` via LangChain4j to compute embeddings
- **Spring Boot** — REST layer with Swagger/OpenAPI documentation
