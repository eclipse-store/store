package org.eclipse.store.demo.vinoteca.ui;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.html.Div;

/**
 * Lightweight Vaadin wrapper around the <a href="https://leafletjs.com">Leaflet</a> JavaScript map.
 * <p>
 * The component lazily injects Leaflet's CSS and JS from a CDN on the first attach, then renders
 * the map and a configurable set of {@link Marker markers} into a {@link Div} container. Markers
 * and free-space clicks are forwarded back to the server through a {@link ClientCallable}, which
 * dispatches them to all registered {@link #addMapClickListener listeners}.
 * <p>
 * Used by the {@link org.eclipse.store.demo.vinoteca.ui.view.WineryExplorerView Winery Explorer}
 * view to plot winery locations and let users pick coordinates by clicking the map.
 */
public class LeafletMap extends Div
{
	/**
	 * A single map marker.
	 *
	 * @param lat   latitude in decimal degrees
	 * @param lon   longitude in decimal degrees
	 * @param popup HTML popup content shown when the marker is clicked (may be {@code null})
	 */
	public record Marker(double lat, double lon, String popup) {}

	private List<Marker> pendingMarkers;
	private final List<BiConsumer<Double, Double>> clickListeners = new ArrayList<>();

	/**
	 * Creates an empty map container with sensible default styling. The actual Leaflet map is
	 * rendered when the component is attached to the UI and {@link #setMarkers(List)} is called.
	 */
	public LeafletMap()
	{
		setId("leaflet-map-" + System.identityHashCode(this));
		getStyle()
			.set("width", "100%")
			.set("height", "100%")
			.set("min-height", "300px")
			.set("border-radius", "var(--lumo-border-radius-m)")
			.set("border", "1px solid var(--lumo-contrast-20pct)")
			.set("z-index", "0")
			.set("position", "relative");
	}

	@Override
	protected void onAttach(final AttachEvent attachEvent)
	{
		super.onAttach(attachEvent);

		final String mapId = getId().orElseThrow();

		getElement().executeJs(
			"if (!document.getElementById('leaflet-css')) {" +
			"  let link = document.createElement('link');" +
			"  link.id = 'leaflet-css';" +
			"  link.rel = 'stylesheet';" +
			"  link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';" +
			"  document.head.appendChild(link);" +
			"}" +
			"if (!window.L) {" +
			"  let script = document.createElement('script');" +
			"  script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';" +
			"  script.onload = () => { this.__leafletReady = true; this.dispatchEvent(new Event('leaflet-ready')); };" +
			"  document.head.appendChild(script);" +
			"} else { this.__leafletReady = true; }"
		);

		if (this.pendingMarkers != null)
		{
			setMarkers(this.pendingMarkers);
		}
	}

	/**
	 * Replaces all markers on the map. If the component is not attached yet, the markers are
	 * stashed and applied on the next {@code onAttach}.
	 *
	 * @param markers the new marker set
	 */
	public void setMarkers(final List<Marker> markers)
	{
		if (!isAttached())
		{
			this.pendingMarkers = markers;
			return;
		}

		final StringBuilder js = new StringBuilder();
		js.append("let el = this;");
		js.append("function init() {");
		js.append("  if (el.__map) { el.__map.remove(); }");
		js.append("  let map = L.map(el).setView([30, 0], 2);");
		js.append("  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {");
		js.append("    attribution: '© OpenStreetMap contributors',");
		js.append("    maxZoom: 18");
		js.append("  }).addTo(map);");
		js.append("  let bounds = [];");
		js.append("  el.__markers = {};");

		for (final Marker m : markers)
		{
			js.append("  (function() {");
			js.append("    let ll = [").append(m.lat()).append(",").append(m.lon()).append("];");
			js.append("    let marker = L.marker(ll).addTo(map)");
			if (m.popup() != null)
			{
				js.append("    .bindPopup(").append(escapeJs(m.popup())).append(")");
			}
			js.append("    ;");
			js.append("    marker.on('click', function() { el.$server.onMapClick(ll[0], ll[1]); });");
			js.append("    el.__markers[ll[0] + ',' + ll[1]] = marker;");
			js.append("    bounds.push(ll);");
			js.append("  })();");
		}

		js.append("  if (bounds.length > 0) { map.fitBounds(bounds, {padding: [30, 30]}); }");
		js.append("  map.on('click', function(e) { el.$server.onMapClick(e.latlng.lat, e.latlng.lng); });");
		js.append("  el.__map = map;");
		js.append("  setTimeout(() => map.invalidateSize(), 200);");
		js.append("}");
		js.append("if (el.__leafletReady) { init(); }");
		js.append("else { el.addEventListener('leaflet-ready', init, {once: true}); }");

		getElement().executeJs(js.toString());
	}

	/**
	 * Pans the map to the given coordinates, zooms in and opens that marker's popup. The marker
	 * must have been previously added via {@link #setMarkers(List)} — if no marker exists at the
	 * coordinates, this method is a no-op.
	 *
	 * @param lat the marker latitude
	 * @param lon the marker longitude
	 */
	public void selectMarker(final double lat, final double lon)
	{
		getElement().executeJs(
			"let el = this;" +
			"if (el.__map && el.__markers) {" +
			"  let key = $0 + ',' + $1;" +
			"  let marker = el.__markers[key];" +
			"  if (marker) { el.__map.setView([" + lat + "," + lon + "], 8); marker.openPopup(); }" +
			"}",
			lat, lon
		);
	}

	/**
	 * Registers a listener that will be invoked whenever the user clicks anywhere on the map
	 * (either on free space or on a marker).
	 *
	 * @param listener a {@code (latitude, longitude)} consumer
	 */
	public void addMapClickListener(final BiConsumer<Double, Double> listener)
	{
		this.clickListeners.add(listener);
	}

	@ClientCallable
	private void onMapClick(final double lat, final double lon)
	{
		this.clickListeners.forEach(l -> l.accept(lat, lon));
	}

	private static String escapeJs(final String value)
	{
		return "'" + value
			.replace("\\", "\\\\")
			.replace("'", "\\'")
			.replace("\n", "\\n")
			.replace("\r", "") + "'";
	}
}
