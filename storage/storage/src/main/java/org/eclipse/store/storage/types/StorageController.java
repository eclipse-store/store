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

import org.eclipse.store.storage.exceptions.StorageExceptionShutdown;


/**
 * The StorageController interface defines the contract for managing and controlling a storage system.
 * It provides methods to initialize, start, monitor, and shut down the storage operations, ensuring
 * appropriate handling of state transitions and task processing capabilities.
 * <p>
 * This interface extends both StorageActivePart and AutoCloseable, combining functionalities for
 * active storage management and resource cleanup during lifecycle management.
 */
public interface StorageController extends StorageActivePart, AutoCloseable
{
	/**
	 * "Starts" the storage controlled by this {@link StorageController} instance, with "starting" meaning:<br>
	 * <ul>
	 * <li>Reading, indexing and potentially caching all the persisted data in the storage.</li>
	 * <li>Recovering from a previous unclean shutdown by truncating any partially written tail of the
	 *     last store, so that the in-memory state matches the last fully persisted state.</li>
	 * <li>Starting storage managing threads to execute requests like storing, loading and issued utility functions.</li>
	 * </ul>
	 * After this method returns successfully, the storage is ready to accept tasks (see
	 * {@link #isAcceptingTasks()}) and the registered root reference (if any) has been resolved.
	 *
	 * @return this to allow writing of fluent code.
	 */
	public StorageController start();

	/**
	 * Issues a command to shut down all active threads managing the storage.
	 * <p>
	 * <b>Calling {@code shutdown()} is not required for data integrity.</b> The storage is designed so
	 * that every successful {@code store(...)} call physically commits its data before returning;
	 * a process crash, kill, or {@code System.exit(0)} therefore cannot corrupt the persisted data, and
	 * the next {@link #start()} will resume from the last fully persisted state.
	 * <p>
	 * That said, a clean shutdown is still recommended. Using {@link AutoCloseable} /
	 * try-with-resources is the preferred idiom for shutting the storage down at the end of its
	 * lifecycle. An explicit shutdown:
	 * <ul>
	 *   <li>Releases the storage lock file promptly. After a crash the lock entry remains on disk
	 *       and the next {@link #start()} is blocked until the lock expires; a clean shutdown
	 *       avoids that delay.</li>
	 *   <li>Releases held file handles and other OS resources owned by the storage threads.</li>
	 *   <li>Allows stopping the storage while the application keeps running (e.g. to change
	 *       configuration, copy or back up the storage files, then call {@link #start()} again).</li>
	 * </ul>
	 *
	 * @return <code>true</code> after a successful shutdown or <code>false</code>
	 *         if an internal {@link InterruptedException} happened.
	 */
	public boolean shutdown();
	
	/**
	 * Checks whether the storage controlled by this instance is currently accepting tasks.
	 * This indicates the readiness of the storage to handle and process new tasks,
	 * based on its internal state and operational status.
	 *
	 * @return true if the storage is currently accepting tasks, false otherwise.
	 */
	public boolean isAcceptingTasks();
	
	/**
	 * Checks whether the storage controlled by this instance is currently running.
	 * This method determines if the storage is operational and handling tasks
	 * after being successfully started and not yet shut down.
	 *
	 * @return true if the storage is running, false otherwise.
	 */
	public boolean isRunning();
	
	/**
	 * Determines whether the storage controlled by this instance is currently in the process
	 * of starting up. This state indicates that the storage is initializing, which includes
	 * activities such as reading and indexing data, initializing resources, or starting
	 * background management processes.
	 *
	 * @return true if the storage is in the process of starting up, false otherwise.
	 */
	public boolean isStartingUp();
	
	/**
	 * Determines whether this instance is currently in the process of shutting down.
	 *
	 * @return true if the storage is in the process of shutting down, false otherwise.
	 */
	public boolean isShuttingDown();
	
	/**
	 * Checks whether the storage controlled by this instance has been shut down.
	 * <p>
	 * This method determines the shutdown state by checking if the storage is no longer running.
	 *
	 * @return true if the storage is shut down, false otherwise.
	 */
	public default boolean isShutdown()
	{
		return !this.isRunning();
	}
	
	/**
	 * Ensures that the storage is accepting tasks by internally checking its state.
	 * This method verifies the ability of the storage to process new tasks and may
	 * throw an exception if the storage is not in a state to accept tasks.
	 * <p>
	 * Depending on the internal implementation, it could check conditions like whether
	 * the storage is running, not shutting down, and has not encountered critical errors.
	 *
	 * @throws IllegalStateException if the storage is not currently accepting tasks.
	 */
	public void checkAcceptingTasks();
	
	/**
	 * Retrieves the timestamp that represents the initialization time of the storage.
	 * This method provides the time when the initialization process was completed successfully.
	 *
	 * @return the initialization time as a long value representing the timestamp
	 *         in milliseconds since the epoch (January 1, 1970, 00:00:00 GMT).
	 */
	public long initializationTime();
	
	/**
	 * Retrieves the timestamp that represents the time when the storage
	 * entered its current operational mode. This method provides a long
	 * value indicating the moment when the storage transitioned into its
	 * active state.
	 *
	 * @return the timestamp as a long value representing the time in milliseconds
	 *         since the epoch (January 1, 1970, 00:00:00 GMT) when the storage
	 *         entered its current operational mode.
	 */
	public long operationModeTime();
	
	/**
	 * Computes the duration of the initialization process for the storage.
	 * This value represents the time elapsed between the completion of the
	 * initialization process and the moment the storage entered its
	 * operational mode.
	 *
	 * @return the initialization duration as a long value representing the
	 *         time in milliseconds.
	 */
	public default long initializationDuration()
	{
		return this.operationModeTime() - this.initializationTime();
	}
	
	/**
	 * Closes the storage controller by shutting down all associated resources and
	 * threads managing the storage. This method ensures that the storage is properly
	 * terminated and no background processes are left running.
	 * <p>
	 * If the shutdown process fails, a {@link StorageExceptionShutdown} is thrown with
	 * an appropriate error message and cause if available.
	 *
	 * @throws StorageExceptionShutdown if the shutdown process is unsuccessful.
	 */
	@Override
	public default void close() throws StorageExceptionShutdown
	{
		final boolean success;
		try
		{
			success = this.shutdown();
		}
		catch(final Exception e)
		{
			throw new StorageExceptionShutdown("Shutdown failed.", e);
		}
		
		if(!success)
		{
			throw new StorageExceptionShutdown("Shutdown failed.");
		}
	}

}
