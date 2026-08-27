package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
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

import static org.eclipse.serializer.util.X.notNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.serializer.typing.Immutable;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;

/**
 * Per-storage strategy part choosing the primary chunk-checksum algorithm (the one used for writes) and the
 * policy, and serving as a factory for per-channel {@link StorageChunkChecksumCalculator} instances.
 * Configured on the storage via {@link StorageConfiguration} / the storage foundation; defaults to no checksum
 * ({@link #NewNone()}) when not set.
 * <p>
 * The algorithm is the swappable axis, supplied as a factory ({@link Supplier}) to the {@code New(...)}
 * methods &mdash; it is not hard-coded. The <i>primary</i> is used for writes; alongside it a set of
 * <i>additional</i> algorithms forms the verify-dispatch universe, so a store written by one algorithm still
 * verifies after the configured primary changes between runs. The additional set defaults to
 * {@link #DefaultAlgorithms()} (CRC32C, chained SHA-256). The available built-in algorithms are
 * {@link StorageChunkChecksumCalculator.Algorithm} implementations:
 * <ul>
 *   <li>{@link StorageChunkChecksumCalculator.Algorithm.Crc32c} &mdash; non-cryptographic integrity checksum:
 *       hardware-accelerated, zero-copy on direct buffers; detects accidental corruption but is <i>not</i>
 *       tamper-evident.</li>
 *   <li>{@link StorageChunkChecksumCalculator.Algorithm.Sha256Chained} &mdash; cryptographic, tamper-evident
 *       chained SHA-256 (each chunk's hash folds in the previous chunk's), carrying a configurable initial
 *       seed.</li>
 *   <li>{@link StorageChunkChecksumCalculator.Algorithm.None} &mdash; no-op "no checksum"; only valid as the
 *       primary of a {@link #NewNone()} provider (which forces an off policy, {@link StorageChunkChecksumPolicy#NewOff()}).</li>
 * </ul>
 * A custom algorithm is a further {@link StorageChunkChecksumCalculator.Algorithm} implementation, passed as
 * the primary (e.g. {@code New(MyAlgorithm::new)}) and/or among the additional algorithms.
 * <p>
 * Each channel receives its own calculator via {@link #createCalculator()}; the stateful native algorithm
 * objects live as plain instance fields on that calculator's algorithms &mdash; no shared state, no
 * thread-safety hacks. That is why algorithms are supplied as factories: each calculator instantiates its
 * own fresh set.
 *
 * @see #New()
 * @see #DefaultAlgorithms()
 * @see StorageChunkChecksumCalculator
 * @see StorageChunkChecksumPolicy
 */
public interface StorageChunkChecksumProvider
{
	/**
	 * @return the {@link StorageChunkChecksumPolicy} governing the mode and strictness knobs.
	 */
	public StorageChunkChecksumPolicy policy();

	/**
	 * The KIND code of the chunk-checksum records this provider's calculators emit on writes (the primary
	 * algorithm's kind).
	 *
	 * @return the chunk-checksum KIND code of the primary algorithm.
	 */
	public long chunkChecksumKind();

	/**
	 * Creates a fresh {@link StorageChunkChecksumCalculator} for one storage channel. Each calculator owns its
	 * own algorithm instances (and therefore its own {@code MessageDigest} / {@code CRC32C} native objects), so
	 * multiple channels can call their calculators concurrently without sharing state.
	 *
	 * @return a new per-channel calculator.
	 */
	public StorageChunkChecksumCalculator createCalculator();

	/**
	 * Creates a fresh instance of this provider's <i>primary</i> (write)
	 * {@link StorageChunkChecksumCalculator.Algorithm} &mdash; the one whose
	 * {@link StorageChunkChecksumCalculator.Algorithm#kind() kind} equals {@link #chunkChecksumKind()}.
	 * Intended for <b>off-line</b> repair tooling (e.g. {@code StorageObjectRestorer}) that emits a
	 * {@code FileHeaderV1} + covering record without a running engine, composing the algorithm's file-free
	 * primitives with {@link StorageMetaRecord#writeFileHeaderV1(java.nio.ByteBuffer, long, byte[])}. The
	 * returned instance is stateful (native digest / running chain tip) and must not be shared across threads.
	 *
	 * @return a fresh primary algorithm instance.
	 */
	public StorageChunkChecksumCalculator.Algorithm createPrimaryAlgorithm();

	/**
	 * Creates the full verify-dispatch universe as a fresh map of KIND code to
	 * {@link StorageChunkChecksumCalculator.Algorithm} &mdash; the primary plus the additional verify-only
	 * algorithms, keyed by {@link StorageChunkChecksumCalculator.Algorithm#kind() kind} (the primary winning
	 * any KIND collision). The read-side counterpart to {@link #createPrimaryAlgorithm()}, for <b>off-line</b>
	 * tooling (e.g. the storage converter) that verifies existing records without a running engine. Using this
	 * rather than the static {@link #DefaultAlgorithms()} keeps a provider's <i>custom</i> verify algorithms
	 * reachable, so an unrecognized on-disk KIND is correctly an unknown-KIND anomaly rather than a silent gap.
	 * <p>
	 * The returned instances are stateful (native digest / running chain tip) and must not be shared across
	 * threads; a fresh map of fresh instances is returned on each call.
	 *
	 * @return a fresh KIND &rarr; algorithm map covering every algorithm this provider knows.
	 */
	public Map<Long, StorageChunkChecksumCalculator.Algorithm> createAlgorithmsByKind();



	/**
	 * @return the framework-default provider: <b>no checksum</b> &mdash; the
	 *         {@link StorageChunkChecksumCalculator.Algorithm.None None} algorithm paired with an off policy, so
	 *         nothing is emitted or verified and data files stay indistinguishable from a pre-feature engine.
	 *         Identical to {@link #NewNone()}; opt in to protection with {@link #NewSha256Chained()},
	 *         {@link #NewCrc32c()} or a {@link #New(Supplier) custom primary}.
	 */
	public static StorageChunkChecksumProvider New()
	{
		return NewNone();
	}

	/**
	 * Creates a provider with the given primary algorithm, the default {@link StorageChunkChecksumPolicy} and
	 * the {@link #DefaultAlgorithms() built-in} verify set. The primary is the algorithm used for writes; its
	 * {@link StorageChunkChecksumCalculator.Algorithm#kind() kind} becomes this provider's
	 * {@link #chunkChecksumKind()}.
	 *
	 * @param primary fresh-instance supplier of the primary (write) algorithm; must be non-{@code null}.
	 * @return a new {@link StorageChunkChecksumProvider} with the given primary.
	 */
	public static StorageChunkChecksumProvider New(
		final Supplier<StorageChunkChecksumCalculator.Algorithm> primary
	)
	{
		return New(StorageChunkChecksumPolicy.New(), primary, StorageChunkChecksumProvider::DefaultAlgorithms);
	}

	/**
	 * Creates a fully specified provider.
	 * <p>
	 * The {@code primary} algorithm is used for writes; {@code additionalAlgorithms} supplies the remaining
	 * verify-dispatch universe (so files written by other kinds still verify). Both are suppliers because the
	 * algorithms own stateful native objects and must be instantiated fresh per channel. Pass
	 * {@link #DefaultAlgorithms()} as {@code additionalAlgorithms} to retain the built-ins (recommended, so
	 * existing SHA-256 / CRC32C / chained stores remain verifiable).
	 *
	 * @param policy               the mode/strictness knobs; must be non-{@code null}.
	 * @param primary              fresh-instance supplier of the primary (write) algorithm; must be non-{@code null}.
	 * @param additionalAlgorithms fresh-instance supplier of the additional verify-only algorithms; must be
	 *                             non-{@code null} (use {@link #DefaultAlgorithms()} for the built-ins).
	 * @return a new {@link StorageChunkChecksumProvider}.
	 */
	public static StorageChunkChecksumProvider New(
		final StorageChunkChecksumPolicy                               policy              ,
		final Supplier<StorageChunkChecksumCalculator.Algorithm>       primary             ,
		final Supplier<List<StorageChunkChecksumCalculator.Algorithm>> additionalAlgorithms
	)
	{
		return new Default(notNull(policy), notNull(primary), notNull(additionalAlgorithms));
	}

	/**
	 * Creates a {@link StorageChunkChecksumCalculator.Algorithm.Crc32c CRC32C} primary provider with the
	 * default {@link StorageChunkChecksumPolicy}. CRC32C detects accidental corruption with a zero-copy,
	 * hardware-accelerated checksum but is <i>not</i> tamper-evident.
	 *
	 * @return a new CRC32C {@link StorageChunkChecksumProvider}.
	 */
	public static StorageChunkChecksumProvider NewCrc32c()
	{
		return New(StorageChunkChecksumCalculator.Algorithm.Crc32c::new);
	}

	/**
	 * Creates a {@link StorageChunkChecksumCalculator.Algorithm.Crc32c CRC32C} primary provider governed by the
	 * given policy.
	 *
	 * @param policy the mode/strictness knobs; must be non-{@code null}.
	 * @return a new CRC32C {@link StorageChunkChecksumProvider}.
	 */
	public static StorageChunkChecksumProvider NewCrc32c(final StorageChunkChecksumPolicy policy)
	{
		return New(
			policy                                              ,
			StorageChunkChecksumCalculator.Algorithm.Crc32c::new,
			StorageChunkChecksumProvider::DefaultAlgorithms
		);
	}

	/**
	 * Creates a chained-SHA-256 primary provider with the default {@link StorageChunkChecksumPolicy} and an
	 * all-zero initial chain seed. Each chunk's hash folds in the previous chunk's hash, so chunk reordering,
	 * insertion, deletion or substitution is detectable &mdash; not just single-chunk bit-rot. See
	 * {@link StorageChunkChecksumCalculator.Algorithm.Sha256Chained}.
	 *
	 * @return a new chained-SHA-256 {@link StorageChunkChecksumProvider}.
	 */
	public static StorageChunkChecksumProvider NewSha256Chained()
	{
		return NewSha256Chained(StorageChunkChecksumPolicy.New(), null);
	}

	/**
	 * Creates a chained-SHA-256 primary provider governed by the given policy, with an all-zero initial chain seed.
	 *
	 * @param policy the mode/strictness knobs; must be non-{@code null}.
	 * @return a new chained-SHA-256 {@link StorageChunkChecksumProvider}.
	 */
	public static StorageChunkChecksumProvider NewSha256Chained(final StorageChunkChecksumPolicy policy)
	{
		return NewSha256Chained(policy, null);
	}

	/**
	 * Creates a chained-SHA-256 primary provider governed by the given policy with a configurable initial chain
	 * seed. The seed is {@code tip_0}: it is stamped into the very first data file's {@code FileHeaderV1.chainRoot}
	 * and folded into that file's first chunk hash. (For tamper-evidence the <i>final</i> tip must be anchored
	 * externally &mdash; an attacker with full disk access can otherwise recompute the whole chain regardless of
	 * the seed; the seed alone does not provide that.)
	 * <p>
	 * Convenience for {@code New(policy, () -> new Sha256Chained(initialSeed), DefaultAlgorithms())}; the seed
	 * lives on the {@link StorageChunkChecksumCalculator.Algorithm.Sha256Chained} algorithm itself.
	 *
	 * @param policy      the mode/strictness knobs; must be non-{@code null}.
	 * @param initialSeed the chain seed; must be {@link StorageMetaRecord#LENGTH_HASH_SHA256} bytes, or
	 *                    {@code null} for the default all-zero seed. Defensively copied by the algorithm.
	 * @return a new chained-SHA-256 {@link StorageChunkChecksumProvider}.
	 */
	public static StorageChunkChecksumProvider NewSha256Chained(
		final StorageChunkChecksumPolicy policy     ,
		final byte[]                     initialSeed
	)
	{
		return New(
			policy                                                                       ,
			() -> new StorageChunkChecksumCalculator.Algorithm.Sha256Chained(initialSeed),
			StorageChunkChecksumProvider::DefaultAlgorithms
		);
	}

	/**
	 * Creates a "no checksum" provider: the {@link StorageChunkChecksumCalculator.Algorithm.None None}
	 * algorithm paired with an off policy ({@link StorageChunkChecksumPolicy#NewOff()}). It writes and verifies
	 * nothing, producing data files indistinguishable from those of a pre-feature engine.
	 * <p>
	 * Express "no checksum" this way, <b>not</b> by passing a kind-0 primary algorithm: kind {@code 0} is the
	 * reserved legacy-file sentinel. The built-in verify set is still supplied, so an <i>existing</i>
	 * SHA-256 / CRC32C / chained store opened with this provider has its meta records recognized (and skipped
	 * without verification) rather than treated as unknown KINDs.
	 *
	 * @return a new no-checksum {@link StorageChunkChecksumProvider}.
	 */
	public static StorageChunkChecksumProvider NewNone()
	{
		return New(
			StorageChunkChecksumPolicy.NewOff()               ,
			StorageChunkChecksumCalculator.Algorithm.None::new,
			StorageChunkChecksumProvider::DefaultAlgorithms
		);
	}

	/**
	 * The built-in chunk-checksum algorithms forming the default verify-dispatch universe: CRC32C and chained
	 * SHA-256 (with the default all-zero seed). Use this when supplying a custom primary while keeping
	 * the built-ins verifiable, or compose with extra algorithms.
	 * <p>
	 * Returns fresh instances on each call &mdash; the algorithms own stateful native objects and must not be
	 * shared across channels.
	 *
	 * @return a new list of the default algorithm instances.
	 */
	public static List<StorageChunkChecksumCalculator.Algorithm> DefaultAlgorithms()
	{
		return List.of(
			new StorageChunkChecksumCalculator.Algorithm.Crc32c()       ,
			new StorageChunkChecksumCalculator.Algorithm.Sha256Chained()
		);
	}



	/**
	 * The canonical {@link StorageChunkChecksumProvider}: holds the policy, the primary-algorithm factory and
	 * the additional-algorithms factory. It assembles a per-channel {@link StorageChunkChecksumCalculator} by
	 * instantiating the primary plus the additional set (the verify-dispatch universe) and keying them by KIND.
	 */
	public final class Default implements StorageChunkChecksumProvider, Immutable
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageChunkChecksumPolicy                               policy              ;
		private final Supplier<StorageChunkChecksumCalculator.Algorithm>       primary             ;
		private final Supplier<List<StorageChunkChecksumCalculator.Algorithm>> additionalAlgorithms;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final StorageChunkChecksumPolicy                               policy              ,
			final Supplier<StorageChunkChecksumCalculator.Algorithm>       primary             ,
			final Supplier<List<StorageChunkChecksumCalculator.Algorithm>> additionalAlgorithms
		)
		{
			super();
			this.policy               = policy              ;
			this.primary              = primary             ;
			this.additionalAlgorithms = additionalAlgorithms;
		}



		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		public final StorageChunkChecksumPolicy policy()
		{
			return this.policy;
		}

		@Override
		public final long chunkChecksumKind()
		{
			return this.primary.get().kind();
		}

		@Override
		public final StorageChunkChecksumCalculator createCalculator()
		{
			final StorageChunkChecksumCalculator.Algorithm primaryAlgorithm = notNull(this.primary.get());

			// fail early: chaining onto an unverified tip provides no tamper-evidence, so a chained primary
			// that emits must also verify (cannot extend a chain it never validated).
			if(primaryAlgorithm.isChained() && this.policy.emit() && !this.policy.verify())
			{
				throw new StorageExceptionConsistency(
					"Chained chunk-checksum emit requires verify (cannot extend an unverified chain tip)."
				);
			}

			final List<StorageChunkChecksumCalculator.Algorithm> all =
				new ArrayList<>(notNull(this.additionalAlgorithms.get()));
			all.add(primaryAlgorithm);

			// Map all algorithms by KIND for verify-time dispatch. The primary is added last so it wins any
			// KIND collision with a same-kind default: the verify-side instance for the primary's kind is then
			// the same instance used for writes (harmless — single-threaded channel, digest auto-resets).
			final Map<Long, StorageChunkChecksumCalculator.Algorithm> algorithmsByKind = new HashMap<>(all.size());
			for(final StorageChunkChecksumCalculator.Algorithm a : all)
			{
				algorithmsByKind.put(notNull(a).kind(), a);
			}

			return new StorageChunkChecksumCalculator.Default(this.policy, primaryAlgorithm, algorithmsByKind);
		}

		@Override
		public final StorageChunkChecksumCalculator.Algorithm createPrimaryAlgorithm()
		{
			// fresh, stateful instance (native digest / chain tip); single-threaded off-line use.
			return notNull(this.primary.get());
		}

		@Override
		public final Map<Long, StorageChunkChecksumCalculator.Algorithm> createAlgorithmsByKind()
		{
			// Same assembly as createCalculator(): additional verify set plus the primary added last, so the
			// primary wins any KIND collision with a same-kind default. No emit/verify policy gating here —
			// this is purely the off-line verify-dispatch universe (a fresh map of fresh, stateful instances).
			final List<StorageChunkChecksumCalculator.Algorithm> all =
				new ArrayList<>(notNull(this.additionalAlgorithms.get()));
			all.add(notNull(this.primary.get()));

			final Map<Long, StorageChunkChecksumCalculator.Algorithm> algorithmsByKind = new HashMap<>(all.size());
			for(final StorageChunkChecksumCalculator.Algorithm a : all)
			{
				algorithmsByKind.put(notNull(a).kind(), a);
			}
			return algorithmsByKind;
		}

	}

}
