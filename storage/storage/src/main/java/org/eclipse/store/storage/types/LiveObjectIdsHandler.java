package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.persistence.types.ObjectIdsSelector;

/**
 * Combined entry point the storage GC uses to interact with application-held object ids.
 * The GC plugs into the application's object registry at two different phases and in opposite
 * directions; this interface bundles both:
 *
 * <h3>{@link ObjectIdsSelector} — sweep-time safety net (filter, pull)</h3>
 * <ul>
 *   <li><b>When:</b> during sweep, after marking has completed.</li>
 *   <li><b>Direction:</b> the sweeper hands the registry a candidate and asks "is this one still
 *       alive in the app?" The registry answers via
 *       {@link org.eclipse.serializer.persistence.types.ObjectIdsProcessor#processObjectIdsByFilter
 *       processObjectIdsByFilter} — essentially a {@code boolean isInRegistry(long oid)} predicate.
 *       The sweep iterates entities it already knows about and calls the predicate per id.</li>
 *   <li><b>Effect</b> see StorageEntityCache sweep implementation:
 *       {@code if(item.isGcMarked() || isReachableInApplication.test(item.objectId)) markWhite} —
 *       an unmarked entity survives the sweep solely because the application's object registry
 *       still has a (live-{@link java.lang.ref.WeakReference WeakReference}) entry for its id,
 *       i.e. the app has not yet released the Java instance, or has released it but
 *       {@link org.eclipse.serializer.persistence.types.PersistenceObjectRegistry#cleanUp()
 *       PersistenceObjectRegistry.cleanUp()} has not yet reaped the cleared WeakReference.</li>
 *   <li><b>Scope:</b> <em>shallow</em> — it protects the entity that was asked about, nothing else.
 *       The entity's own binary references are not consulted.</li>
 * </ul>
 *
 * <h3>{@link LiveObjectIdsIterator} — mark-time seed (enumeration, push)</h3>
 * <ul>
 *   <li><b>When:</b> at the end of each sweep, inside
 *       {@link StorageEntityMarkMonitor.Default#completeSweep}, immediately after
 *       {@code determineAndEnqueueRootOid}. The next mark cycle has not started yet; the mark
 *       queues have just been reset.</li>
 *   <li><b>Direction:</b> the registry pushes every live object id it currently holds into the
 *       mark queue via {@code acceptor.acceptObjectId(oid)}. The mark monitor is itself a
 *       {@link org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor}, so every id
 *       lands directly in the right per-channel
 *       {@link StorageObjectIdMarkQueue}.</li>
 *   <li><b>Scope:</b> <em>transitive</em> — every app-held id becomes a mark root, so the next
 *       mark phase walks its binary references (and their references, and so on), marking
 *       everything reachable. Those transitively-reached entities then survive the subsequent
 *       sweep normally (gc-marked, not merely safety-net-kept).</li>
 * </ul>
 *
 * <h3>Why both are needed</h3>
 * The selector alone is not enough: an entity kept alive only via the safety net retains its
 * <em>old</em> binary record. If that record points at another entity whose Java instance got
 * collected (and whose registry entry was cleaned up), nothing keeps the pointed-at entity alive
 * and the sweep deletes it. The next mark phase then walks the safety-net-kept entity's stale
 * pointer, looks up the swept id, gets {@code null}, and produces a zombie OID — persistent
 * corruption on reload. The iterator closes that gap: by re-seeding every live registry id as a
 * mark root, anything reachable from an app-held entity is marked <em>before</em> the sweep
 * decides what to delete, so the stale pointer can no longer dangle.
 *
 * <h3>At a glance</h3>
 * <table>
 *   <caption>Comparison of the two roles</caption>
 *   <tr><th></th><th>{@code ObjectIdsSelector}</th><th>{@code LiveObjectIdsIterator}</th></tr>
 *   <tr><td>phase</td>
 *       <td>sweep</td>
 *       <td>pre-mark (end of previous sweep)</td></tr>
 *   <tr><td>flow</td>
 *       <td>sweep &rarr; registry (ask)</td>
 *       <td>registry &rarr; mark queue (push)</td></tr>
 *   <tr><td>API style</td>
 *       <td>filter predicate</td>
 *       <td>enumeration via acceptor</td></tr>
 *   <tr><td>keeps alive</td>
 *       <td>the asked-about entity</td>
 *       <td>the entity <b>and</b> everything it transitively references</td></tr>
 *   <tr><td>purpose</td>
 *       <td>don't delete what the app still holds</td>
 *       <td>make sure what the app holds is traversed</td></tr>
 * </table>
 */
public interface LiveObjectIdsHandler extends ObjectIdsSelector, LiveObjectIdsIterator
{
	// combined marker interface
}
