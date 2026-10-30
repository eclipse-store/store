package test.microstream.afs.blobstore;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - commons
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

import java.util.Map;

import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import test.microstream.afs.AbstractFileSystemTest;


public abstract class BlobStoreFileSystemTest extends AbstractFileSystemTest<BlobStoreFileSystem>
{
	@SuppressWarnings("unchecked")
	public static void setEnv(final Map<String, String> newenv)
	{
//		try
//		{
//			try
//			{
//				final Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
//				final Field    theEnvironmentField     = processEnvironmentClass.getDeclaredField("theEnvironment");
//				theEnvironmentField.setAccessible(true);
//				final Map<String, String> env = (Map<String, String>)theEnvironmentField.get(null);
//				env.putAll(newenv);
//				final Field theCaseInsensitiveEnvironmentField =
//					processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
//				theCaseInsensitiveEnvironmentField.setAccessible(true);
//				final Map<String, String> cienv = (Map<String, String>)theCaseInsensitiveEnvironmentField.get(null);
//				cienv.putAll(newenv);
//			}
//			catch(final NoSuchFieldException e)
//			{
//				final Class<?>[]          classes = Collections.class.getDeclaredClasses();
//				final Map<String, String> env     = System.getenv();
//				for(final Class<?> cl : classes)
//				{
//					if("java.util.Collections$UnmodifiableMap".equals(cl.getName()))
//					{
//						final Field field = cl.getDeclaredField("m");
//						field.setAccessible(true);
//						final Object              obj = field.get(env);
//						final Map<String, String> map = (Map<String, String>)obj;
//						map.clear();
//						map.putAll(newenv);
//					}
//				}
//			}
//		}
//		catch(final Exception e)
//		{
//			throw new RuntimeException(e);
//		}
	}
	
	
	protected BlobStoreFileSystemTest()
	{
		super();
	}

	protected BlobStoreFileSystemTest(final String root)
	{
		super(root);
	}
	
	@Override
	protected void cleanup()
	{
		super.cleanup();
		
		this.fileSystem.ioHandler().connector().close();
	}
	
}
