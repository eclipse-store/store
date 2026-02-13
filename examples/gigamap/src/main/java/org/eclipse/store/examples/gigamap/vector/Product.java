package org.eclipse.store.examples.gigamap.vector;

/**
 * Product with embedded vector.
 * <p>
 * Vectorization was done with all-minilm, name and category are included in the vector data
 */
public record Product(String name, Category category, double price, boolean inStock, float[] vector)
{
}
