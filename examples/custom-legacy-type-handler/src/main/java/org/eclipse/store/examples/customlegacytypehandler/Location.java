
package org.eclipse.store.examples.customlegacytypehandler;

/*-
 * #%L
 * EclipseStore Example Legacy Type Handler
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

public class Location
{
	String directions;
	double latitude;
	double longitude;
	
	public Location(final String directions, final double latitude, final double longitude)
	{
		super();
		this.directions = directions;
		this.latitude   = latitude;
		this.longitude  = longitude;
	}
	
	@Override
	public String toString()
	{
		return "Latitude: " + this.latitude + "\nLogitude: " + this.longitude + "\ndirections: " + this.directions;
	}
}
