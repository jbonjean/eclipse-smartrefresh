/*******************************************************************************
 * Copyright (c) 2014 Julien Bonjean <julien@bonjean.info>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package info.bonjean.eclipse.smartrefresh;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.refresh.IRefreshMonitor;
import org.eclipse.core.resources.refresh.IRefreshResult;

public class SmartRefreshMonitor implements IRefreshMonitor {
	private static SmartRefreshLogger log = SmartRefreshLogger.getLogger();

	private WatchService watchService;
	private IRefreshResult refreshResult;
	private IProject project;
	private FileSystem filesystem;

	public SmartRefreshMonitor(IResource resource, IRefreshResult result) {

		if (resource.getLocation() == null) {
			log.error("can only watch local filesystems");
			return;
		}

		refreshResult = result;
		project = resource.getProject();

		Path path = resource.getLocation().toFile().toPath();
		filesystem = path.getFileSystem();

		try {
			// create the watch service
			watchService = filesystem.newWatchService();

		} catch (IOException e) {
			log.error("cannot create watchservice");
			e.printStackTrace();
		}

		// register the project root
		registerFolder(path);

		// processing is done in a dedicated thread
		monitoringThread.start();
	}

	/**
	 * Return relative path to the project root
	 */
	private Path getRelativePath(Path absolutePath) {
		return project.getLocation().toFile().toPath().relativize(absolutePath);
	}

	/**
	 * Register folder and sub-folders to the watch service
	 */
	private void registerFolder(Path folder) {
		try {
			Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path path,
						BasicFileAttributes attrs) {

					try {
						log.debug("watching " + path);
						path.register(watchService,
								StandardWatchEventKinds.ENTRY_CREATE,
								StandardWatchEventKinds.ENTRY_DELETE,
								StandardWatchEventKinds.ENTRY_MODIFY);
					} catch (IOException e) {
						log.error("cannot watch this folder");
						e.printStackTrace();
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			log.error("cannot access folder " + folder.toString());
			e.printStackTrace();
		}
	}

	@Override
	public void unmonitor(IResource resource) {
		if (watchService != null) {
			try {
				watchService.close();
			} catch (IOException e) {
				log.error("error while closing the watch service");
				e.printStackTrace();
				return;
			}
		}
		try {
			// the interrupt has already been send, wait for the thread to end
			monitoringThread.join(30000);
		} catch (InterruptedException e) {
			log.error("monitoring thread did not end");
			e.printStackTrace();
			return;
		}
		log.debug(project.getName() + " is no longer monitored");
	}

	/**
	 * Update the Eclipse resource corresponding to a watched path
	 */
	private void updateResource(Path path, Path watchablePath, Kind<?> eventKind) {
		Path absolutePath = watchablePath.resolve(path);
		Path relativePath = getRelativePath(absolutePath);

		log.debug(eventKind + " on " + relativePath);

		IResource resource = null;

		// check if the resource is already there
		log.debug("trying to find the resource in the project");
		resource = project.findMember(relativePath.toString());

		if (resource == null
				&& eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
			// we have nothing to do
			log.debug("resource deleted and not in the project");
			return;
		}

		if (resource == null) {
			// the resource is not already in the project, prepare it manually
			if (Files.isDirectory(absolutePath)) {
				resource = project.getFolder(relativePath.toString());
				if (eventKind == StandardWatchEventKinds.ENTRY_CREATE)
					registerFolder(absolutePath);
			} else
				resource = project.getFile(relativePath.toString());
		}

		// if the resource is not already synchronized, trigger the refresh
		if (!resource.isSynchronized(IResource.DEPTH_INFINITE)) {
			log.debug("resource not synchronized, refreshing");
			refreshResult.refresh(resource);
		} else {
			log.debug("resource is already synchronized");
		}
	}

	/**
	 * Use a thread for background processing, there is no point using Jobs with
	 * an infinite loop.
	 */
	private Thread monitoringThread = new Thread() {
		public void run() {
			log.info(project.getName() + ": monitoring thread started");

			WatchKey watckKey = null;

			// we have no choice but to use an infinite loop here.
			// using the regular scheduling to run the job is not fast enough
			// and OVERFLOWs happen easily because events are not consumed.
			while (true) {
				try {
					// wait for events
					watckKey = watchService.take();
				} catch (ClosedWatchServiceException | InterruptedException e) {
					log.info(project.getName() + ": monitoring thread stopped");
					return;
				}

				log.debug("processing changes");

				List<WatchEvent<?>> events = watckKey.pollEvents();
				for (WatchEvent<?> event : events) {

					if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
						log.error("overflow detected on "
								+ watckKey.watchable());
						// TODO: maybe schedule a job to check this directory
						// later (refresh and register sub-folders)
						// but unexpectedly it seems to be working...
						continue;
					}

					// path of the watched folder
					Path watchablePath = ((Path) watckKey.watchable());

					// path of the changed resource
					Path path = (Path) event.context();

					if (path == null) {
						log.error("no path received from event");
						continue;
					}

					updateResource(path, watchablePath, event.kind());
				}

				// we are done, we can reset the key
				watckKey.reset();
			}
		}
	};
}
