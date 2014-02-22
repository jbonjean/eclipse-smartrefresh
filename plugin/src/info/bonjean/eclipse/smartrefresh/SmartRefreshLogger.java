/*******************************************************************************
 * Copyright (c) 2014 Julien Bonjean <julien@bonjean.info>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package info.bonjean.eclipse.smartrefresh;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

public class SmartRefreshLogger {
	private static SmartRefreshLogger instance;
	private static String PLUGIN_NAME = Activator.getContext().getBundle()
			.getSymbolicName();
	private static boolean DEBUG = System.getProperty("DEBUG") != null;
	private ILog platformLogger;

	private SmartRefreshLogger() {
		platformLogger = Platform.getLog(Platform
				.getBundle(Platform.PI_RUNTIME));
	}

	public static SmartRefreshLogger getLogger() {
		if (instance == null)
			instance = new SmartRefreshLogger();
		return instance;
	}

	private void log(int level, String message) {
		platformLogger.log(new Status(level, PLUGIN_NAME, message));
	}

	public void error(String message) {
		log(IStatus.ERROR, message);
		System.err.println(PLUGIN_NAME + ": <error> " + message);
	}

	public void info(String message) {
		log(IStatus.INFO, message);
		System.out.println(PLUGIN_NAME + ": <info> " + message);
	}

	public void debug(String message) {
		if (DEBUG)
			System.out.println(PLUGIN_NAME + ": <debug> " + message);
	}
}
