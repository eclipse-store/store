package test.eclipse.store.recovery;

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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Recovery only ever cuts within the head file. When the cross-channel consensus store lies below a
 * channel's head-file baseline - in a sealed, earlier file - it cannot be reached by a head-only
 * rollback, so recovery must refuse to start loudly rather than silently discard the committed data
 * the intact channels still hold. Small files force the ahead channel to seal the consensus store
 * away before the crash.
 * <p>
 * Below-baseline is refused both before and after this change (never silent); this locks the precise
 * diagnosis added here, which before was reported only via a generic consensus error.
 */
@Timeout(120)
class BelowBaselineDivergenceFailsLoudlyTest
{
	private static final int MAX_FILE_SIZE = 4096;
	private static final int PAYLOAD_SIZE  = 3000;
	private static final int ROUNDS        = 8   ;

	@TempDir
	Path directory;

	private EmbeddedStorageManager storage ;
	private EmbeddedStorageManager reloaded;

	@AfterEach
	void shutdown()
	{
		for(final EmbeddedStorageManager manager : new EmbeddedStorageManager[]{this.reloaded, this.storage})
		{
			if(manager != null && manager.isRunning())
			{
				try
				{
					manager.shutdown();
				}
				catch(final RuntimeException ignored)
				{
					// best effort
				}
			}
		}
	}

	@Test
	void consensusBelowASealedFileRefusesToStart()
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE));
		final Root root = new Root();
		this.storage.setRoot(root);
		for(int version = 1; version <= ROUNDS; version++)
		{
			final byte[] payload = new byte[PAYLOAD_SIZE];
			payload[0]   = (byte)version;
			root.version = version;
			root.payload = payload;
			this.storage.storeRoot();
		}
		this.storage.shutdown();
		this.storage = null;

		// the ahead channel rolled the early stores into sealed files
		assertTrue(RecoverySimulation.dataFiles(this.directory, 0).size() > 1,
			"staging: channel 0 must have rolled over so early stores are sealed");

		// channel 1 keeps only its first store: the consensus now lies below channel 0's head baseline
		RecoverySimulation.cutTrailingStores(this.directory, 1, ROUNDS - 1);

		final RuntimeException failure = assertThrows(RuntimeException.class, () ->
			this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE)));
		assertTrue(causeChain(failure).contains("baseline"),
			"the refusal must name the head-file baseline, not fail for another reason: " + causeChain(failure));
	}

	private static String causeChain(final Throwable throwable)
	{
		final StringBuilder chain = new StringBuilder();
		for(Throwable t = throwable; t != null; t = t.getCause())
		{
			if(chain.length() > 0)
			{
				chain.append(" <- ");
			}
			chain.append(t.getMessage());
		}
		return chain.toString();
	}


	public static class Root
	{
		int    version;
		byte[] payload;
	}
}
