
package org.eclipse.store.integrations.cdi.types;

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
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import java.util.Set;


@EnableAutoWeld
@AddExtensions(StorageExtension.class)
public class StorageTest
{
	// Test if a class annotated with @Storage is converted into an ApplicationScoped bean.
	@Inject
	private Agenda agenda;

	@Inject
	private BeanManager beanManager;

	@ApplicationScoped
	@Produces
	// StorageBean requires a StorageManager
	private StorageManager storageManagerMock = Mockito.mock(StorageManager.class);

	@Test
	@DisplayName("Should check if it create an instance by annotation")
	public void shouldCreateInstance()
	{
		Assertions.assertNotNull(this.agenda);
		this.agenda.add("JUnit");

		// Another way of testing we have only 1 instance of @Storage bean.
		final Agenda instance = CDI.current()
				.select(Agenda.class)
				.get();
		Assertions.assertEquals("JUnit", instance.getNames()
				.iterator()
				.next());
	}

	@Test
	public void shouldCreateApplicationScopedBean()
	{
		final Set<Bean<?>> beans = this.beanManager.getBeans(Agenda.class);
		Assertions.assertEquals(1, beans.size());
		final Bean<?> storageBean = beans.iterator()
				.next();
		Assertions.assertEquals(ApplicationScoped.class, storageBean.getScope());

	}
}
