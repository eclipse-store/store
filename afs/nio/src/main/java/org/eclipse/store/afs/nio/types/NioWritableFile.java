package org.eclipse.store.afs.nio.types;

/*-
 * #%L
 * EclipseStore Abstract File System - Java NIO
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

import static org.eclipse.serializer.util.X.mayNull;
import static org.eclipse.serializer.util.X.notNull;

import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.serializer.collections.XArrays;

public interface NioWritableFile extends NioReadableFile, AWritableFile
{
	public static NioWritableFile New(
        final AFile actual,
        final Object user  ,
        final Path   path
    )
    {
        return NioWritableFile.New(actual, user, path, null);
    }
    
    public static NioWritableFile New(
    	final AFile       actual     ,
    	final Object      user       ,
    	final Path        path       ,
    	final FileChannel fileChannel
    )
    {
        return new NioWritableFile.Default<>(
            notNull(actual),
            notNull(user)  ,
            notNull(path)  ,
            mayNull(fileChannel)
        );
    }
    
    public class Default<U> extends NioReadableFile.Default<U> implements NioWritableFile
    {
    	///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
    	
        protected Default(
        	final AFile       actual     ,
        	final U           user       ,
        	final Path        path       ,
        	final FileChannel fileChannel
        )
        {
            super(actual, user, path, fileChannel);
        }
        
        
        
        ///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
        
        @Override
        protected void validateOpenOptions(final OpenOption... options)
        {
        	/*
        	 * override super class (readable) implementation to do no validation
        	 * since everything is allowed for writable files.
        	 */
        }
        
        @Override
        protected OpenOption[] normalizeOpenOptions(final OpenOption... options)
        {
        	// super class implementation ensures READ
    		final OpenOption[] superOptions = super.normalizeOpenOptions(options);
    		
    		// this implementation ensures WRITE
    		return XArrays.ensureContained(superOptions, StandardOpenOption.WRITE);
        }
                
    }
    
}
