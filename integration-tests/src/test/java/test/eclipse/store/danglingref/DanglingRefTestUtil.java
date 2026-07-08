package test.eclipse.store.danglingref;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.store.storage.types.StorageEventLogger;

/**
 * Shared helpers for the dangling-reference validation tests.
 */
final class DanglingRefTestUtil
{
	/**
	 * A fabricated object id safely inside the OID range (which starts at one quintillion + 1)
	 * but far above anything a small test storage will ever assign.
	 */
	static final long FAKE_OID_BASE = 1_000_000_000_900_000_000L;

	/**
	 * Walks the cause chain (including suppressed exceptions) for a throwable of the given type.
	 */
	static <T extends Throwable> T findInCauseChain(final Throwable root, final Class<T> type)
	{
		final List<Throwable> queue = new ArrayList<>();
		queue.add(root);
		for(int i = 0; i < queue.size(); i++)
		{
			final Throwable current = queue.get(i);
			if(current == null)
			{
				continue;
			}
			if(type.isInstance(current))
			{
				return type.cast(current);
			}
			if(current.getCause() != null && !queue.contains(current.getCause()))
			{
				queue.add(current.getCause());
			}
			for(final Throwable suppressed : current.getSuppressed())
			{
				if(!queue.contains(suppressed))
				{
					queue.add(suppressed);
				}
			}
		}
		return null;
	}

	/**
	 * Precondition assertion for heal-success tests: healing coverage exists only if the store
	 * was actually rejected at least once. Without this, a change to the lazy storer's skip
	 * semantics would silently turn the heal tests into plain store tests that stay green with
	 * zero heal coverage.
	 */
	static void assertRejectionsRecorded(final RecordingEventLogger recorder)
	{
		if(recorder.eventCount() == 0)
		{
			throw new AssertionError(
				"precondition failed: no dangling-reference rejection was reported"
				+ " - the store never went through the healing path, the test covers nothing"
			);
		}
	}

	/**
	 * Records every dangling-reference rejection event with its channel. Thread-safe: channels
	 * report concurrently from their own threads.
	 */
	static final class RecordingEventLogger implements StorageEventLogger
	{
		final List<long[]> reportedObjectIds = new ArrayList<>();

		private final Set<Integer> reportingChannels = new HashSet<>();

		@Override
		public void logStoreDetectedDanglingReferences(final int channelIndex, final long[] objectIds)
		{
			synchronized(this.reportedObjectIds)
			{
				this.reportedObjectIds.add(objectIds);
				this.reportingChannels.add(channelIndex);
			}
		}

		int eventCount()
		{
			synchronized(this.reportedObjectIds)
			{
				return this.reportedObjectIds.size();
			}
		}

		int distinctReportingChannels()
		{
			synchronized(this.reportedObjectIds)
			{
				return this.reportingChannels.size();
			}
		}
	}

	private DanglingRefTestUtil()
	{
		throw new UnsupportedOperationException();
	}
}
