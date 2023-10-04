package org.eclipse.store.storage.restservice.sparkjava.types;

/*-
 * #%L
 * EclipseStore Storage REST Service Sparkjava
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

import java.util.Hashtable;

import spark.RouteImpl;
import spark.Service;
import spark.route.HttpMethod;

public class RouteManager
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	protected final Service sparkService;

	/*
	 * Holds the handler for a httpMethod and path
	 * Hashtable<RouteURI, <Hashtable<HttpMethod, HandlerClassName>>
	 */
	private final Hashtable<String, Hashtable<String, String>> registeredRoutes;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public RouteManager(final Service sparkService)
	{
		super();
		this.sparkService = sparkService;
		this.registeredRoutes = new Hashtable<>();
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public Hashtable<String, Hashtable<String, String>> getRegisteredRoutes()
	{
		return this.registeredRoutes;
	}

	public void registerRoute(final HttpMethod httpMethod, final String uri, final RouteBase<?> route)
	{
		Hashtable<String, String> methods = this.registeredRoutes.get(uri);
		if(methods == null)
		{
			methods = new Hashtable<>();
			this.registeredRoutes.put(uri, methods);
		}
		methods.put(httpMethod.toString().toLowerCase(), route.getClass().getName());
		this.sparkService.addRoute(httpMethod, RouteImpl.create(uri, route));
	}

}
