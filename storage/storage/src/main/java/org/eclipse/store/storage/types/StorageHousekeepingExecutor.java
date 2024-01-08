package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public interface StorageHousekeepingExecutor
{

	public boolean performIssuedFileCleanupCheck(long nanoTimeBudget);
	
	public boolean performIssuedGarbageCollection(long nanoTimeBudget);
	
	public boolean performIssuedEntityCacheCheck(long nanoTimeBudget, StorageEntityCacheEvaluator evaluator);
	
	
	public boolean performFileCleanupCheck(long nanoTimeBudget);
	
	public boolean performGarbageCollection(long nanoTimeBudget);
	
	public boolean performEntityCacheCheck(long nanoTimeBudget);

	public boolean performTransactionFileCheck(boolean checkSize);
	
}
