/**
 * Copyright (c) 2010-2011 Ed Merks and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Ed Merks - Initial API and implementation
 */
package org.eclipse.emf.ecore.resource;

import java.util.Map;

import org.eclipse.emf.common.util.Callback;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.URIHandlerImpl;

/**
 * TODO
 */
public class URIServiceCallback extends URIHandlerImpl
{
	public URIServiceCallback(URIServiceAsync uriService)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void createInputStream(final URI uri, Map<?, ?> options, final Callback<Map<?, ?>> callback)
	{
	   throw new UnsupportedOperationException();
	}

	@Override
	public void createJSON(final URI uri, Map<?, ?> options, final Callback<Map<?, ?>> callback)
	{
	   throw new UnsupportedOperationException();
	}

	@Override
	public void store(URI uri, String json, Map<?, ?> options, final Callback<Map<?, ?>> callback)
	{
	   throw new UnsupportedOperationException();
	}

	@Override
	public void delete(URI uri, Map<?, ?> options, final Callback<Map<?, ?>> callback)
	{
	   throw new UnsupportedOperationException();
	}

	@Override
	public void exists(URI uri, Map<?, ?> options, final Callback<Boolean> callback)
	{
	   throw new UnsupportedOperationException();
	}
}