package org.eclipse.store.gigamap.misc.it;

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

import com.github.javafaker.Faker;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageChannelCountProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.text.NumberFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class GigaMapTestBase<T>
{
	private final Logger           logger = LoggerFactory.getLogger(this.getClass());
	private final long             testDataAmount;
	private EmbeddedStorageManager storage;
	private GigaMap<T>             gigaMap;
	
	Path tempDir;

	protected GigaMapTestBase(final long testDataAmount)
	{
		super();
		this.testDataAmount    = testDataAmount;
	}
	
	public Logger logger()
	{
		return this.logger;
	}
	
	public long testDataAmount()
	{
		return this.testDataAmount;
	}
	
	public EmbeddedStorageManager storage()
	{
		return this.storage;
	}
	
	public GigaMap<T> gigaMap()
	{
		return this.gigaMap;
	}
	
	@BeforeAll
	protected final void setup(@TempDir final Path location)
	{
		this.tempDir = location;
		this.storage = this.startStorage();
		this.gigaMap = this.ensureRoot(this.storage);
	}
	
	@Test
	void testSize()
	{
		assertEquals(this.testDataAmount, this.gigaMap.size());
	}
	
	@Test
	void testIteratorSize()
	{
		final Counter iteratorSize = new Counter();
		this.gigaMap().iterate(entity -> iteratorSize.increment());
		assertEquals(this.testDataAmount, iteratorSize.get());
	}
		
	@AfterAll
	@SuppressWarnings("unchecked")
	protected void shutdown()
	{
		this.storage.shutdown();
		
		this.storage = this.startStorage();
		this.gigaMap = (GigaMap<T>)this.storage.root();
		
		assertEquals(this.testDataAmount, this.gigaMap.size());
		
		this.storage.shutdown();
	}
	
	private EmbeddedStorageManager startStorage()
	{
		return EmbeddedStorage.Foundation()
			.setConfiguration(
				StorageConfiguration.Builder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setChannelCountProvider(StorageChannelCountProvider.New(4))
					.createConfiguration()
			)
			.createEmbeddedStorageManager()
			.start()
		;
	}
	
	
	@SuppressWarnings("unchecked")
	private GigaMap<T> ensureRoot(final EmbeddedStorageManager storage)
	{
		GigaMap<T> gigaMap = (GigaMap<T>)storage.root();
		if(gigaMap != null)
		{
			return gigaMap;
		}
		
		gigaMap = GigaMap.New();
		
		this.createIndices(gigaMap);

		storage.setRoot(gigaMap);
		storage.storeRoot();
		
		this.fillWithRandomData(gigaMap);
		
		gigaMap.store();
		
		return gigaMap;
	}
	
	protected abstract void createIndices(GigaMap<T> gigaMap);
	
	private void fillWithRandomData(final GigaMap<T> gigaMap)
	{
		final Faker        faker        = new Faker();
		final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
		final long         amount       = this.testDataAmount;
		for(long i = 1; i <= amount; i++)
		{
			gigaMap.add(this.createEntity(faker, i - 1));
			
			if(i % 100_000 == 0)
			{
				gigaMap.store();

				this.logger.info("Created data {} / {}", numberFormat.format(i), numberFormat.format(amount));
			}
		}
		
		this.logger.info("Data creation finished");
	}
	
	protected abstract T createEntity(Faker faker, long index);
	
}
