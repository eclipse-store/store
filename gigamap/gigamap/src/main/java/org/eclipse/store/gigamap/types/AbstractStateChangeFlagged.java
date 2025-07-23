package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.persistence.types.Storer;


/**
 * An abstract class representing objects with state change tracking capabilities.
 * This class is intended to track changes in the instance itself, its children,
 * or its nested child objects. It provides utility methods to manage and query
 * the state of the object concerning whether it is newly created, changed, or if
 * its children objects indicate any form of state change.
 *
 * Instances of this class or its subclasses can distinguish between:
 * - Newly created objects.
 * - Objects with changes in their attributes or properties.
 * - Objects whose nested child objects have undergone changes.
 *
 * The class ensures that state information about instances and their children is
 * appropriately initialized, updated, and reset as needed.
 *
 * Subclasses must implement functionality to handle children state changes.
 */
public abstract class AbstractStateChangeFlagged
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private transient boolean isNew               ; // meaning newly created instances in contrast to instances created via loading.
	private transient boolean instanceStateChanged; // state change of the instance itself (this)
	private transient boolean childrenStateChanged; // state change of any child or any childrens-child

	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected AbstractStateChangeFlagged(final boolean isNew)
	{
		super();
		
		this.initializeState(isNew);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	final void initializeState(final boolean isNew)
	{
		// needed to distinguish "new and changed" instances from "only changed" ones.
		this.isNew = isNew;
		
		// a newly created instance must ALWAYS be flagged for storing to store it at least once initially.
		this.instanceStateChanged = isNew;
		
		// both newly created AND loaded instances NEVER have children state changed right away.
		this.childrenStateChanged = false;
	}
	
	final boolean isNew()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		return this.isNew;
	}
	
	final boolean isChangedAndNotNew()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		return !this.isNew && (this.instanceStateChanged || this.childrenStateChanged);
	}
	
	final boolean isInstanceNewOrChanged()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		return this.isNew || this.instanceStateChanged;
	}
	
	final boolean hasAnyChange()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		return this.isNew || this.instanceStateChanged || this.childrenStateChanged;
	}
	
	// whether the instance itself changed, NOT counting changed instances exclusively referenced by this instance.
	final boolean stateChangedInstance()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		return this.instanceStateChanged;
	}
	
	final boolean stateChangedChildren()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		return this.childrenStateChanged;
	}
	
	protected final void markStateChangeInstance()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		this.instanceStateChanged = true;
	}
	
	protected final void markStateChangeChildren()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		this.childrenStateChanged = true;
	}
	
	void clearStateChangeFlags()
	{
		// it's not worth the hazzle to check a boolean flag whether to clear it. Just clear it anyway.
		this.instanceStateChanged = this.childrenStateChanged = this.isNew = false;
	}
	
	public void clearStateChangeMarkers()
	{
		/*
		 * All 3 flags can mean that children needed to be stored and thus need to be cleared:
		 * - instance is new: might have children that are also new.
		 * - instance is changed: might have gotten a child added.
		 * - children changed: obvious.
		 */
		if(this.hasAnyChange())
		{
			this.clearChildrenStateChangeMarkers();
		}
		this.clearStateChangeFlags();
	}
	
	protected void storeChildren(final Storer storer)
	{
		// also applies to new instances, but all children that are new (and thus already handled) get filtered out inside.
		if(this.stateChangedChildren())
		{
			this.storeChangedChildren(storer);
		}
	}
	
	protected void storeChangedChild(final Storer storer, final AbstractStateChangeFlagged child)
	{
		if(!child.isChangedAndNotNew())
		{
			return;
		}
		
		// store child only if it has (any!) changes but is not new (as new instances get stored by storing the parent instance)
		storer.store(child);
	}

	protected abstract void storeChangedChildren(Storer storer);

	protected abstract void clearChildrenStateChangeMarkers();
}
