package test.eclipse.store.restservice.javalin;

/*-
 * #%L
 * EclipseStore Integration Tests
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
import java.util.Optional;
import java.util.OptionalLong;

public class UrlBuilder
{
	static final String urlBase				= "http://localhost:4567/store-data/object/";
	static final String urlFixedOffset   	= "fixedOffset=";
	static final String urlFixedLength   	= "fixedLength=";
	static final String urlVariableOffset   = "variableOffset=";
	static final String urlVariableLength   = "variableLength=";
	static final String urlDataLength		= "valueLength=";
	static final String urlFormat			= "format=";
	static final String urlReferences		= "references=";
	static final String urlReferenceOffset	= "referenceOffset=";
	static final String urlReferenceLength	= "referenceLength=";

	OptionalLong oid				= OptionalLong.empty();

	OptionalLong fixedOffset		= OptionalLong.empty();
	OptionalLong fixedLength		= OptionalLong.empty();

	OptionalLong variableOffset		= OptionalLong.empty();
	OptionalLong variableLength		= OptionalLong.empty();

	OptionalLong dataLength			= OptionalLong.empty();
	Optional<String> format			= Optional.empty();
	Optional<Boolean> references	= Optional.empty();
	OptionalLong referenceOffset	= OptionalLong.empty();
	OptionalLong referenceLength	= OptionalLong.empty();
	String url = UrlBuilder.urlBase;

	private int appliedParameters = 0;

	public UrlBuilder(final long oid)
	{
		this.setOid(oid);
	}

	public String build()
	{
		this.url = urlBase;
		this.appliedParameters = 0;

		this.oid.ifPresent( value -> this.url = this.url.concat(Long.toString(value)));
		this.format.ifPresent( value -> this.appendStringParamter(urlFormat + value));

		this.fixedOffset.ifPresent( value -> this.appendStringParamter(urlFixedOffset + Long.toString(value)));
		this.fixedLength.ifPresent( value -> this.appendStringParamter(urlFixedLength + Long.toString(value)));

		this.variableOffset.ifPresent( value -> this.appendStringParamter(urlVariableOffset + Long.toString(value)));
		this.variableLength.ifPresent( value -> this.appendStringParamter(urlVariableLength + Long.toString(value)));

		this.dataLength.ifPresent( value -> this.appendStringParamter(urlDataLength + Long.toString(value)));

		this.references.ifPresent( value -> this.appendStringParamter(urlReferences + value.toString()));

		return this.url;
	}

	public void appendStringParamter(final String paramter)
	{
		if(this.appliedParameters > 0)
		{
			this.url = this.url.concat("&");
		}
		else
		{
			this.url = this.url.concat("?");
		}

		this.url = this.url.concat(paramter);
		this.appliedParameters++;
	}

	public UrlBuilder setOid(final long oid) {
		this.oid = OptionalLong.of(oid);
		return this;
	}

	public UrlBuilder setDataLength(final long dataLength) {
		this.dataLength = OptionalLong.of(dataLength);
		return this;
	}

	public UrlBuilder setFormat(final String format) {
		this.format = Optional.of(format);
		return this;
	}

	public UrlBuilder setFixedOffset(final long fixedOffset) {
		this.fixedOffset = OptionalLong.of(fixedOffset);
		return this;
	}

	public UrlBuilder setFixedLength(final long fixedLength) {
		this.fixedLength = OptionalLong.of(fixedLength);
		return this;
	}

	public UrlBuilder setVariableOffset(final long variableOffset) {
		this.variableOffset = OptionalLong.of(variableOffset);
		return this;
	}

	public UrlBuilder setVariableLength(final long variableLength) {
		this.variableLength = OptionalLong.of(variableLength);
		return this;
	}

	public UrlBuilder setReferences(final boolean references) {
		this.references = Optional.of(references);
		return this;
	}

	public UrlBuilder setUrl(final String url) {
		this.url = url;
		return this;
	}



}
