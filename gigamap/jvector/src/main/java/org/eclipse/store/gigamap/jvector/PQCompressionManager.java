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

import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.quantization.CompressedVectors;
import io.github.jbellis.jvector.quantization.ProductQuantization;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages Product Quantization (PQ) compression for a VectorIndex.
 * <p>
 * This manager handles:
 * <ul>
 *   <li>PQ codebook training from sampled vectors</li>
 *   <li>Vector compression using trained PQ</li>
 *   <li>Search with PQ-compressed scoring and exact reranking</li>
 * </ul>
 */
interface PQCompressionManager
{
    /**
     * Minimum vectors needed for PQ training.
     */
    final static int MIN_VECTORS_FOR_PQ_TRAINING = 256;

    /**
     * Rerank multiplier - how many extra candidates to fetch for reranking.
     */
    final static int PQ_RERANK_MULTIPLIER = 2;

    /**
     * Returns whether PQ has been trained.
     *
     * @return true if trained
     */
    public boolean isTrained();

    /**
     * Returns the trained ProductQuantization, or null if not trained.
     *
     * @return the PQ instance
     */
    public ProductQuantization getPQ();

    /**
     * Returns the compressed vectors, or null if not trained.
     *
     * @return the compressed vectors
     */
    public CompressedVectors getCompressedVectors();

    /**
     * Trains PQ if compression is enabled and sufficient vectors exist.
     * Does nothing if already trained or insufficient vectors.
     */
    public void trainIfNeeded();

    /**
     * Searches using PQ-compressed vectors with reranking.
     *
     * @param query              the query vector
     * @param k                  the number of results to return
     * @param searcher           the graph searcher to use
     * @param ravv               random access vector values for exact reranking
     * @param similarityFunction the similarity function to use
     * @return the search result with reranked nodes
     */
    public SearchResult searchWithRerank(
        VectorFloat<?>           query             ,
        int                      k                 ,
        GraphSearcher            searcher          ,
        RandomAccessVectorValues ravv              ,
        VectorSimilarityFunction similarityFunction
    );

    /**
     * Marks PQ as trained (used when loading from disk where FusedPQ is embedded).
     */
    public void markTrained();

    /**
     * Resets PQ state (clears trained PQ and compressed vectors).
     */
    public void reset();


    /**
     * Provider interface for accessing vectors needed for PQ training.
     */
    public static interface VectorProvider
    {
        /**
         * Returns the current vector count.
         *
         * @return the vector count
         */
        public long getVectorCount();

        /**
         * Collects training vectors for PQ training.
         *
         * @return list of vectors for training
         */
        public List<VectorFloat<?>> collectTrainingVectors();
    }


    /**
     * Default implementation of PQCompressionManager.
     */
    public static class Default implements PQCompressionManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(PQCompressionManager.class);

        private final VectorProvider provider   ;
        private final String         name       ;
        private final int            dimension  ;
        private final int            pqSubspaces;

        private ProductQuantization pq               ;
        private CompressedVectors   compressedVectors;
        private boolean             pqTrained        ;

        Default(
            final VectorProvider provider   ,
            final String         name       ,
            final int            dimension  ,
            final int            pqSubspaces
        )
        {
            this.provider    = provider   ;
            this.name        = name       ;
            this.dimension   = dimension  ;
            this.pqSubspaces = pqSubspaces;
        }

        @Override
        public boolean isTrained()
        {
            return this.pqTrained;
        }

        @Override
        public ProductQuantization getPQ()
        {
            return this.pq;
        }

        @Override
        public CompressedVectors getCompressedVectors()
        {
            return this.compressedVectors;
        }

        @Override
        public void trainIfNeeded()
        {
            if(this.pqTrained)
            {
                return; // Already trained
            }

            final long vectorCount = this.provider.getVectorCount();
            if(vectorCount < MIN_VECTORS_FOR_PQ_TRAINING)
            {
                LOG.debug("Not enough vectors for PQ training ({} < {})", vectorCount, MIN_VECTORS_FOR_PQ_TRAINING);
                return;
            }

            this.trainPQ();
        }

        /**
         * Trains the PQ codebook from current vectors.
         */
        private void trainPQ()
        {
            LOG.info("Training PQ for index '{}'...", this.name);

            // Collect training vectors
            final List<VectorFloat<?>> trainingVectors = this.provider.collectTrainingVectors();

            if(trainingVectors.isEmpty())
            {
                LOG.warn("No vectors available for PQ training");
                return;
            }

            // Determine number of subspaces
            final int subspaces = this.pqSubspaces > 0
                ? this.pqSubspaces
                : Math.max(1, this.dimension / 4);

            final ListRandomAccessVectorValues ravv =
                new ListRandomAccessVectorValues(trainingVectors, this.dimension);

            // Train PQ - use 256 centroids per subspace (standard), center for better accuracy
            this.pq = ProductQuantization.compute(
                ravv,
                subspaces,
                256,   // centroids per subspace (2^8 = 256 is standard)
                this.dimension < 64 // use global centroid for low dimensions
            );

            // Compress all vectors
            this.compressedVectors = this.pq.encodeAll(ravv);

            this.pqTrained = true;
            LOG.info("PQ training complete for '{}': {} subspaces, {} vectors compressed",
                this.name, subspaces, trainingVectors.size());
        }

        @Override
        public SearchResult searchWithRerank(
            final VectorFloat<?>           query             ,
            final int                      k                 ,
            final GraphSearcher            searcher          ,
            final RandomAccessVectorValues ravv              ,
            final VectorSimilarityFunction similarityFunction
        )
        {
            // Search with PQ for approximate results (fetch more candidates for reranking)
            final int candidateCount = k * PQ_RERANK_MULTIPLIER;

            // Use exact vectors for search but rerank with exact vectors
            final SearchScoreProvider ssp = DefaultSearchScoreProvider.exact(
                query,
                similarityFunction,
                ravv
            );

            final SearchResult result = searcher.search(ssp, candidateCount, Bits.ALL);

            // Rerank with exact vectors to get the best k
            final List<NodeScoreEntry> reranked = new ArrayList<>();

            for(final SearchResult.NodeScore node : result.getNodes())
            {
                final VectorFloat<?> exactVector = ravv.getVector(node.node);
                if(exactVector != null)
                {
                    final float exactScore = similarityFunction.compare(query, exactVector);
                    reranked.add(new NodeScoreEntry(node.node, exactScore));
                }
            }

            // Sort by score descending and take top k
            reranked.sort((a, b) -> Float.compare(b.score, a.score));
            final SearchResult.NodeScore[] topK = reranked.stream()
                .limit(k)
                .map(e -> new SearchResult.NodeScore(e.node, e.score))
                .toArray(SearchResult.NodeScore[]::new);

            // Create a SearchResult with the reranked nodes
            return new SearchResult(topK, result.getVisitedCount(), 0, 0, 0, 0f);
        }

        @Override
        public void markTrained()
        {
            this.pqTrained = true;
        }

        @Override
        public void reset()
        {
            this.pq = null;
            this.compressedVectors = null;
            this.pqTrained = false;
        }

        /**
         * Simple holder for node and score during reranking.
         */
        private record NodeScoreEntry(int node, float score) {}

    }

}
