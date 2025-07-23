package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/**
 * AnalyzerCreator is an abstract class designed to create instances of
 * analyzers. Subclasses will provide specific implementations to instantiate
 * and return varying types of analyzers.
 * <p>
 * The purpose of the class is to establish a clear structure for creating
 * analyzer instances while allowing flexibility for customization in
 * subclasses.
 * <p>
 * Note: This class is intentionally not a functional interface to avoid
 * potential issues with the use of unpersistable lambda instances.
 */
//may NOT be a functional interface to avoid unpersistable lambda instances getting used.
public abstract class AnalyzerCreator
{
	/**
	 * Creates and returns an instance of an Analyzer. The specific implementation
	 * of the Analyzer is determined by the subclass of AnalyzerCreator.
	 *
	 * @return an instance of Analyzer
	 */
	public abstract Analyzer createAnalyzer();
	
	/**
	 * Provides a new instance of the {@code AnalyzerCreator.Standard} class,
	 * which is a specific implementation of {@code AnalyzerCreator}
	 * which creates a {@link StandardAnalyzer}.
	 *
	 * @return a new instance of {@code AnalyzerCreator.Standard}, which creates
	 *         analyzers based on the standard analyzer implementation.
	 */
	public static AnalyzerCreator Standard()
	{
		return new Standard();
	}
	
	
	public static class Standard extends AnalyzerCreator
	{
		Standard()
		{
			super();
		}
	
		@Override
		public Analyzer createAnalyzer()
		{
			return new StandardAnalyzer();
		}
		
	}
	
}
