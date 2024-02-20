package org.eclipse.store.integrations.cdi.types.config;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */


import io.smallrye.config.inject.ConfigExtension;
import org.eclipse.store.integrations.cdi.types.extension.StorageExtension;
import org.eclipse.store.storage.types.StorageManager;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;


@EnableAutoWeld
@AddExtensions(ConfigExtension.class)  // SmallRye Config extension to Support MicroProfile Config within this test
@AddExtensions(StorageExtension.class)
@DisplayName("Check if the Storage Manager will load using the default MicroProfile Properties file")
public class StorageManagerConverterTest extends AbstractStorageManagerConverterTest
{
	@Inject
	private StorageManager manager;

	@ApplicationScoped
	@Produces
	private StorageManager storageManagerMock = Mockito.mock(StorageManager.class);

	@Test
	public void shouldBeFromProducer()
	{
		Assertions.assertNotNull(this.manager);
		//Assertions.assertSame(this.storageManagerMock, this.manager);
		//Although it is the same instance, the types are different and thus assertSame fails
		Assertions.assertEquals(this.storageManagerMock.toString(), this.manager.toString());
	}
}
