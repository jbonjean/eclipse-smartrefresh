/*******************************************************************************
 * Copyright (c) 2014 Julien Bonjean <julien@bonjean.info>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package info.bonjean.eclipse.smartrefresh;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.refresh.IRefreshMonitor;
import org.eclipse.core.resources.refresh.IRefreshResult;
import org.eclipse.core.resources.refresh.RefreshProvider;

public class SmartRefreshProvider extends RefreshProvider {
	@Override
	public IRefreshMonitor installMonitor(IResource resource,
			IRefreshResult result) {
		return new SmartRefreshMonitor(resource, result);
	}
}
