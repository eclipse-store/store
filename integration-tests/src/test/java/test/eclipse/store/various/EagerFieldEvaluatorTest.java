package test.eclipse.store.various;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.PersistenceEagerStoringFieldEvaluator;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that a custom {@link PersistenceEagerStoringFieldEvaluator} registered via
 * {@code onConnectionFoundation(cf -> cf.setReferenceFieldEagerEvaluator(...))} actually
 * forces eager traversal through annotated fields while leaving unannotated fields lazy.
 *
 * <p>Scenario:
 * <ul>
 *   <li>A root holds two {@link Holder} references — one annotated {@link StoreEager}, one not.</li>
 *   <li>After the initial store both holders are registered (have an objectId).</li>
 *   <li>On reopen, the {@code value} field of <em>both</em> holders is mutated in place.</li>
 *   <li>{@code storageManager.storeRoot()} is invoked — a lazy convenience store.</li>
 *   <li>By the lazy rule the holders should be skipped (already registered children),
 *       but the per-field eager evaluator must force the eager-marked field's holder
 *       to be re-traversed and re-written.</li>
 * </ul>
 *
 * <p>Expectation after reopening the storage:
 * <ul>
 *   <li>Eager-annotated holder reflects the new value.</li>
 *   <li>Non-annotated (lazy) holder still reflects the old value.</li>
 * </ul>
 */
public class EagerFieldEvaluatorTest
{
	@TempDir
	Path storageDir;

	@Test
	void eagerAnnotatedFieldIsRewrittenOnLazyRootStore()
	{
		// --- 1) Initial setup: write the root with two holders carrying old values ---
		try (EmbeddedStorageManager manager = newManager(storageDir)) {
			final Root root = new Root();
			root.eagerHolder = new Holder("eager-OLD");
			root.lazyHolder = new Holder("lazy-OLD");
			manager.setRoot(root);
			manager.storeRoot();
		}

		// --- 2) Reopen, mutate both holders in place, then storeRoot() (lazy convenience) ---
		try (EmbeddedStorageManager manager = newManager(storageDir)) {
			final Root root = (Root) manager.root();
			// Sanity check: persisted state from step 1.
			assertEquals("eager-OLD", root.eagerHolder.value);
			assertEquals("lazy-OLD", root.lazyHolder.value);

			// In-place mutation of an already-registered child object.
			// A pure lazy walk from root would skip both holders.
			root.eagerHolder.value = "eager-NEW";
			root.lazyHolder.value = "lazy-NEW";

			// Lazy convenience store on the root. The per-field evaluator must
			// force descent through Root#eagerHolder and re-write that Holder.
			manager.storeRoot();
		}

		// --- 3) Reopen again and verify what actually hit the disk ---
		try (EmbeddedStorageManager manager = newManager(storageDir)) {
			final Root root = (Root) manager.root();

			// Eager-annotated field: must reflect the new value.
			assertEquals("eager-NEW", root.eagerHolder.value,
					"Field annotated with @StoreEager should have been re-written by the per-field eager evaluator.");

			// Non-annotated field: lazy walk skipped the already-registered holder,
			// so the in-place mutation must NOT have been persisted.
			assertEquals("lazy-OLD", root.lazyHolder.value,
					"Non-annotated field should follow lazy semantics: in-place mutation of a registered child must not be persisted by storeRoot().");
		}
	}

	private static EmbeddedStorageManager newManager(final Path dir)
	{
		return EmbeddedStorage.Foundation(dir)
				.onConnectionFoundation(cf -> cf.setReferenceFieldEagerEvaluator(new StoreEagerEvaluator()))
				.createEmbeddedStorageManager()
				.start();
	}

	// ----- Test fixtures -------------------------------------------------------------

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface StoreEager
	{
		// marker
	}

	/**
	 * Field evaluator that forces eager traversal for any field annotated with {@link StoreEager}.
	 */
	public static class StoreEagerEvaluator implements PersistenceEagerStoringFieldEvaluator
	{
		@Override
		public boolean isEagerStoring(final Class<?> clazz, final Field field)
		{
			return field.isAnnotationPresent(StoreEager.class);
		}
	}

	public static class Root
	{
		@StoreEager
		Holder eagerHolder;

		Holder lazyHolder;
	}

	public static class Holder
	{

		String value;

		Holder()
		{
			// for deserialization
		}

		Holder(final String value)
		{
			this.value = value;
		}
	}
}

