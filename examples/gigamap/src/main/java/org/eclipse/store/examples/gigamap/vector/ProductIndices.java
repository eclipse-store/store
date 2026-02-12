package org.eclipse.store.examples.gigamap.vector;

import org.eclipse.store.gigamap.types.BinaryIndexerString;

public class ProductIndices
{
    public final static class NameIndex extends BinaryIndexerString.Abstract<Product>
    {
        @Override
        protected String getString(final Product product)
        {
            return product.name();
        }
    }
}
