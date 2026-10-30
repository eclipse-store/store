package test.microstream.afs;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.afs.types.AReadableFile;
import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.XArrays;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;


@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public abstract class AbstractFileSystemTest<FS extends AFileSystem>
{
	final String   filePrefix = "test";
	final String   fileSuffix = "dat";
	final String   fileName   = this.filePrefix + "." + this.fileSuffix;
	
	final String[] dirPath1 ;
	final String[] filePath1;
	                        
	final String[] dirPath2 ;
	final String[] filePath2;
	                        
	final String[] dirPath3 ;
	final String[] filePath3;
		
	final byte[]   data       = VarString.New()
		.repeat(1_000, "abcdefghijklmnopqrstuvwxyz012345789")
		.toString()
		.getBytes(StandardCharsets.UTF_8)
	;
	
	protected FS fileSystem;

	protected AbstractFileSystemTest()
	{
		this(null);
	}
	
	protected AbstractFileSystemTest(final String root)
	{
		super();
		
		this.dirPath1   = this.createDirPath(root, 1);
		this.filePath1  = XArrays.add(this.dirPath1, this.fileName);
		
		this.dirPath2   = this.createDirPath(root, 2);
		this.filePath2  = XArrays.add(this.dirPath2, this.fileName);
		
		this.dirPath3   = this.createDirPath(root, 3);
		this.filePath3  = XArrays.add(this.dirPath3, this.fileName);
	}
	
	private String[] createDirPath(final String root, final int number)
	{
		return root != null && root.length() > 0
			? new String[]{root, "microstream_afs_test_" + number, "test_dir", "test_subdir"}
			: new String[]{"microstream_afs_test_" + number, "test_dir", "test_subdir"}
		;
	}

	@BeforeAll
	protected void setup()
	{
		this.fileSystem = this.setupFileSystem();
	}

	protected abstract FS setupFileSystem();
	
	@AfterAll
	protected void cleanup()
	{
		this.cleanupFileSystem();
	}

	protected abstract void cleanupFileSystem();

	@Test
	@Order(0)
	void nameTest()
	{
		assertEquals(
			this.filePrefix,
			this.fileSystem.deriveFileName(this.fileName)
		);
		assertEquals(
			this.fileSuffix,
			this.fileSystem.deriveFileType(this.fileName)
		);
		
		final AFile file = this.fileSystem.ensureFilePath(this.filePath1);
		assertEquals(
			this.filePrefix,
			this.fileSystem.getFileName(file)
		);
		assertEquals(
			this.fileSuffix,
			this.fileSystem.getFileType(file)
		);
	}
	
	@Test
	@Order(1)
	void sharedTest()
	{
		final AFile file    = this.fileSystem.ensureFilePath(this.filePath1);
		final AReadableFile rFile1  = file.useReading();
		final AReadableFile rFile1a = file.useReading();
		final AReadableFile rFile2  = file.useReading("Other user 2");
		final AReadableFile rFile3  = file.useReading("Other user 3");

		assertSame(rFile1, rFile1a);
		assertNotSame(rFile1, rFile2);
		assertNotSame(rFile1, rFile3);
		assertNotSame(rFile2, rFile3);

		assertThrows(Exception.class, () -> file.useWriting());

		rFile1.release();
		rFile2.release();
		rFile3.release();

		assertDoesNotThrow(() -> file.useWriting().release());
	}
	
	@Test
	@Order(2)
	void exclusiveTest()
	{
		final AFile         file    = this.fileSystem.ensureFilePath(this.filePath1);
		final AWritableFile wFile1  = file.useWriting();
		final AWritableFile wFile1a = file.useWriting();
		
		assertSame(wFile1, wFile1a);
		
		assertThrows(Exception.class, () -> file.useWriting("Other user 2"));
		assertThrows(Exception.class, () -> file.useReading("Other user 2"));
		
		wFile1.release();
	}
	
	@Test
	@Order(3)
	void directoryTest()
	{
		final ADirectory dir  = this.fileSystem.ensureDirectoryPath(this.dirPath1);
		final AFile      file = this.fileSystem.ensureFilePath(this.filePath1);
		
		assertSame(dir, file.parent());
	}
	
	@Test
	@Order(4)
	void downgradeTest()
	{
		final AFile         file  = this.fileSystem.ensureFilePath(this.filePath1);
		final AReadableFile rFile = file.useWriting().downgrade();
		
		assertThrows(Exception.class, () -> file.useWriting("Other user 2"));
		assertDoesNotThrow(() -> file.useReading("Other user 2").release());
		
		rFile.release();
	}
	
	@Test
	@Order(5)
	void ensurePathTest()
	{
		final ADirectory dir1   = this.fileSystem.ensureDirectoryPath(this.dirPath1);
		final AFile      file1  = this.fileSystem.ensureFilePath(this.filePath1);
		final ADirectory dir2   = this.fileSystem.ensureDirectoryPath(this.dirPath1);
		final AFile      file2  = this.fileSystem.ensureFilePath(this.filePath1);
		final AFile      file2a = dir2.ensureFile(file2.identifier());
		
		assertSame(dir1, dir2);
		assertSame(file1, file2);
		assertSame(file2, file2a);
	}
	
	/**
	 * Creates file1
	 */
	@Test
	@Order(6)
	void writeTest()
	{
		final AWritableFile wFile1 = this.fileSystem.ensureFilePath(this.filePath1).useWriting();
		
		// the return value of the AFS call is not asserted here
//		assertTrue(wFile1.ensureExists());
		wFile1.ensureExists();
		
		final long   size  = wFile1.writeBytes(ByteBuffer.wrap(this.data));
		
		assertEquals(this.data.length, size);
		
		wFile1.release();
	}
	
	/**
	 * Reads file1
	 */
	@Test
	@Order(7)
	void readTest()
	{
		final AReadableFile rFile1     = this.fileSystem.ensureFilePath(this.filePath1).useReading();
		final ByteBuffer    allBuffer  = rFile1.readBytes();
		
		assertEquals(this.data.length, allBuffer.remaining());

		final int        slice      = this.data.length / 3;
		final ByteBuffer partBuffer = rFile1.readBytes(slice, slice);
		
		assertEquals(ByteBuffer.wrap(this.data, slice, slice), partBuffer);
		
		rFile1.release();
	}
	
	/**
	 * Moves file1 -> file2
	 */
	@Test
	@Order(8)
	void moveTest()
	{
		// exists at this moment
		final AWritableFile wFile1 = this.fileSystem.ensureFilePath(this.filePath1).useWriting();
		// doesn't exist at this moment
		final AWritableFile wFile2 = this.fileSystem.ensureFilePath(this.filePath2).useWriting();
				
		final long size1 = wFile1.size();
		
		// the return value of the AFS call is not asserted here
//		assertTrue(wFile2.parent().ensureExists());
		wFile2.parent().ensureExists();
		
		wFile1.moveTo(wFile2);
		
		assertFalse(wFile1.exists());
		assertEquals(size1, wFile2.size());
		
		wFile1.release();
		wFile2.release();
	}
	
	/**
	 * Copies file2 -> file1 [full]
	 * Copies file2 -> file3 [partial]
	 */
	@Test
	@Order(9)
	void copyTest()
	{
		// doesn't exist at this moment
		final AWritableFile wFile1 = this.fileSystem.ensureFilePath(this.filePath1).useWriting();
		// exists at this moment
		final AWritableFile wFile2 = this.fileSystem.ensureFilePath(this.filePath2).useWriting();
		// doesn't exist at this moment
		final AWritableFile wFile3 = this.fileSystem.ensureFilePath(this.filePath3).useWriting();
		
		final long size2 = wFile2.size();
		
		assertEquals(wFile2.copyTo(wFile1), size2);
		assertTrue(wFile1.exists());
		assertTrue(wFile2.exists());

		// the return value of the AFS call is not asserted here
//		assertTrue(wFile3.parent().ensureExists());
		wFile3.parent().ensureExists();

		final int slice = this.data.length / 3;
		assertEquals(slice, wFile2.copyTo(wFile3, slice, slice));
		assertEquals(slice, wFile3.size());
		
		wFile1.release();
		wFile2.release();
		wFile3.release();
	}
	
	@Test
	@Order(10)
	void truncateTest()
	{
		final AWritableFile wFile2  = this.fileSystem.ensureFilePath(this.filePath2).useWriting();
		final int           newSize = this.data.length / 2;
		wFile2.truncate(newSize);
		
		assertEquals(newSize, wFile2.size());
		
		wFile2.release();
	}
	
	@Test
	@Order(11)
	void deleteTest()
	{
		final AWritableFile wFile1 = this.fileSystem.ensureFilePath(this.filePath1).useWriting();
		final AWritableFile wFile2 = this.fileSystem.ensureFilePath(this.filePath2).useWriting();
		final AWritableFile wFile3 = this.fileSystem.ensureFilePath(this.filePath3).useWriting();
		
		for(final AWritableFile wFile : Arrays.asList(wFile1, wFile2, wFile3))
		{
			assertTrue(wFile.delete());
			assertFalse(wFile.exists());
			wFile.release();
		}
	}

	/**
	 * Verifies that an {@link EmbeddedStorageManager} backed by this AFS file system supports a
	 * shutdown/start cycle on the <em>same</em> instance: the storage must release and re-attach its
	 * backing files (and any connector/lease) on restart, stay operational, and lose no data.
	 *
	 * <p>Uses a directory unique to each run so it is isolated from the file-operation tests above
	 * and repeatable without leftover state causing a type clash on load. Runs last (high order) so
	 * it never interferes with the file-operation tests sharing {@link #fileSystem}. The path reuses
	 * the same valid prefix as the other tests (e.g. a blob-store bucket whose name has restricted
	 * characters) and only appends a bucket-safe, lowercase unique leaf.
	 */
	@Test
	@Order(100)
	void restartSameInstanceTest()
	{
		// reuse only the valid root segment (e.g. the blob-store bucket) plus a short, lowercase,
		// unique leaf — keeps blob-store bucket names valid and SQL table names within length limits
		final ADirectory dir = this.fileSystem.ensureDirectoryPath(
			this.dirPath1[0], "restart" + System.currentTimeMillis()
		);

		final List<String> root = new ArrayList<>();
		final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);

		root.add("alpha");
		storage.store(root);
		storage.shutdown();

		// same-instance restart against this AFS backend
		storage.start();
		final List<String> afterFirstRestart = storage.root();
		assertEquals(1, afterFirstRestart.size());
		assertEquals("alpha", afterFirstRestart.get(0));

		// the storage must stay operational after restart: write new data, then restart again
		afterFirstRestart.add("beta");
		storage.store(afterFirstRestart);
		storage.shutdown();

		storage.start();
		final List<String> afterSecondRestart = storage.root();
		assertEquals(2, afterSecondRestart.size());
		assertEquals("alpha", afterSecondRestart.get(0));
		assertEquals("beta", afterSecondRestart.get(1));
		storage.shutdown();
	}

}
