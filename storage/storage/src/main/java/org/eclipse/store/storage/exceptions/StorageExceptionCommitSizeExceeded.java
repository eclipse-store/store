package org.eclipse.store.storage.exceptions;

@SuppressWarnings("serial")
public class StorageExceptionCommitSizeExceeded extends StorageException
{

	public StorageExceptionCommitSizeExceeded(String message)
	{
		super(message);
	}

	public StorageExceptionCommitSizeExceeded(int channelIndex, long commitSize)
	{
		super("Cannel " + channelIndex + " store size " + commitSize  + " bytes exceeds technical limit of 2^31 bytes");
	}

}
