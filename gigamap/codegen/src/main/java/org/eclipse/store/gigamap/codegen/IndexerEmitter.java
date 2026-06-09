/*-
 * #%L
 * EclipseStore GigaMap Codegen
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
package org.eclipse.store.gigamap.codegen;

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.IndexKind;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;

/**
 * Compile-time counterpart of {@code IndexerGenerator.AnnotationBased.createIndexer}: maps a member's
 * (type, {@link IndexKind kind}, binary / unique flags) to the matching {@code Indexer} flavor and
 * emits the source of a typed {@code public static final} constant — an anonymous subclass of the
 * corresponding {@code Indexer.Abstract} reading the member through {@code readExpr}.
 * <p>
 * The decision tree here mirrors the runtime generator one-for-one; the {@code flavorsMatchRuntime}
 * test cross-checks the two paths to catch drift. Type references are routed through {@link Imports}
 * so the generated source uses imported simple names rather than fully qualified names. One instance
 * is used per generated file (it captures that file's {@link Imports}).
 */
final class IndexerEmitter
{
	static final String TYPES = "org.eclipse.store.gigamap.types.";

	private final Types    types;
	private final Elements elements;
	private final Imports  imports;

	IndexerEmitter(final Types types, final Elements elements, final Imports imports)
	{
		this.types    = types;
		this.elements = elements;
		this.imports  = imports;
	}

	/**
	 * The generated source of a single indexer constant: its declared interface type (with generics)
	 * and the {@code new ...Abstract<>(){ ... }} initializer (without a trailing semicolon).
	 */
	static final class IndexerCode
	{
		final String declaredType;
		final String initializer;

		IndexerCode(final String declaredType, final String initializer)
		{
			this.declaredType = declaredType;
			this.initializer  = initializer;
		}
	}

	/**
	 * @return the constant source, or {@code null} if the member type is not supported for the
	 *         requested kind (the caller reports the diagnostic).
	 */
	IndexerCode emit(
		final String      entityRef ,
		final String      indexName ,
		final String      readExpr  ,
		final TypeMirror  type      ,
		final Index       annotation,
		final boolean     unique
	)
	{
		final IndexKind kind           = annotation != null ? annotation.kind() : IndexKind.AUTO;
		final boolean   explicitBinary = kind == IndexKind.BINARY || (annotation != null && annotation.binary());
		final boolean   preferBinary   = explicitBinary || (kind == IndexKind.AUTO && unique);

		if(kind == IndexKind.BIT_SLICED)
		{
			return this.bitSliced(entityRef, indexName, readExpr, type);
		}

		if(preferBinary)
		{
			final IndexerCode binary = this.binary(entityRef, indexName, readExpr, type);
			if(binary != null)
			{
				return binary;
			}
			if(explicitBinary)
			{
				return null;
			}
			// unique on a non-binary type: fall through to a standard indexer (used as unique constraint)
		}

		return this.standard(entityRef, indexName, readExpr, type);
	}

	private IndexerCode standard(
		final String     entityRef,
		final String     indexName,
		final String     readExpr ,
		final TypeMirror type
	)
	{
		if(this.isType(type, "java.lang.String"))
		{
			return this.single("IndexerString", entityRef, indexName, "String", "getString", readExpr);
		}
		if(this.isCharacterType(type))
		{
			return this.single("IndexerCharacter", entityRef, indexName, "Character", "getCharacter", readExpr);
		}
		if(this.isIntegerType(type))
		{
			return this.single("IndexerInteger", entityRef, indexName, "Integer", "getInteger", readExpr);
		}
		if(this.isLongType(type))
		{
			return this.single("IndexerLong", entityRef, indexName, "Long", "getLong", readExpr);
		}
		if(this.isFloatType(type))
		{
			return this.single("IndexerFloat", entityRef, indexName, "Float", "getFloat", readExpr);
		}
		if(this.isDoubleType(type))
		{
			return this.single("IndexerDouble", entityRef, indexName, "Double", "getDouble", readExpr);
		}
		if(this.isByteType(type))
		{
			return this.single("IndexerByte", entityRef, indexName, "Byte", "getByte", readExpr);
		}
		if(this.isShortType(type))
		{
			return this.single("IndexerShort", entityRef, indexName, "Short", "getShort", readExpr);
		}
		if(this.isBooleanType(type))
		{
			return this.single("IndexerBoolean", entityRef, indexName, "Boolean", "getBoolean", readExpr);
		}
		if(this.isType(type, "java.time.LocalDate"))
		{
			return this.single("IndexerLocalDate", entityRef, indexName, "java.time.LocalDate", "getLocalDate", readExpr);
		}
		if(this.isType(type, "java.time.LocalTime"))
		{
			return this.single("IndexerLocalTime", entityRef, indexName, "java.time.LocalTime", "getLocalTime", readExpr);
		}
		if(this.isType(type, "java.time.LocalDateTime"))
		{
			return this.single("IndexerLocalDateTime", entityRef, indexName, "java.time.LocalDateTime", "getLocalDateTime", readExpr);
		}
		if(this.isType(type, "java.time.YearMonth"))
		{
			return this.single("IndexerYearMonth", entityRef, indexName, "java.time.YearMonth", "getYearMonth", readExpr);
		}
		if(this.isType(type, "java.time.Instant"))
		{
			return this.single("IndexerInstant", entityRef, indexName, "java.time.Instant", "getInstant", readExpr);
		}
		if(this.isType(type, "java.time.ZonedDateTime"))
		{
			return this.single("IndexerZonedDateTime", entityRef, indexName, "java.time.ZonedDateTime", "getZonedDateTime", readExpr);
		}
		if(this.isType(type, "java.util.UUID"))
		{
			return this.single("BinaryIndexerUUID", entityRef, indexName, "java.util.UUID", "getUUID", readExpr);
		}
		if(this.isEnum(type))
		{
			return this.keyed("Indexer", entityRef, this.refRaw(type), indexName, readExpr);
		}
		if(this.isIterable(type))
		{
			final String element = this.singleTypeArgument(type);
			if(element == null)
			{
				return null;
			}
			return this.multiValue(entityRef, element, indexName, readExpr);
		}
		if(type.getKind() == TypeKind.ARRAY)
		{
			return this.multiValue(entityRef, this.box(((ArrayType)type).getComponentType()), indexName, readExpr);
		}
		if(this.isComparable(type))
		{
			return this.keyed("IndexerComparing", entityRef, this.refRaw(type), indexName, readExpr);
		}
		// custom: equality-only Indexer over the raw member type
		return this.keyed("Indexer", entityRef, this.refRaw(type), indexName, readExpr);
	}

	private IndexerCode binary(
		final String     entityRef,
		final String     indexName,
		final String     readExpr ,
		final TypeMirror type
	)
	{
		if(this.isNaturalNumberType(type))
		{
			final String base     = this.imports.ref(TYPES + "BinaryIndexer");
			final String declared = base + "<" + entityRef + ">";
			final StringBuilder b = new StringBuilder();
			b.append("new ").append(base).append(".Abstract<").append(entityRef).append(">()\n");
			b.append("\t{\n");
			this.appendName(b, indexName);
			b.append("\t\t@Override\n");
			b.append("\t\tpublic long indexBinary(final ").append(entityRef).append(" e)\n");
			b.append("\t\t{\n");
			b.append("\t\t\tfinal Number key = ").append(readExpr).append(";\n");
			b.append("\t\t\tif(key == null)\n");
			b.append("\t\t\t{\n");
			b.append("\t\t\t\tthrow new IllegalArgumentException(\"Null keys are not allowed in index ")
				.append(escape(indexName)).append("\");\n");
			b.append("\t\t\t}\n");
			b.append("\t\t\treturn key.longValue();\n");
			b.append("\t\t}\n");
			b.append("\t}");
			return new IndexerCode(declared, b.toString());
		}
		if(this.isFloatType(type))
		{
			return this.single("BinaryIndexerFloat", entityRef, indexName, "Float", "getFloat", readExpr);
		}
		if(this.isDoubleType(type))
		{
			return this.single("BinaryIndexerDouble", entityRef, indexName, "Double", "getDouble", readExpr);
		}
		if(this.isType(type, "java.lang.String"))
		{
			return this.single("BinaryIndexerString", entityRef, indexName, "String", "getString", readExpr);
		}
		if(this.isType(type, "java.util.UUID"))
		{
			return this.single("BinaryIndexerUUID", entityRef, indexName, "java.util.UUID", "getUUID", readExpr);
		}
		return null;
	}

	private IndexerCode bitSliced(
		final String     entityRef,
		final String     indexName,
		final String     readExpr ,
		final TypeMirror type
	)
	{
		if(this.isIntegerType(type))
		{
			return this.single("ByteIndexerInteger", entityRef, indexName, "Integer", "getInteger", readExpr);
		}
		if(this.isLongType(type))
		{
			return this.single("ByteIndexerLong", entityRef, indexName, "Long", "getLong", readExpr);
		}
		if(this.isByteType(type))
		{
			return this.single("ByteIndexerByte", entityRef, indexName, "Byte", "getByte", readExpr);
		}
		if(this.isShortType(type))
		{
			return this.single("ByteIndexerShort", entityRef, indexName, "Short", "getShort", readExpr);
		}
		if(this.isFloatType(type))
		{
			return this.single("ByteIndexerFloat", entityRef, indexName, "Float", "getFloat", readExpr);
		}
		if(this.isDoubleType(type))
		{
			return this.single("ByteIndexerDouble", entityRef, indexName, "Double", "getDouble", readExpr);
		}
		if(this.isType(type, "java.time.Instant"))
		{
			return this.single("ByteIndexerInstant", entityRef, indexName, "java.time.Instant", "getInstant", readExpr);
		}
		return null;
	}

	/**
	 * Emits the source of a {@code SpatialIndexer} constant reading two numeric coordinate members.
	 */
	IndexerCode spatial(
		final String entityRef,
		final String indexName,
		final String latReadExpr,
		final String lonReadExpr
	)
	{
		final String base     = this.imports.ref(TYPES + "SpatialIndexer");
		final String declared = base + "<" + entityRef + ">";
		final StringBuilder b = new StringBuilder();
		b.append("new ").append(base).append(".Abstract<").append(entityRef).append(">()\n");
		b.append("\t{\n");
		this.appendName(b, indexName);
		this.appendCoordinate(b, entityRef, "getLatitude", "latitude", latReadExpr);
		b.append("\n");
		this.appendCoordinate(b, entityRef, "getLongitude", "longitude", lonReadExpr);
		b.append("\t}");
		return new IndexerCode(declared, b.toString());
	}

	private void appendCoordinate(
		final StringBuilder b,
		final String entityRef,
		final String method,
		final String axis,
		final String readExpr
	)
	{
		b.append("\t\t@Override\n");
		b.append("\t\tprotected Double ").append(method).append("(final ").append(entityRef).append(" e)\n");
		b.append("\t\t{\n");
		b.append("\t\t\tfinal Object value = ").append(readExpr).append(";\n");
		b.append("\t\t\tif(value == null)\n");
		b.append("\t\t\t{\n");
		b.append("\t\t\t\treturn null;\n");
		b.append("\t\t\t}\n");
		b.append("\t\t\tif(value instanceof Number)\n");
		b.append("\t\t\t{\n");
		b.append("\t\t\t\treturn ((Number)value).doubleValue();\n");
		b.append("\t\t\t}\n");
		b.append("\t\t\tthrow new IllegalStateException(\"Spatial index ").append(escape(axis))
			.append(" member must be numeric\");\n");
		b.append("\t\t}\n");
	}

	/** A single {@code @FullText} document field: target Lucene field name, read expression, flags. */
	static final class FullTextField
	{
		final String  fieldName;
		final String  readExpr;
		final boolean analyzed;
		final boolean store;

		FullTextField(final String fieldName, final String readExpr, final boolean analyzed, final boolean store)
		{
			this.fieldName = fieldName;
			this.readExpr  = readExpr;
			this.analyzed  = analyzed;
			this.store     = store;
		}
	}

	/**
	 * Emits a nested {@code DocumentPopulator} subclass that maps each {@code @FullText} member into a
	 * Lucene document field through {@code readExpr} (reflection-free, mirroring
	 * {@code AnnotationDocumentPopulator}).
	 */
	String fullTextPopulator(final String entityRef, final String className, final List<FullTextField> fields)
	{
		final String base     = this.imports.ref("org.eclipse.store.gigamap.lucene.DocumentPopulator");
		final String document = this.imports.ref("org.apache.lucene.document.Document");
		final String store    = this.imports.ref("org.apache.lucene.document.Field.Store");

		final StringBuilder b = new StringBuilder();
		b.append("\tpublic static final class ").append(className)
			.append(" extends ").append(base).append("<").append(entityRef).append(">\n");
		b.append("\t{\n");
		b.append("\t\t@Override\n");
		b.append("\t\tpublic void populate(final ").append(document).append(" document, final ")
			.append(entityRef).append(" e)\n");
		b.append("\t\t{\n");
		int i = 0;
		for(final FullTextField f : fields)
		{
			final String fieldType = this.imports.ref(f.analyzed
				? "org.apache.lucene.document.TextField"
				: "org.apache.lucene.document.StringField");
			final String local = "v" + i++;
			b.append("\t\t\tfinal Object ").append(local).append(" = ").append(f.readExpr).append(";\n");
			b.append("\t\t\tif(").append(local).append(" != null)\n\t\t\t{\n");
			b.append("\t\t\t\tdocument.add(new ").append(fieldType).append("(\"").append(escape(f.fieldName))
				.append("\", String.valueOf(").append(local).append("), ").append(store)
				.append(f.store ? ".YES" : ".NO").append("));\n");
			b.append("\t\t\t}\n");
		}
		b.append("\t\t}\n");
		b.append("\t}\n");
		return b.toString();
	}

	/**
	 * Emits a nested {@code Vectorizer} subclass that reads the {@code @Vector float[]} member through
	 * {@code readExpr} in embedded mode (reflection-free, mirroring {@code AnnotationVectorizer}).
	 */
	String vectorizer(final String entityRef, final String className, final String readExpr)
	{
		final String base = this.imports.ref("org.eclipse.store.gigamap.jvector.Vectorizer");

		final StringBuilder b = new StringBuilder();
		b.append("\tpublic static final class ").append(className)
			.append(" extends ").append(base).append("<").append(entityRef).append(">\n");
		b.append("\t{\n");
		b.append("\t\t@Override\n");
		b.append("\t\tpublic float[] vectorize(final ").append(entityRef).append(" e)\n");
		b.append("\t\t{\n");
		b.append("\t\t\treturn ").append(readExpr).append(";\n");
		b.append("\t\t}\n\n");
		b.append("\t\t@Override\n");
		b.append("\t\tpublic boolean isEmbedded()\n");
		b.append("\t\t{\n");
		b.append("\t\t\treturn true;\n");
		b.append("\t\t}\n");
		b.append("\t}\n");
		return b.toString();
	}

	/** The generated source for a {@code @Index(creator=...)} constant plus its backing helper method. */
	static final class CreatorCode
	{
		final String declaredType;
		final String initializer;
		final String helperMethod;

		CreatorCode(final String declaredType, final String initializer, final String helperMethod)
		{
			this.declaredType = declaredType;
			this.initializer  = initializer;
			this.helperMethod = helperMethod;
		}
	}

	/**
	 * Emits a constant backed by a custom {@code Indexer.Creator}: a typed {@code Indexer<E, K>} (or
	 * {@code Indexer<E, ?>} when {@code keyRawFqn} is {@code null}) initialized from a generated private
	 * static helper. A plain creator is instantiated reflection-free ({@code new C().create()}); a
	 * {@code MemberAware} creator additionally receives the resolved index name and the reflective
	 * member (mirroring the runtime {@code IndexerGenerator.instantiateCreator}).
	 */
	CreatorCode creator(
		final String  entityRef,
		final String  helperName,
		final String  creatorFqn,
		final String  keyRawFqn,     // null -> wildcard
		final boolean memberAware,
		final String  declaringFqn,
		final boolean methodMember,
		final String  memberName,
		final String  indexName
	)
	{
		final String indexer    = this.imports.ref(TYPES + "Indexer");
		final String creatorRef = this.imports.ref(creatorFqn);
		final String keyRef     = keyRawFqn == null ? "?" : this.imports.ref(keyRawFqn);
		final String declared   = indexer + "<" + entityRef + ", " + keyRef + ">";

		final StringBuilder b = new StringBuilder();
		b.append("\t@SuppressWarnings(\"unchecked\")\n");
		b.append("\tprivate static ").append(declared).append(" ").append(helperName).append("()\n");
		b.append("\t{\n");
		if(memberAware)
		{
			final String declaringRef = this.imports.ref(declaringFqn);
			final String memberType   = this.imports.ref(methodMember
				? "java.lang.reflect.Method" : "java.lang.reflect.Field");
			final String lookup       = methodMember ? "getDeclaredMethod" : "getDeclaredField";
			final String checkedEx    = methodMember ? "NoSuchMethodException" : "NoSuchFieldException";

			b.append("\t\tfinal ").append(creatorRef).append(" creator = new ").append(creatorRef).append("();\n");
			b.append("\t\ttry\n\t\t{\n");
			b.append("\t\t\tfinal ").append(memberType).append(" member = ").append(declaringRef)
				.append(".class.").append(lookup).append("(\"").append(escape(memberName)).append("\");\n");
			b.append("\t\t\tmember.trySetAccessible();\n");
			b.append("\t\t\tcreator.initialize(\"").append(escape(indexName)).append("\", member);\n");
			b.append("\t\t}\n");
			b.append("\t\tcatch(final ").append(checkedEx).append(" e)\n\t\t{\n");
			b.append("\t\t\tthrow new RuntimeException(e);\n\t\t}\n");
			b.append("\t\treturn (").append(declared).append(")(").append(indexer)
				.append("<?, ?>) creator.create();\n");
		}
		else
		{
			b.append("\t\treturn (").append(declared).append(")(").append(indexer)
				.append("<?, ?>) new ").append(creatorRef).append("().create();\n");
		}
		b.append("\t}\n");

		return new CreatorCode(declared, helperName + "()", b.toString());
	}

	// ---- shared constant templates -------------------------------------------------------------

	/** A single-{@code E}-parameter indexer overriding {@code name()} and one protected getter. */
	private IndexerCode single(
		final String simpleType ,
		final String entityRef  ,
		final String indexName  ,
		final String returnType ,
		final String getter     ,
		final String readExpr
	)
	{
		final String base       = this.imports.ref(TYPES + simpleType);
		final String returnRef  = this.imports.ref(returnType);
		final String declared   = base + "<" + entityRef + ">";
		final StringBuilder b = new StringBuilder();
		b.append("new ").append(base).append(".Abstract<").append(entityRef).append(">()\n");
		b.append("\t{\n");
		this.appendName(b, indexName);
		b.append("\t\t@Override\n");
		b.append("\t\tprotected ").append(returnRef).append(" ").append(getter)
			.append("(final ").append(entityRef).append(" e)\n");
		b.append("\t\t{\n");
		b.append("\t\t\treturn ").append(readExpr).append(";\n");
		b.append("\t\t}\n");
		b.append("\t}");
		return new IndexerCode(declared, b.toString());
	}

	/** An {@code Indexer}/{@code IndexerComparing} keyed by {@code keyRef}, overriding name/keyType/index. */
	private IndexerCode keyed(
		final String simpleType,
		final String entityRef ,
		final String keyRef    ,
		final String indexName ,
		final String readExpr
	)
	{
		final String base     = this.imports.ref(TYPES + simpleType);
		final String declared = base + "<" + entityRef + ", " + keyRef + ">";
		final StringBuilder b = new StringBuilder();
		b.append("new ").append(base).append(".Abstract<").append(entityRef).append(", ").append(keyRef).append(">()\n");
		b.append("\t{\n");
		this.appendName(b, indexName);
		b.append("\t\t@Override\n");
		b.append("\t\tpublic Class<").append(keyRef).append("> keyType()\n");
		b.append("\t\t{\n");
		b.append("\t\t\treturn ").append(keyRef).append(".class;\n");
		b.append("\t\t}\n\n");
		b.append("\t\t@Override\n");
		b.append("\t\tpublic ").append(keyRef).append(" index(final ").append(entityRef).append(" e)\n");
		b.append("\t\t{\n");
		b.append("\t\t\treturn ").append(readExpr).append(";\n");
		b.append("\t\t}\n");
		b.append("\t}");
		return new IndexerCode(declared, b.toString());
	}

	private IndexerCode multiValue(
		final String entityRef,
		final String keyRef   ,
		final String indexName,
		final String readExpr
	)
	{
		final String base     = this.imports.ref(TYPES + "IndexerMultiValue");
		final String declared = base + "<" + entityRef + ", " + keyRef + ">";
		final StringBuilder b = new StringBuilder();
		b.append("new ").append(base).append(".Abstract<").append(entityRef).append(", ").append(keyRef).append(">()\n");
		b.append("\t{\n");
		this.appendName(b, indexName);
		b.append("\t\t@Override\n");
		b.append("\t\tpublic Class<").append(keyRef).append("> keyType()\n");
		b.append("\t\t{\n");
		b.append("\t\t\treturn ").append(keyRef).append(".class;\n");
		b.append("\t\t}\n\n");
		b.append("\t\t@Override\n");
		b.append("\t\tpublic Iterable<? extends ").append(keyRef).append("> indexEntityMultiValue(final ")
			.append(entityRef).append(" e)\n");
		b.append("\t\t{\n");
		b.append("\t\t\treturn ").append(readExpr).append(";\n");
		b.append("\t\t}\n");
		b.append("\t}");
		return new IndexerCode(declared, b.toString());
	}

	private void appendName(final StringBuilder b, final String indexName)
	{
		b.append("\t\t@Override\n");
		b.append("\t\tpublic String name()\n");
		b.append("\t\t{\n");
		b.append("\t\t\treturn \"").append(escape(indexName)).append("\";\n");
		b.append("\t\t}\n\n");
	}

	// ---- type categories (mirror of XTypes) ----------------------------------------------------

	boolean isNumericCoordinate(final TypeMirror t)
	{
		return this.isByteType(t) || this.isShortType(t) || this.isIntegerType(t) || this.isLongType(t)
			|| this.isFloatType(t) || this.isDoubleType(t)
			|| this.isAssignableTo(t, "java.lang.Number");
	}

	private boolean isType(final TypeMirror t, final String fqn)
	{
		return t.getKind() == TypeKind.DECLARED && this.rawName(t).equals(fqn);
	}

	private boolean isBooleanType(final TypeMirror t)
	{
		return t.getKind() == TypeKind.BOOLEAN || this.isType(t, "java.lang.Boolean");
	}

	private boolean isByteType(final TypeMirror t)
	{
		return t.getKind() == TypeKind.BYTE || this.isType(t, "java.lang.Byte");
	}

	private boolean isShortType(final TypeMirror t)
	{
		return t.getKind() == TypeKind.SHORT || this.isType(t, "java.lang.Short");
	}

	private boolean isIntegerType(final TypeMirror t)
	{
		return t.getKind() == TypeKind.INT || this.isType(t, "java.lang.Integer");
	}

	private boolean isLongType(final TypeMirror t)
	{
		return t.getKind() == TypeKind.LONG || this.isType(t, "java.lang.Long");
	}

	private boolean isFloatType(final TypeMirror t)
	{
		return t.getKind() == TypeKind.FLOAT || this.isType(t, "java.lang.Float");
	}

	private boolean isDoubleType(final TypeMirror t)
	{
		return t.getKind() == TypeKind.DOUBLE || this.isType(t, "java.lang.Double");
	}

	private boolean isCharacterType(final TypeMirror t)
	{
		return t.getKind() == TypeKind.CHAR || this.isType(t, "java.lang.Character");
	}

	private boolean isNaturalNumberType(final TypeMirror t)
	{
		return this.isByteType(t) || this.isShortType(t) || this.isIntegerType(t) || this.isLongType(t)
			|| this.isType(t, "java.math.BigInteger")
			|| this.isType(t, "java.util.concurrent.atomic.AtomicInteger")
			|| this.isType(t, "java.util.concurrent.atomic.AtomicLong");
	}

	private boolean isEnum(final TypeMirror t)
	{
		if(t.getKind() != TypeKind.DECLARED)
		{
			return false;
		}
		final Element e = ((DeclaredType)t).asElement();
		return e.getKind() == ElementKind.ENUM;
	}

	private boolean isIterable(final TypeMirror t)
	{
		return this.isAssignableTo(t, "java.lang.Iterable");
	}

	private boolean isComparable(final TypeMirror t)
	{
		return this.isAssignableTo(t, "java.lang.Comparable");
	}

	private boolean isAssignableTo(final TypeMirror t, final String superFqn)
	{
		if(t.getKind() != TypeKind.DECLARED)
		{
			return false;
		}
		final TypeElement sup = this.elements.getTypeElement(superFqn);
		if(sup == null)
		{
			return false;
		}
		return this.types.isAssignable(this.types.erasure(t), this.types.erasure(sup.asType()));
	}

	/** The single {@code Class} type argument of an {@code Iterable<X>}, import-resolved, or {@code null}. */
	private String singleTypeArgument(final TypeMirror t)
	{
		if(t.getKind() != TypeKind.DECLARED)
		{
			return null;
		}
		final List<? extends TypeMirror> args = ((DeclaredType)t).getTypeArguments();
		if(args.size() != 1 || args.get(0).getKind() != TypeKind.DECLARED)
		{
			return null;
		}
		return this.refRaw(args.get(0));
	}

	/** Import-resolved raw (erased) name, suitable for a {@code .class} literal and a type argument. */
	private String refRaw(final TypeMirror t)
	{
		return this.imports.ref(this.rawName(t));
	}

	private String rawName(final TypeMirror t)
	{
		return this.types.erasure(t).toString();
	}

	/** Import-resolved boxed name for primitives; import-resolved raw name otherwise. */
	private String box(final TypeMirror t)
	{
		switch(t.getKind())
		{
			case BOOLEAN: return "Boolean";
			case BYTE   : return "Byte";
			case SHORT  : return "Short";
			case INT    : return "Integer";
			case LONG   : return "Long";
			case CHAR   : return "Character";
			case FLOAT  : return "Float";
			case DOUBLE : return "Double";
			default     : return this.refRaw(t);
		}
	}

	private static String escape(final String s)
	{
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}