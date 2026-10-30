package one.microstream.test.cache_jcache.catalog;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - cache-jcache
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

public class Product implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String     sku;
    private final String     name;
    private final BigDecimal price;
    private final String     description;

    public Product(final String sku, final String name, final BigDecimal price, final String description)
    {
        this.sku         = sku;
        this.name        = name;
        this.price       = price;
        this.description = description;
    }

    public String     sku()         { return this.sku; }
    public String     name()        { return this.name; }
    public BigDecimal price()       { return this.price; }
    public String     description() { return this.description; }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Product other)) return false;
        return Objects.equals(this.sku, other.sku)
            && Objects.equals(this.name, other.name)
            && Objects.equals(this.price, other.price)
            && Objects.equals(this.description, other.description);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.sku, this.name, this.price, this.description);
    }

    @Override
    public String toString()
    {
        return "Product[" + this.sku + ", " + this.name + ", " + this.price + "]";
    }
}
