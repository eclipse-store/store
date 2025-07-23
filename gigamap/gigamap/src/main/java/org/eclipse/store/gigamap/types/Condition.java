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

import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.util.cql.CQL;

import java.util.Comparator;
import java.util.function.Predicate;

import static org.eclipse.serializer.util.X.notNull;

/**
 * A class representing a condition that can evaluate and combine logical expressions.
 * The `Condition` class provides methods to create, combine, and evaluate logical conditions
 * using "AND" or "OR" linkers. It also supports completion of a condition logic.
 *
 * @param <E> the type of elements handled by this condition.
 */
public interface Condition<E> extends Predicate<E>
{
	/**
	 * Evaluates the condition against the given parent and produces the resulting bitmap.
	 *
	 * @param parent the internal bitmap indices structure to evaluate the condition against
	 * @return the resulting bitmap of the evaluation
	 */
	public <S extends E> BitmapResult evaluate(BitmapIndices.Internal<S> parent);
	
	/**
	 * Marks the condition as complete and returns itself.
	 *
	 * @return the current condition instance
	 */
	public default Condition<E> complete()
	{
		return this;
	}
	
	/**
	 * Links the current condition with another condition using the specified linker.
	 *
	 * @param condition the condition to be linked with the current condition
	 * @param linker the linker function that defines how the conditions are linked
	 * @return the resulting condition after linking with the specified condition and linker
	 */
	public default Condition<E> linkCondition(
		final Condition<E>     condition,
		final Linker linker
	)
	{
		return linker.linkCondition(condition, this);
	}
	
	/**
	 * Combines the current condition with another condition using a logical AND operation.
	 *
	 * @param condition the condition to be combined with the current condition
	 * @return a new condition that represents the logical AND of the current condition and the specified condition
	 */
	public Condition<E> and(Condition<E> condition);
	
	/**
	 * Combines the current condition with another condition using a logical OR operation.
	 *
	 * @param condition the condition to be combined with the current condition
	 * @return a new condition that represents the logical OR of the current condition and the specified condition
	 */
	public Condition<E> or(Condition<E> condition);
	
	
	
	static final Linker CREATOR_INITIAL = new Linker()
	{
		@Override
		public <E> Condition<E> linkCondition(final Condition<E> primary, final Condition<E> other)
		{
			if(other != null)
			{
				throw new IllegalStateException("Initial condition is already present.");
			}
			
			// no wrapping condition instance, just passing through the primary
			return primary;
		}
		
	};
	
	static final Linker CREATOR_AND = new Linker()
	{
		@Override
		public <E> Condition<E> linkCondition(final Condition<E> primary, final Condition<E> other)
		{
			return new And<>(other, primary);
		}
		
	};
	
	static final Linker CREATOR_OR = new Linker()
	{
		@Override
		public <E> Condition<E> linkCondition(final Condition<E> primary, final Condition<E> other)
		{
			return new Or<>(other, primary);
		}
		
	};
		
	
	
	public abstract class Abstract<E> implements Condition<E>
	{
		@Override
		public Condition<E> and(final Condition<E> condition)
		{
			// already created condition instance can be added right away with AND logic
			return this.linkCondition(condition.complete(), Condition.CREATOR_AND);
		}
		
		@Override
		public Condition<E> or(final Condition<E> condition)
		{
			// already created condition instance can be added right away with OR logic
			return this.linkCondition(condition.complete(), Condition.CREATOR_OR);
		}
	}
	
	// conditions that link (nest, wrap, collect) other conditions (Term, NOT, AND, OR)
	public abstract class AbstractLinking<E> extends Abstract<E>
	{
		protected AbstractLinking()
		{
			super();
		}
	}
	
	public abstract class AbstractLinkingSingle<E> extends AbstractLinking<E>
	{
		final Condition<E> condition;
					
		protected AbstractLinkingSingle(final Condition<E> condition)
		{
			super();
			this.condition = notNull(condition);
		}
		
		@Override
		public <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			return this.condition.evaluate(parent);
		}
	}
	
	/*
	 * Required for defining "parenthesis"
	 * (actually just encapsulating a chain condition to prevent prioritized linking).
	 * Consider:
	 * 
	 * A.or(B).or(C).and(D)
	 * would be seen by the user as "A OR B OR C AND D"
	 * And that would be assumed to be structured like "(A OR B OR (C AND D))"
	 * because AND binds stronger than OR.
	 * But internally, the structure is actually
	 * ChainOr(A, B, C).and(D).
	 * So the already present C has to be "assimilated" and replaced by the incoming AND:
	 * ChainOr(A, B, ChainAnd(C, D))
	 * Only this created the correct assumed structure of
	 * "(A OR B OR (C AND D))"
	 * 
	 * But what if one wants to actually express "((A OR B OR C) AND D)"?
	 * Then an explicit encapsulation as a term ("parenthesis") is needed:
	 * term(A.or(B).or(C)).and(D)
	 * 
	 * Only then will the last ChainOr element NOT be wrapped and replaced,
	 * but the whole ChainOr will be treated as an inseparable condition:
	 * ChainAnd(ChainOr(A, B, C), D)
	 * 
	 * Yes, stuff is complicated.
	 */
	public final class Term<E> extends AbstractLinkingSingle<E>
	{
		protected Term(final Condition<E> condition)
		{
			super(condition);
		}
		
		@Override
		public <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			// there are actually no parenthesis at all. It's just that the term wraps the given condition. Uh, lol.
			return this.condition.evaluate(parent);
		}
		
		@Override
		public boolean test(final E entity)
		{
			return this.condition.test(entity);
		}
	}
	
	public final class Not<E> extends AbstractLinkingSingle<E>
	{
		protected Not(final Condition<E> condition)
		{
			super(condition);
		}
		
		@Override
		public <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			return new BitmapResult.Not(this.condition.evaluate(parent));
		}
		
		@Override
		public boolean test(final E entity)
		{
			return !this.condition.test(entity);
		}
	}
	
	public abstract class AbstractLinkingChain<E> extends AbstractLinking<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final BulkList<Condition<E>> conditions;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
				
		protected AbstractLinkingChain()
		{
			this(BulkList.New());
		}
		
		protected AbstractLinkingChain(final BulkList<Condition<E>> conditions)
		{
			super();
			this.conditions = conditions;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		protected <S extends E> BitmapResult[] evaluateConditions(
			final BitmapIndices.Internal<S> parent,
			final Comparator<BitmapResult>  order
		)
		{
			return CQL
				.from(this.conditions)
				.project(c ->
					c.evaluate(parent)
				)
				.orderBy(order)
				.executeInto(new BitmapResult[this.conditions.intSize()])
			;
		}
		
	}
	
	public final class And<E> extends AbstractLinkingChain<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected And(final Condition<E> leftCondition, final Condition<E> rightCondition)
		{
			super();
			this.conditions.add(leftCondition);
			this.conditions.add(rightCondition);
		}
		
		protected And(final BulkList<Condition<E>> conditions)
		{
			super(conditions);
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public Condition<E> linkCondition(
			final Condition<E>     condition,
			final Linker linker
		)
		{
			// AND condition can just be added to this chainAnd's conditions (flattening).
			if(linker == CREATOR_AND)
			{
				this.conditions.add(condition);
				return this;
			}
			
			// otherwise (i.e., OR), the last condition is bound with priority to the new condition
			return linker.linkCondition(condition, this);
		}
		
		@Override
		public final <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			// AND sub results start with the LOWEST segment count to exclude as many segments as possible right away.
			final BitmapResult[] results = this.evaluateConditions(parent, BitmapResult::andOptimize);
			
			return new BitmapResult.ChainAnd(results);
		}
		
		// (22.09.2023 TM)NOTE: not necessary for querying the parent map, but a nice usage on the side.
		@Override
		public boolean test(final E entity)
		{
			final BulkList<Condition<E>> conditions = this.conditions;
			for(final Condition<E> condition : conditions)
			{
				if(!condition.test(entity))
				{
					return false;
				}
			}

			return true;
		}
	}
	
	public final class Or<E> extends AbstractLinkingChain<E>
	{
		protected Or(final Condition<E> leftCondition, final Condition<E> rightCondition)
		{
			super();
			this.conditions.add(leftCondition);
			this.conditions.add(rightCondition);
		}
		
		@Override
		public Condition<E> complete()
		{
			/* This is really tricky!
			 * A query like
			 * .query(Condition1)
			 * .   or(Condition2)
			 * .  and(Condition3)
			 * is naturally interpreted (and thus on a technical level handled) as:
			 * Condition1 OR (Condition2 AND Condition3)
			 * Since AND has priority over OR.
			 * 
			 * Contrary to that, the query
			 * .query(Condition1.or(Condition2))
			 * .  and(Condition3)
			 * is naturally interpreted as:
			 * (Condition1 OR Condition2) AND Condition3)
			 * Since the OR-Condition seems to be enclosed or completed inside the query(...) call.
			 * 
			 * BUT: Actually, it is technically the same thing as the first query:
			 * A query has an OR condition instance and then another condition gets linked with AND.
			 * 
			 * To achieve the "perceived" correct behavior of the OR condition being "complete" and
			 * unchangeable by the next AND, it has to be wrapped in a term condition.
			 * 
			 * Condition.And does not need that bandaid, since it binds so strongly that it is unchangeable, anyway
			 */
			return new Term<>(this);
		}

		@Override
		public Condition<E> linkCondition(
			final Condition<E>     condition,
			final Linker linker
		)
		{
			if(linker == CREATOR_OR)
			{
				// OR condition can just be added to this chainOr's conditions (flattening).
				this.conditions.add(condition);
				return this;
			}
			
			// otherwise (i.e. AND), the last condition is bound with priority to the new condition
			final Condition<E> linkingCondition = linker.linkCondition(condition, this.conditions.last());
			this.conditions.setLast(linkingCondition);
			return this;
		}
		
		@Override
		public final <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			// OR sub results start with the HIGHEST segment count to include as many segments as possible right away.
			final BitmapResult[] results = this.evaluateConditions(parent, BitmapResult::segmentCountDesc);
			
			return new BitmapResult.ChainOr(results);
		}
		

		
		// not necessary for querying the parent map, but a nice usage on the side.
		@Override
		public boolean test(final E entity)
		{
			for(final Condition<? super E> condition : this.conditions)
			{
				if(condition.test(entity))
				{
					return true;
				}
			}

			return false;
		}
	}
	
	
	// conditions that refer to an index (or at least an identifier)
	public abstract class AbstractIndex<E, K> extends Abstract<E>
	{
		final IndexIdentifier<? super E, K> index;
		
		protected AbstractIndex(final IndexIdentifier<? super E, K> index)
		{
			super();
			this.index = index;
		}
		
	}
	
	public final class Equals<E, K> extends AbstractIndex<E, K>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final K key;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Equals(final IndexIdentifier<? super E, K> index, final K key)
		{
			super(index);
			this.key = key;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
						
		@Override
		public <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			// "resolving" is used here to validate whether the passed parent map is actually the index' parent.
			final BitmapIndex.Internal<S, K> index = this.index.resolveFor(parent);
			
			return index.internalQuery(this.key);
		}
		
		// not necessary for querying the parent map, but a nice usage on the side.
		@Override
		public boolean test(final E entity)
		{
			return this.index.test(entity, this.key);
		}
		
	}
	
	public final class In<E, K> extends AbstractIndex<E, K>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final K[] keys;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		In(final IndexIdentifier<? super E, K> index, final K[] keys)
		{
			super(index);
			this.keys = keys;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
						
		@Override
		public <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			// "resolving" is used here to validate whether the passed parent map is actually the index's parent.
			final BitmapIndex.Internal<S, K> index = this.index.resolveFor(parent);
			
			final BitmapResult[] results = new BitmapResult[this.keys.length];
			for(int i = 0; i < results.length; i++)
			{
				results[i] = index.internalQuery(this.keys[i]);
			}
			
			return new BitmapResult.ChainOr(results);
		}
		
		// not necessary for querying the parent map, but a nice usage on the side.
		@Override
		public boolean test(final E entity)
		{
			final K[] keys = this.keys;
			final IndexIdentifier<? super E, K> index = this.index;
			
			for(final K key : keys)
			{
				if(index.test(entity, key))
				{
					return true;
				}
			}
			
			return false;
		}
		
	}
	
	public final class All<E, K> extends AbstractIndex<E, K>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final K[] keys;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		All(final IndexIdentifier<? super E, K> index, final K[] keys)
		{
			super(index);
			this.keys = keys;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
						
		@Override
		public <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			// "resolving" is used here to validate whether the passed parent map is actually the index' parent.
			final BitmapIndex.Internal<S, K> index = this.index.resolveFor(parent);
			
			final BitmapResult[] results = new BitmapResult[this.keys.length];
			for(int i = 0; i < results.length; i++)
			{
				results[i] = index.internalQuery(this.keys[i]);
			}
			
			return new BitmapResult.ChainAnd(results);
		}
		
		// not necessary for querying the parent map, but a nice usage on the side.
		@Override
		public boolean test(final E entity)
		{
			final K[] keys = this.keys;
			final IndexIdentifier<? super E, K> index = this.index;
			
			for(final K key : keys)
			{
				if(!index.test(entity, key))
				{
					return false;
				}
			}
			
			return true;
		}
		
	}
	
	public final class Searched<E, K> extends AbstractIndex<E, K>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final Predicate<? super K> predicate;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Searched(final IndexIdentifier<? super E, K> index, final Predicate<? super K> predicate)
		{
			super(index);
			this.predicate = predicate;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
						
		@Override
		public <S extends E> BitmapResult evaluate(final BitmapIndices.Internal<S> parent)
		{
			final BitmapIndex<S, K> index = this.index.resolveFor(parent);
			return index.search(this.predicate);
		}
		
		// not necessary for querying the parent map, but a nice usage on the side.
		@Override
		public boolean test(final E entity)
		{
			final Indexer<? super E, K> indexer = this.index.indexer();
			final K                  indexedKey = indexer.index(entity);
			return this.predicate.test(indexedKey);
		}
		
	}
	
	
	/**
	 * A functional interface that defines a mechanism to combine or link two conditions.
	 * Implementations of this interface specify how the linking between a primary condition
	 * and another condition should occur.
	 */
	@FunctionalInterface
	public interface Linker
	{
		public <E> Condition<E> linkCondition(Condition<E> primary, Condition<E> other);
	}
	
}
