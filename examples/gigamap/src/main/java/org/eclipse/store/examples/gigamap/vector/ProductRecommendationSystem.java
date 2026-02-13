package org.eclipse.store.examples.gigamap.vector;

import org.eclipse.store.gigamap.jvector.*;
import org.eclipse.store.gigamap.types.GigaMap;

/**
 * Demonstrates a product recommendation system using EclipseStore's GigaMap
 * with vector similarity search capabilities.
 */
public class ProductRecommendationSystem
{
    public static void main(final String[] args)
    {
        // Create a new GigaMap instance to store Product entities.
        final GigaMap<Product> gigaMap = GigaMap.New();

        // Create a bitmap index for exact string matching on product names.
        // This allows fast lookup of products by their exact name.
        final ProductIndices.NameIndex nameIndex = new ProductIndices.NameIndex();
        gigaMap.index().bitmap().add(nameIndex);

        // Register and configure a vector index for similarity search.
        // Vector indices enable finding products that are semantically similar
        // based on their vector embeddings.
        final VectorIndex<Product> vectorIndex = gigaMap.index().register(VectorIndices.Category()).add(
            "vectors",  // Name of the vector index
            VectorIndexConfiguration.builder()
                .dimension(384)  // Vector dimension, matches the used embedding model (all-minilm)
                .similarityFunction(VectorSimilarityFunction.COSINE)  // Use cosine similarity - ideal for text/semantic embeddings
                .maxDegree(16)  // Max connections per node in the HNSW graph (higher = better recall, more memory)
                .beamWidth(200)  // Search beam width during index construction (higher = better quality, slower build)
                .neighborOverflow(1.2f)  // Allow 20% more neighbors during construction for better graph quality
                .alpha(1.2f)  // Pruning parameter - controls graph sparsity vs recall trade-off
                .build(),
            new ProductVectorizer()  // Extracts vector embeddings from Product entities
        );

        // Load products from JSON file and add them all to the GigaMap.
        // This automatically indexes them in both the bitmap and vector indices.
        gigaMap.addAll(ProductLoader.loadProductsFromJson());

        System.out.printf("Created GigaMap with %d products%n", gigaMap.size());

        // Demonstrate finding similar products for three different seed products.
        // Each call will:
        // 1. Look up the seed product by exact name using the bitmap index
        // 2. Use its vector embedding to find the 10 most similar products
        findSimilarProducts("Wireless Bluetooth Headphones", gigaMap, nameIndex, vectorIndex);
        findSimilarProducts("Running Shoes Ultra Lightweight", gigaMap, nameIndex, vectorIndex);
        findSimilarProducts("Cast Iron Skillet 12 inch", gigaMap, nameIndex, vectorIndex);
    }

    /**
     * Finds and displays products similar to the given product name.
     * 
     * @param productName  The exact name of the seed product to find similar items for
     * @param gigaMap      The GigaMap containing all products
     * @param nameIndex    Bitmap index for exact name lookup
     * @param vectorIndex  Vector index for similarity search
     */
    private static void findSimilarProducts(
        final String productName,
        final GigaMap<Product> gigaMap,
        final ProductIndices.NameIndex nameIndex,
        final VectorIndex<Product> vectorIndex
    )
    {
        System.out.println("------------------------------------");

        // Step 1: Find the seed product by exact name using the bitmap index.
        // The query returns a stream; we get the first (and should be only) match.
        final Product product = gigaMap.query(nameIndex.is(productName)).findFirst().get();

        final long start = System.currentTimeMillis();

        // Step 2: Perform vector similarity search.
        // This uses the seed product's vector embedding to find the 10 most
        // similar products based on cosine similarity of their vectors.
        // The HNSW algorithm provides approximate nearest neighbor search
        // with sub-linear time complexity.
        final VectorSearchResult<Product> result = vectorIndex.search(product, 10);

        System.out.printf("Finding similar products for '%s', took %d ms%n%n", product.name(), System.currentTimeMillis() - start);

        // Step 3: Display results with their similarity scores.
        // Higher scores indicate greater similarity (for cosine: 1.0 = identical, 0.0 = orthogonal).
        result.forEach(entry ->
        {
            final Product similar = entry.entity();
            System.out.printf("%s, score=%s%n", similar.name(), entry.score());
        });
    }

}
