package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link IndexLocation} values and the JVM-wide name table {@link IndexLocations}.
 */
class IndexLocationTest
{
	private static final String NAME = "index-location-test";

	@AfterEach
	void unbind()
	{
		IndexLocations.unbind(NAME);
	}

	@Test
	void absoluteLocationIsItsDirectory()
	{
		final Path          directory = Path.of("some", "index");
		final IndexLocation location  = IndexLocation.Absolute(directory);

		assertFalse(location.isNamed());
		assertEquals(directory, location.directory());
		assertNull(location.name());
		assertEquals(directory, location.resolve());
	}

	@Test
	void namedLocationResolvesThroughTheBinding()
	{
		final IndexLocation location = IndexLocation.Named(NAME);
		assertTrue(location.isNamed());
		assertEquals(NAME, location.name());
		assertNull(location.directory());

		final Path directory = Path.of("bound");
		IndexLocations.bind(NAME, directory);
		assertEquals(directory, location.resolve());
	}

	@Test
	void locationsAreValues()
	{
		assertEquals(IndexLocation.Named(NAME), IndexLocation.Named(NAME));
		assertEquals(IndexLocation.Named(NAME).hashCode(), IndexLocation.Named(NAME).hashCode());
		assertEquals(IndexLocation.Absolute(Path.of("a")), IndexLocation.Absolute(Path.of("a")));
		assertNotEquals(IndexLocation.Absolute(Path.of("a")), IndexLocation.Absolute(Path.of("b")));
		assertNotEquals(IndexLocation.Named("a"), IndexLocation.Absolute(Path.of("a")));
	}

	@Test
	void invalidLocationsAreRejected()
	{
		assertThrows(RuntimeException.class         , () -> IndexLocation.Absolute(null));
		assertThrows(RuntimeException.class         , () -> IndexLocation.Named(null));
		assertThrows(IllegalArgumentException.class , () -> IndexLocation.Named(""));
		assertThrows(IllegalArgumentException.class , () -> IndexLocation.Named("  "));
		assertThrows(RuntimeException.class         , () -> IndexLocations.bind(NAME, null));
	}

	@Test
	void anUnboundNameFailsAndNamesTheName()
	{
		assertNull(IndexLocations.lookup(NAME));
		final IllegalStateException failure = assertThrows(
			IllegalStateException.class,
			() -> IndexLocation.Named(NAME).resolve()
		);
		assertTrue(failure.getMessage().contains("\"" + NAME + "\""), failure.getMessage());
	}

	@Test
	void rebindingReplacesAndUnbindingRemoves()
	{
		IndexLocations.bind(NAME, Path.of("first"));
		IndexLocations.bind(NAME, Path.of("second"));
		assertEquals(Path.of("second"), IndexLocations.resolve(NAME));

		assertTrue(IndexLocations.unbind(NAME));
		assertFalse(IndexLocations.unbind(NAME));
		assertNull(IndexLocations.lookup(NAME));
	}

	@Test
	void bindingsAreVisibleOnOtherThreads()
	{
		// a map behind a Lazy may be loaded on any thread
		IndexLocations.bind(NAME, Path.of("bound"));
		assertEquals(
			Path.of("bound"),
			CompletableFuture.supplyAsync(() -> IndexLocations.resolve(NAME)).join()
		);
	}

}
