package org.eclipse.store.examples.gigamap.vector;

import org.eclipse.store.gigamap.jvector.Vectorizer;

public class ProductVectorizer extends Vectorizer<Product>
{
    @Override
    public float[] vectorize(final Product product)
    {
        return product.vector();
    }

    @Override
    public boolean isEmbedded()
    {
        return true;
    }
}
