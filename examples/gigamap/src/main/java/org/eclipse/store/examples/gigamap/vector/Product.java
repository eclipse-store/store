package org.eclipse.store.examples.gigamap.vector;

public record Product(String name, Category category, double price, boolean inStock, float[] vector)
{
}
