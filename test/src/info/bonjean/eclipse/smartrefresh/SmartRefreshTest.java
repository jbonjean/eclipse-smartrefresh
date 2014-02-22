/*******************************************************************************
 * Copyright (c) 2014 Julien Bonjean <julien@bonjean.info>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package info.bonjean.eclipse.smartrefresh;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.prefs.Preferences;

public class SmartRefreshTest {
	private static int WAIT = 1000;
	private IProject project;
	private File rootFolder;

	@Before
	public void setUp() throws Exception {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

		// create a project for our test
		project = root.getProject("smartrefresh-test-project");
		try {
			project.create(null);
			project.open(null);
		} catch (CoreException e) {
			e.printStackTrace();
		}

		// enable the native hooks in Eclipse preferences
		Preferences preferences = InstanceScope.INSTANCE
				.getNode("org.eclipse.core.resources");
		preferences.putBoolean("refresh.enabled", true);

		rootFolder = project.getLocation().toFile();
	}

	private boolean isFileSynchronized(String fileName) {
		return project.getFile(fileName).isSynchronized(
				IResource.DEPTH_INFINITE);
	}

	private boolean isFolderSynchronized(String fileName) {
		return project.getFolder(fileName).isSynchronized(
				IResource.DEPTH_INFINITE);
	}

	@After
	public void tearDown() throws Exception {
		project.delete(true, true, null);
	}

	@Test
	public void createModifyDeleteFile() throws InterruptedException,
			IOException {
		String fileName = "test";
		File file = new File(rootFolder, fileName);

		// file creation
		file.createNewFile();
		assertFalse(isFileSynchronized(fileName));
		Thread.sleep(WAIT);
		assertTrue(isFileSynchronized(fileName));

		// file modification
		Files.write(file.toPath(), "test".getBytes());
		assertFalse(isFileSynchronized(fileName));
		Thread.sleep(WAIT);
		assertTrue(isFileSynchronized(fileName));

		// file deletion
		file.delete();
		assertFalse(isFileSynchronized(fileName));
		Thread.sleep(WAIT);
		assertTrue(isFileSynchronized(fileName));
	}

	@Test
	public void createDeleteFolder() throws InterruptedException, IOException {
		String folder1Name = "lvl1";
		String folder2Name = "lvl1/lvl2";
		String fileName = "lvl1/lvl2/test";
		File folder1 = new File(rootFolder, folder1Name);
		File folder2 = new File(rootFolder, folder2Name);
		File file = new File(rootFolder, fileName);

		// folder creation
		folder1.mkdir();
		assertFalse(isFolderSynchronized(folder1Name));
		Thread.sleep(WAIT);
		assertTrue(isFolderSynchronized(folder1Name));

		// second level folder creation
		folder2.mkdir();
		assertFalse(isFolderSynchronized(folder2Name));
		Thread.sleep(WAIT);
		assertTrue(isFolderSynchronized(folder2Name));

		// create a file in the 2nd level folder
		file.createNewFile();
		assertFalse(isFileSynchronized(fileName));
		Thread.sleep(WAIT);
		assertTrue(isFileSynchronized(fileName));

		// folder deletion
		file.delete();
		assertFalse(isFolderSynchronized(folder1Name));
		Thread.sleep(WAIT);
		assertTrue(isFolderSynchronized(folder1Name));
	}

	@Test
	public void overflow() throws InterruptedException, IOException {

		// prepare the folder for the overflow test
		String folderName = "test";
		File file = new File(rootFolder, folderName);
		file.mkdir();
		Thread.sleep(WAIT);
		assertTrue(isFolderSynchronized(folderName));

		// start overflow
		String currentDir = System.getProperty("user.dir");
		Runtime r = Runtime.getRuntime();
		Process p = r.exec(currentDir + "/misc/overflow.sh " + rootFolder
				+ " 10");
		p.waitFor();

		// wait a bit longer
		Thread.sleep(WAIT * 2);

		// check if every file is sync after an overflow
		Files.walkFileTree(rootFolder.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				assertTrue(isFileSynchronized(file.toString()));
				return FileVisitResult.CONTINUE;
			}
		});

		// check is folders are monitored after an overflow
		// this test is passing, but why? the overflow should have broken the
		// monitoring...
		Files.walkFileTree(rootFolder.toPath(), new SimpleFileVisitor<Path>() {
			private int count = 0;

			@Override
			public FileVisitResult preVisitDirectory(Path path,
					BasicFileAttributes attrs) throws IOException {
				count++;

				if (count % 50 != 0)
					return FileVisitResult.CONTINUE;

				// create a file and check if it is synchronized
				System.out.println("checking " + path);
				String fileName = "test";
				File file = new File(rootFolder, fileName);
				file.createNewFile();
				try {
					Thread.sleep(WAIT);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				assertTrue(isFolderSynchronized(file.toString()));

				return FileVisitResult.CONTINUE;
			}
		});
	}
}
