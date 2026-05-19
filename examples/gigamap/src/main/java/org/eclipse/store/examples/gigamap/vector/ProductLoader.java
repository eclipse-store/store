package org.eclipse.store.examples.gigamap.vector;

/*-
 * #%L
 * EclipseStore Example GigaMap
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ProductLoader
{
    public static List<Product> loadProductsFromJson()
    {
        final List<Product> products = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();

        try(final InputStream inputStream = ProductRecommendationSystem.class
            .getResourceAsStream("/samples/products.json")
        )
        {
            final JsonNode rootNode = mapper.readTree(inputStream);
            final JsonNode documentsNode = rootNode.get("documents");

            if(documentsNode != null && documentsNode.isArray())
            {
                for (final JsonNode document : documentsNode)
                {
                    products.add(parseProduct(document));
                }
            }
        }
        catch(final IOException e)
        {
            throw new RuntimeException("Failed to load products from JSON", e);
        }

        return products;
    }

    private static Product parseProduct(final JsonNode document)
    {
        final JsonNode metadata = document.get("metadata");
        final JsonNode vectorNode = document.get("vector");

        final String name = metadata.get("name").asText();
        final Category category = Category.valueOf(
            metadata.get("category").asText()
                .toUpperCase()
                .replace(' ', '_')
        );
        final double price = metadata.get("price").asDouble();
        final boolean inStock = metadata.get("in_stock").asBoolean();

        final float[] vector = new float[vectorNode.size()];
        for (int i = 0; i < vectorNode.size(); i++)
        {
            vector[i] = (float) vectorNode.get(i).asDouble();
        }

        return new Product(name, category, price, inStock, vector);
    }
}
