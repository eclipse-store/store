package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

/**
 * Similarity functions for comparing vectors in a {@link VectorIndex}.
 * <p>
 * The similarity function determines how distance/similarity between vectors is computed.
 * Choosing the right function is critical for search quality and depends on your data and use case.
 *
 * <h2>Decision Guide</h2>
 * <table border="1">
 *   <tr><th>Use Case</th><th>Recommended Function</th><th>Reason</th></tr>
 *   <tr><td>Text/semantic search</td><td>{@link #COSINE}</td><td>Meaning is in direction, not magnitude</td></tr>
 *   <tr><td>OpenAI/Cohere embeddings</td><td>{@link #COSINE}</td><td>Models optimized for cosine</td></tr>
 *   <tr><td>Pre-normalized vectors</td><td>{@link #DOT_PRODUCT}</td><td>Faster, same results as cosine</td></tr>
 *   <tr><td>Recommendation (MIPS)</td><td>{@link #DOT_PRODUCT}</td><td>Maximize relevance scores</td></tr>
 *   <tr><td>Geographic/spatial</td><td>{@link #EUCLIDEAN}</td><td>Physical distance matters</td></tr>
 *   <tr><td>Image pixels/raw data</td><td>{@link #EUCLIDEAN}</td><td>Absolute values meaningful</td></tr>
 *   <tr><td>Face embeddings (FaceNet)</td><td>{@link #EUCLIDEAN}</td><td>Model trained with L2</td></tr>
 *   <tr><td>Unknown/unsure</td><td>{@link #COSINE}</td><td>Most robust default choice</td></tr>
 * </table>
 *
 * @see VectorIndex
 * @see VectorIndexConfiguration
 */
public enum VectorSimilarityFunction
{
    /**
     * Measures the straight-line (Euclidean) distance between vector endpoints.
     * <ul>
     *   <li><b>Range:</b> [0, +∞) where 0 = identical vectors</li>
     *   <li><b>Formula:</b> {@code ||A - B|| = √(Σ(Aᵢ - Bᵢ)²)}</li>
     * </ul>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Spatial/geographic data</b> - Finding nearest physical locations, GPS coordinates,
     *       3D object positions in games or simulations.</li>
     *   <li><b>Image pixel similarity</b> - When comparing raw image data or simple features
     *       where absolute color/intensity values matter.</li>
     *   <li><b>Scientific measurements</b> - Sensor data, spectral analysis, time series
     *       where absolute values are meaningful.</li>
     *   <li><b>Clustering algorithms</b> - K-means and similar algorithms often assume Euclidean space.</li>
     *   <li><b>Face recognition</b> - Some face embedding models (e.g., FaceNet) are trained
     *       with Euclidean distance.</li>
     * </ul>
     * <b>Example:</b> Finding the nearest warehouse to a delivery address based on lat/long coordinates.
     */
    EUCLIDEAN,

    /**
     * Computes the sum of element-wise products. Magnitude affects the result.
     * <ul>
     *   <li><b>Range:</b> (-∞, +∞) for general vectors; [0, 1] for unit vectors</li>
     *   <li><b>Formula:</b> {@code A · B = Σ(Aᵢ × Bᵢ)}</li>
     * </ul>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Pre-normalized vectors</b> - When you've already normalized vectors to unit length,
     *       dot product is faster than cosine (no normalization step) and gives identical rankings.</li>
     *   <li><b>Maximum Inner Product Search (MIPS)</b> - Recommendation systems where you want
     *       to find items that maximize relevance scores (e.g., user-item matrix factorization).</li>
     *   <li><b>Neural network outputs</b> - When the model was trained with dot product similarity
     *       (check your embedding model's documentation).</li>
     *   <li><b>Attention mechanisms</b> - Query-key matching in transformer-style architectures.</li>
     * </ul>
     * <b>Example:</b> OpenAI's text-embedding-3 models are optimized for cosine similarity,
     * but if you pre-normalize them, dot product gives identical results with better performance.
     */
    DOT_PRODUCT,

    /**
     * Measures the cosine of the angle between vectors, ignoring magnitude.
     * <ul>
     *   <li><b>Range:</b> [-1, 1] where 1 = identical direction, 0 = orthogonal, -1 = opposite</li>
     *   <li><b>Formula:</b> {@code cos(θ) = (A · B) / (||A|| × ||B||)}</li>
     * </ul>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Text embeddings</b> - Semantic similarity search, document retrieval, FAQ matching.
     *       Text embeddings capture meaning in direction, not magnitude.</li>
     *   <li><b>Recommendation systems</b> - Finding similar items based on feature vectors
     *       where the "strength" of features shouldn't dominate.</li>
     *   <li><b>Image embeddings</b> - When using models like CLIP that produce direction-based embeddings.</li>
     *   <li><b>Any case where vectors have varying magnitudes</b> - Cosine normalizes automatically.</li>
     * </ul>
     * <b>Example:</b> Two documents about "machine learning" should match even if one is longer
     * (higher magnitude) than the other.
     * <p>
     * This is the default and most commonly used similarity function for embedding-based search.
     */
    COSINE
}
