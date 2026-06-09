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

import org.eclipse.store.gigamap.annotations.Identity;
import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.Indexed;
import org.eclipse.store.gigamap.annotations.SpatialIndex;
import org.eclipse.store.gigamap.annotations.Unique;
import org.eclipse.store.gigamap.codegen.IndexerEmitter.IndexerCode;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Compile-time annotation processor that emits, for each entity carrying GigaMap index annotations
 * ({@link Index}, {@link Unique}, {@link Identity}, {@link SpatialIndex} or the {@link Indexed}
 * marker), a sibling {@code <Entity>_} metamodel class of typed {@code public static final} indexer
 * constants plus a {@code registerIndices(GigaMap)} helper.
 * <p>
 * This is the compile-time, reflection-free counterpart of the runtime
 * {@code IndexerGenerator.AnnotationBased}: the type/kind to indexer-flavor mapping is reproduced in
 * {@link IndexerEmitter}. Bitmap and spatial indices yield typed query constants. Full-text
 * ({@code @FullText}, Lucene) and vector ({@code @Vector}, JVector) annotations are wired up too: the
 * processor emits a reflection-free {@code DocumentPopulator} / {@code Vectorizer} and registration
 * code in {@code registerIndices} (in-graph / in-memory defaults), but — unlike bitmap indices — no
 * typed query handle, since those are per-map runtime objects. Members carrying a custom
 * {@code @Index(creator=...)}, and {@code @Vector(onDisk=true)}, cannot be reproduced at compile time
 * and are skipped with a note — those stay on the runtime generator.
 * <p>
 * The full-text / vector annotations are read via the {@code javax.lang.model} mirror API by fully
 * qualified name, so this processor depends only on the GigaMap core (not the Lucene / JVector
 * modules); the generated code references their types, which are on the user's classpath whenever the
 * annotations are used.
 * <p>
 * The suffix appended to the entity's simple name to form the metamodel name (default {@code "_"},
 * e.g. {@code Person} &rarr; {@code Person_}) is configurable through the processor option
 * {@value #SUFFIX_OPTION}, for example {@code -Agigamap.metamodel.suffix=Meta}.
 */
@SupportedAnnotationTypes({
	"org.eclipse.store.gigamap.annotations.Index",
	"org.eclipse.store.gigamap.annotations.Unique",
	"org.eclipse.store.gigamap.annotations.Identity",
	"org.eclipse.store.gigamap.annotations.SpatialIndex",
	"org.eclipse.store.gigamap.annotations.Indexed",
	"org.eclipse.store.gigamap.lucene.annotations.FullText",
	"org.eclipse.store.gigamap.jvector.annotations.Vector"
})
@SupportedOptions(IndexMetamodelProcessor.SUFFIX_OPTION)
public final class IndexMetamodelProcessor extends AbstractProcessor
{
	/** Processor option selecting the metamodel type-name suffix; defaults to {@value #DEFAULT_SUFFIX}. */
	static final String SUFFIX_OPTION  = "gigamap.metamodel.suffix";
	static final String DEFAULT_SUFFIX = "_";

	private static final String DUMMY_CREATOR = "org.eclipse.store.gigamap.types.Indexer.Creator.Dummy";

	private static final String FULLTEXT_ANNOTATION = "org.eclipse.store.gigamap.lucene.annotations.FullText";
	private static final String VECTOR_ANNOTATION   = "org.eclipse.store.gigamap.jvector.annotations.Vector";
	private static final String FULLTEXT_POPULATOR  = "FullTextPopulator";
	private static final String VECTORIZER_CLASS    = "EmbeddingVectorizer";

	private Types             types;
	private Elements          elements;
	private String            suffix;
	private final Set<String> processed = new HashSet<>();

	@Override
	public SourceVersion getSupportedSourceVersion()
	{
		return SourceVersion.latestSupported();
	}

	@Override
	public synchronized void init(final ProcessingEnvironment env)
	{
		super.init(env);
		this.types    = env.getTypeUtils();
		this.elements = env.getElementUtils();

		final String configured = env.getOptions().get(SUFFIX_OPTION);
		this.suffix = configured == null || configured.isEmpty() ? DEFAULT_SUFFIX : configured;
	}

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv)
	{
		if(roundEnv.processingOver())
		{
			return false;
		}

		final Set<TypeElement> entities = new LinkedHashSet<>();
		this.collectEntities(roundEnv, Index.class       , entities);
		this.collectEntities(roundEnv, Unique.class       , entities);
		this.collectEntities(roundEnv, Identity.class     , entities);
		this.collectEntities(roundEnv, SpatialIndex.class , entities);
		this.collectEntities(roundEnv, Indexed.class       , entities);
		this.collectEntities(roundEnv, FULLTEXT_ANNOTATION, entities);
		this.collectEntities(roundEnv, VECTOR_ANNOTATION  , entities);

		for(final TypeElement entity : entities)
		{
			final String fqn = entity.getQualifiedName().toString();
			if(this.processed.add(fqn))
			{
				try
				{
					this.generate(entity);
				}
				catch(final IOException e)
				{
					this.error(entity, "Failed to write index metamodel: " + e.getMessage());
				}
			}
		}

		return false;
	}

	private void collectEntities(
		final RoundEnvironment            roundEnv  ,
		final Class<? extends Annotation> annotation,
		final Set<TypeElement>            entities
	)
	{
		for(final Element e : roundEnv.getElementsAnnotatedWith(annotation))
		{
			addEntity(e, entities);
		}
	}

	/**
	 * Like {@link #collectEntities(RoundEnvironment, Class, Set)} but resolves the annotation by name,
	 * so it works for the Lucene / JVector annotations that are not on this processor's classpath. A
	 * no-op when the annotation type is absent from the compilation (then no entity uses it).
	 */
	private void collectEntities(
		final RoundEnvironment roundEnv      ,
		final String           annotationFqn ,
		final Set<TypeElement> entities
	)
	{
		final TypeElement annotation = this.elements.getTypeElement(annotationFqn);
		if(annotation == null)
		{
			return;
		}
		for(final Element e : roundEnv.getElementsAnnotatedWith(annotation))
		{
			addEntity(e, entities);
		}
	}

	private static void addEntity(final Element e, final Set<TypeElement> entities)
	{
		final TypeElement entity = e.getKind().isClass() || e.getKind().isInterface()
			? (TypeElement)e
			: enclosingType(e);
		if(entity != null && (entity.getKind() == ElementKind.CLASS || entity.getKind() == ElementKind.RECORD))
		{
			entities.add(entity);
		}
	}

	private static TypeElement enclosingType(final Element e)
	{
		for(Element c = e.getEnclosingElement(); c != null; c = c.getEnclosingElement())
		{
			if(c instanceof TypeElement)
			{
				return (TypeElement)c;
			}
		}
		return null;
	}

	// ---- model building ------------------------------------------------------------------------

	private void generate(final TypeElement entity) throws IOException
	{
		final String entityFqn     = entity.getQualifiedName().toString();
		final String pkg           = this.elements.getPackageOf(entity).getQualifiedName().toString();
		final String metamodelName = entity.getSimpleName().toString() + this.suffix;

		final Imports        imports   = new Imports(pkg);
		final String         entityRef = imports.ref(entityFqn);
		final IndexerEmitter emitter   = new IndexerEmitter(this.types, this.elements, imports);

		final List<GeneratedConstant> constants     = new ArrayList<>();
		final Set<String>             indexNames    = new HashSet<>();
		final List<String>            helperMethods = new ArrayList<>();

		for(final Member member : this.collectAnnotatedMembers(entity))
		{
			final Index    index    = member.element.getAnnotation(Index.class);
			final boolean  unique   = member.element.getAnnotation(Unique.class)   != null;
			final boolean  identity = member.element.getAnnotation(Identity.class) != null;

			final String indexName = index != null && !index.name().isEmpty()
				? index.name()
				: member.propertyName;

			if(!indexNames.add(indexName))
			{
				this.error(member.element, "Double index name '" + indexName + "' in " + entityFqn);
				continue;
			}

			if(index != null)
			{
				final TypeMirror creator = creatorType(index);
				if(creator != null)
				{
					final String id = this.identifier(member.propertyName, constants);
					final IndexerEmitter.CreatorCode cc =
						this.buildCreator(entity, entityRef, emitter, member, indexName, id, creator);
					if(cc != null)
					{
						helperMethods.add(cc.helperMethod);
						constants.add(new GeneratedConstant(id, cc.declaredType, cc.initializer, unique, identity));
					}
					continue; // generated, or NOTE + skip (falls back to the runtime generator)
				}
			}

			final String readExpr = this.readExpression(entity, member);
			if(readExpr == null)
			{
				continue; // diagnostic already emitted
			}

			final IndexerCode code = emitter.emit(
				entityRef, indexName, readExpr, member.type, index, unique
			);
			if(code == null)
			{
				this.error(member.element,
					"Unsupported field type for annotation based index generation: " + member.type);
				continue;
			}

			constants.add(new GeneratedConstant(
				this.identifier(member.propertyName, constants),
				code.declaredType,
				code.initializer,
				unique,
				identity
			));
		}

		this.addSpatial(entity, entityRef, emitter, indexNames, constants);

		final FullTextPlan fullText = this.buildFullText(entity, entityRef, emitter);
		final VectorPlan   vector   = this.buildVector(entity, entityRef, emitter, metamodelName);

		this.writeSource(entity, pkg, metamodelName, entityRef, imports, constants, helperMethods, fullText, vector);
	}

	/** Builds the {@code @FullText} populator plan, or {@code null} if the entity has no full-text members. */
	private FullTextPlan buildFullText(final TypeElement entity, final String entityRef, final IndexerEmitter emitter)
	{
		final List<Member> members = this.collectMembers(entity, e -> findMirror(e, FULLTEXT_ANNOTATION) != null);
		if(members.isEmpty())
		{
			return null;
		}
		final List<IndexerEmitter.FullTextField> fields = new ArrayList<>();
		for(final Member member : members)
		{
			final String readExpr = this.readExpression(entity, member);
			if(readExpr == null)
			{
				continue; // diagnostic already emitted
			}
			final AnnotationMirror am = findMirror(member.element, FULLTEXT_ANNOTATION);
			fields.add(new IndexerEmitter.FullTextField(
				nonEmpty(stringValue(am, "name"), member.propertyName),
				readExpr,
				boolValue(am, "analyzed", true),
				boolValue(am, "store", true)
			));
		}
		if(fields.isEmpty())
		{
			return null;
		}
		return new FullTextPlan(emitter.fullTextPopulator(entityRef, FULLTEXT_POPULATOR, fields));
	}

	/** Builds the {@code @Vector} vectorizer plan, or {@code null} if absent / not generatable. */
	private VectorPlan buildVector(
		final TypeElement    entity,
		final String         entityRef,
		final IndexerEmitter emitter,
		final String         metamodelName
	)
	{
		final List<Member> members = this.collectMembers(entity, e -> findMirror(e, VECTOR_ANNOTATION) != null);
		if(members.isEmpty())
		{
			return null;
		}
		if(members.size() > 1)
		{
			this.error(entity, "Multiple @Vector members in " + entity.getQualifiedName()
				+ "; only one vector index per entity is supported");
			return null;
		}
		final Member member = members.get(0);
		if(isIndexAnnotated(member.element))
		{
			this.error(member.element, "A @Vector member must not also carry @Index / @Unique / @Identity");
			return null;
		}
		if(!isFloatArray(member.type))
		{
			this.error(member.element, "A @Vector member must be of type float[]");
			return null;
		}

		final AnnotationMirror am = findMirror(member.element, VECTOR_ANNOTATION);
		final int dimension = intValue(am, "dimension", 0);
		if(dimension <= 0)
		{
			this.error(member.element, "@Vector requires a positive dimension");
			return null;
		}
		if(boolValue(am, "onDisk", false))
		{
			this.note(member.element, "@Vector(onDisk = true) needs an index directory and is not generated into "
				+ metamodelName + "; register it via the runtime VectorAnnotationHandler.New(Path).");
			return null;
		}

		final String readExpr = this.readExpression(entity, member);
		if(readExpr == null)
		{
			return null; // diagnostic already emitted
		}
		return new VectorPlan(
			emitter.vectorizer(entityRef, VECTORIZER_CLASS, readExpr),
			nonEmpty(stringValue(am, "name"), member.propertyName),
			dimension,
			enumValue(am, "similarity", "COSINE")
		);
	}

	private void addSpatial(
		final TypeElement             entity,
		final String                  entityRef,
		final IndexerEmitter          emitter,
		final Set<String>             indexNames,
		final List<GeneratedConstant> constants
	)
	{
		final SpatialIndex spatial = entity.getAnnotation(SpatialIndex.class);
		if(spatial == null)
		{
			return;
		}
		final String name = spatial.name().isEmpty() ? "spatial" : spatial.name();
		if(!indexNames.add(name))
		{
			this.error(entity, "Double index name '" + name + "' in " + entity.getQualifiedName());
			return;
		}

		final Member lat = this.resolveMember(entity, spatial.latitude());
		final Member lon = this.resolveMember(entity, spatial.longitude());
		if(lat == null || lon == null)
		{
			return; // diagnostic already emitted
		}
		if(!emitter.isNumericCoordinate(lat.type) || !emitter.isNumericCoordinate(lon.type))
		{
			this.error(entity, "Spatial index '" + name + "' coordinate members must be numeric");
			return;
		}

		final String latRead = this.readExpression(entity, lat);
		final String lonRead = this.readExpression(entity, lon);
		if(latRead == null || lonRead == null)
		{
			return;
		}

		final IndexerCode code = emitter.spatial(entityRef, name, latRead, lonRead);
		constants.add(new GeneratedConstant(
			this.identifier(name, constants),
			code.declaredType,
			code.initializer,
			false,
			false
		));
	}

	/**
	 * Mirrors {@code IndexerGenerator.AnnotationBased.collectAnnotatedMembers}: index-annotated
	 * instance fields take precedence over getters of the same property (case-insensitive); both are
	 * collected across the superclass chain.
	 */
	private List<Member> collectAnnotatedMembers(final TypeElement entity)
	{
		return this.collectMembers(entity, IndexMetamodelProcessor::isIndexAnnotated);
	}

	/**
	 * Collects instance members (fields, then no-argument getters) matching {@code annotated} across
	 * the superclass chain; fields take precedence over getters of the same property
	 * (case-insensitive).
	 */
	private List<Member> collectMembers(final TypeElement entity, final Predicate<Element> annotated)
	{
		final List<Member> result    = new ArrayList<>();
		final Set<String>  seenProps = new HashSet<>();

		for(final TypeElement t : this.hierarchy(entity))
		{
			for(final VariableElement field : fieldsOf(t))
			{
				if(!field.getModifiers().contains(Modifier.STATIC) && annotated.test(field))
				{
					final String prop = field.getSimpleName().toString();
					if(seenProps.add(normalize(prop)))
					{
						result.add(new Member(field, field.asType(), prop));
					}
				}
			}
		}

		for(final TypeElement t : this.hierarchy(entity))
		{
			for(final ExecutableElement method : methodsOf(t))
			{
				if(method.getModifiers().contains(Modifier.STATIC)
					|| !method.getParameters().isEmpty()
					|| method.getReturnType().getKind() == TypeKind.VOID)
				{
					continue;
				}
				if(annotated.test(method))
				{
					final String prop = derivePropertyName(method);
					if(seenProps.add(normalize(prop)))
					{
						result.add(new Member(method, method.getReturnType(), prop));
					}
				}
			}
		}

		return result;
	}

	private List<TypeElement> hierarchy(final TypeElement entity)
	{
		final List<TypeElement> chain = new ArrayList<>();
		for(TypeElement t = entity; t != null && !isObject(t); )
		{
			chain.add(t);
			final TypeMirror sup = t.getSuperclass();
			t = sup.getKind() == TypeKind.DECLARED ? (TypeElement)((DeclaredType)sup).asElement() : null;
		}
		return chain;
	}

	private static boolean isObject(final TypeElement t)
	{
		return t.getQualifiedName().contentEquals("java.lang.Object");
	}

	/**
	 * Resolves a spatial coordinate member referenced by name: a field by exact name, or a method by
	 * exact name or derived property name (mirrors {@code AnnotationBased.resolveMember}).
	 */
	private Member resolveMember(final TypeElement entity, final String propertyName)
	{
		for(final TypeElement t : this.hierarchy(entity))
		{
			for(final VariableElement field : fieldsOf(t))
			{
				if(!field.getModifiers().contains(Modifier.STATIC)
					&& field.getSimpleName().contentEquals(propertyName))
				{
					return new Member(field, field.asType(), propertyName);
				}
			}
		}
		for(final TypeElement t : this.hierarchy(entity))
		{
			for(final ExecutableElement method : methodsOf(t))
			{
				if(method.getModifiers().contains(Modifier.STATIC)
					|| !method.getParameters().isEmpty()
					|| method.getReturnType().getKind() == TypeKind.VOID)
				{
					continue;
				}
				if(method.getSimpleName().contentEquals(propertyName)
					|| derivePropertyName(method).equals(propertyName))
				{
					return new Member(method, method.getReturnType(), derivePropertyName(method));
				}
			}
		}
		this.error(entity, "No field or getter '" + propertyName + "' found in " + entity.getQualifiedName());
		return null;
	}

	// ---- accessor (read expression) selection --------------------------------------------------

	/**
	 * The source expression reading {@code member} off a parameter {@code e}: a direct field access
	 * when the field is accessible from the generated metamodel, otherwise an accessible getter call.
	 * The metamodel sits in the entity's package and is not a subclass, so a member is reachable only
	 * when it is {@code public} or declared in the entity's package (see {@link #isAccessibleFrom}).
	 */
	private String readExpression(final TypeElement entity, final Member member)
	{
		if(member.element.getKind() == ElementKind.METHOD)
		{
			if(this.isAccessibleFrom(member.element, entity))
			{
				return "e." + member.element.getSimpleName() + "()";
			}
			this.error(member.element, "Indexed accessor '" + member.element.getSimpleName()
				+ "' is not accessible from the generated metamodel (must be public or in the entity's package)");
			return null;
		}

		// field
		if(this.isAccessibleFrom(member.element, entity))
		{
			return "e." + member.element.getSimpleName();
		}
		final ExecutableElement getter = this.findGetter(entity, member);
		if(getter != null)
		{
			return "e." + getter.getSimpleName() + "()";
		}
		this.error(member.element, "Indexed field '" + member.element.getSimpleName()
			+ "' is not accessible and has no accessible getter reachable from the generated metamodel");
		return null;
	}

	/**
	 * Whether {@code member} can be referenced from a generated class in the entity's package. The
	 * metamodel is not a subclass of the member's declaring type, so {@code protected} only helps when
	 * the declaring type is in the entity's package; {@code private} is never reachable.
	 */
	private boolean isAccessibleFrom(final Element member, final TypeElement entity)
	{
		final Set<Modifier> modifiers = member.getModifiers();
		if(modifiers.contains(Modifier.PRIVATE))
		{
			return false;
		}
		if(modifiers.contains(Modifier.PUBLIC))
		{
			return true;
		}
		return this.elements.getPackageOf(member).equals(this.elements.getPackageOf(entity));
	}

	private ExecutableElement findGetter(final TypeElement entity, final Member field)
	{
		final String prop    = field.propertyName;
		final String cap     = Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
		final boolean isBool = field.type.getKind() == TypeKind.BOOLEAN
			|| (field.type.getKind() == TypeKind.DECLARED
				&& ((DeclaredType)field.type).asElement().getSimpleName().contentEquals("Boolean"));

		for(final TypeElement t : this.hierarchy(entity))
		{
			for(final ExecutableElement method : methodsOf(t))
			{
				if(method.getModifiers().contains(Modifier.STATIC)
					|| method.getModifiers().contains(Modifier.PRIVATE)
					|| !method.getParameters().isEmpty()
					|| method.getReturnType().getKind() == TypeKind.VOID)
				{
					continue;
				}
				final String n = method.getSimpleName().toString();
				if((n.equals("get" + cap)
					|| (isBool && n.equals("is" + cap))
					|| n.equals(prop)) // record component accessor
					&& this.isAccessibleFrom(method, entity))
				{
					return method;
				}
			}
		}
		return null;
	}

	// ---- source emission -----------------------------------------------------------------------

	private void writeSource(
		final TypeElement             entity,
		final String                  pkg,
		final String                  metamodelName,
		final String                  entityRef,
		final Imports                 imports,
		final List<GeneratedConstant> constants,
		final List<String>            helperMethods,
		final FullTextPlan            fullText,
		final VectorPlan              vector
	) throws IOException
	{
		// resolve the remaining references (these register their imports) before emitting the header
		final String generatedRef = imports.ref("javax.annotation.processing.Generated");

		final StringBuilder body = new StringBuilder();
		body.append("@").append(generatedRef).append("(\"")
			.append(IndexMetamodelProcessor.class.getName()).append("\")\n");
		body.append("public final class ").append(metamodelName).append("\n{\n");

		for(final GeneratedConstant c : constants)
		{
			body.append("\tpublic static final ").append(c.declaredType).append(' ').append(c.identifier)
				.append(" =\n\t\t").append(c.initializer).append(";\n\n");
		}

		this.appendRegisterIndices(body, entityRef, imports, constants, fullText, vector);

		for(final String helper : helperMethods)
		{
			body.append("\n").append(helper);
		}

		if(fullText != null)
		{
			body.append("\n").append(fullText.source);
		}
		if(vector != null)
		{
			body.append("\n").append(vector.source);
		}

		body.append("\n\tprivate ").append(metamodelName).append("()\n\t{\n")
			.append("\t\t// static metamodel; not instantiable\n\t}\n");
		body.append("}\n");

		final String qualified = pkg.isEmpty() ? metamodelName : pkg + "." + metamodelName;
		final JavaFileObject file = this.processingEnv.getFiler().createSourceFile(qualified, entity);
		try(final Writer w = file.openWriter())
		{
			final StringBuilder out = new StringBuilder();
			if(!pkg.isEmpty())
			{
				out.append("package ").append(pkg).append(";\n\n");
			}
			boolean anyImport = false;
			for(final String name : imports.imported())
			{
				out.append("import ").append(name).append(";\n");
				anyImport = true;
			}
			if(anyImport)
			{
				out.append("\n");
			}
			out.append(body);
			w.write(out.toString());
		}
	}

	private void appendRegisterIndices(
		final StringBuilder           b,
		final String                  entityRef,
		final Imports                 imports,
		final List<GeneratedConstant> constants,
		final FullTextPlan            fullText,
		final VectorPlan              vector
	)
	{
		final List<GeneratedConstant> nonUnique = new ArrayList<>();
		final List<GeneratedConstant> unique    = new ArrayList<>();
		final List<GeneratedConstant> identity  = new ArrayList<>();
		for(final GeneratedConstant c : constants)
		{
			(c.unique ? unique : nonUnique).add(c);
			if(c.identity)
			{
				identity.add(c);
			}
		}
		final boolean hasBitmap = !constants.isEmpty();

		final String gigaMap = imports.ref(IndexerEmitter.TYPES + "GigaMap");

		b.append("\t/**\n");
		b.append("\t * Idempotently registers all generated indices on the given map; safe to call on a\n");
		b.append("\t * freshly created or a reloaded {@link ").append(gigaMap).append("}.\n");
		b.append("\t */\n");
		b.append("\tpublic static void registerIndices(final ").append(gigaMap).append("<")
			.append(entityRef).append("> map)\n\t{\n");

		if(hasBitmap)
		{
			final String bitmaps = imports.ref(IndexerEmitter.TYPES + "BitmapIndices");
			b.append("\t\tfinal ").append(bitmaps).append("<").append(entityRef)
				.append("> bitmap = map.index().bitmap();\n");

			if(!nonUnique.isEmpty())
			{
				b.append("\t\tbitmap.ensureAll(").append(joinIdentifiers(nonUnique)).append(");\n");
			}

			if(!unique.isEmpty())
			{
				final String set      = imports.ref("java.util.Set");
				final String hashSet  = imports.ref("java.util.HashSet");
				final String list     = imports.ref("java.util.List");
				final String arrayList = imports.ref("java.util.ArrayList");
				final String indexer  = imports.ref(IndexerEmitter.TYPES + "Indexer");

				b.append("\t\tfinal ").append(set).append("<String> existingUnique = new ").append(hashSet).append("<>();\n");
				b.append("\t\tbitmap.accessUniqueConstraints(cs -> cs.forEach(c -> existingUnique.add(c.name())));\n");
				b.append("\t\tfinal ").append(list).append("<").append(indexer).append("<? super ")
					.append(entityRef).append(", ?>> newUnique = new ").append(arrayList).append("<>();\n");
				for(final GeneratedConstant c : unique)
				{
					b.append("\t\tif(!existingUnique.contains(").append(c.identifier).append(".name()))\n\t\t{\n")
						.append("\t\t\tnewUnique.add(").append(c.identifier).append(");\n\t\t}\n");
				}
				b.append("\t\tif(!newUnique.isEmpty())\n\t\t{\n");
				b.append("\t\t\tbitmap.addUniqueConstraints(newUnique);\n\t\t}\n");
			}

			if(!identity.isEmpty())
			{
				b.append("\t\tbitmap.setIdentityIndices(").append(joinIdentifiers(identity)).append(");\n");
			}
		}

		if(fullText != null)
		{
			final String luceneIndex   = imports.ref("org.eclipse.store.gigamap.lucene.LuceneIndex");
			final String luceneContext = imports.ref("org.eclipse.store.gigamap.lucene.LuceneContext");
			// register() returns null if already present (reloaded map) — that is intentionally ignored
			b.append("\t\tmap.index().register(").append(luceneIndex).append(".Category(")
				.append(luceneContext).append(".New(new ").append(FULLTEXT_POPULATOR).append("())));\n");
		}

		if(vector != null)
		{
			final String vectorIndices = imports.ref("org.eclipse.store.gigamap.jvector.VectorIndices");
			final String vectorConfig  = imports.ref("org.eclipse.store.gigamap.jvector.VectorIndexConfiguration");
			final String similarity    = imports.ref("org.eclipse.store.gigamap.jvector.VectorSimilarityFunction");
			b.append("\t\t").append(vectorIndices).append("<").append(entityRef)
				.append("> vectorIndices = map.index().register(").append(vectorIndices).append(".Category());\n");
			b.append("\t\tif(vectorIndices == null)\n\t\t{\n");
			b.append("\t\t\tvectorIndices = map.index().get(").append(vectorIndices).append(".class);\n\t\t}\n");
			b.append("\t\tvectorIndices.ensure(\"").append(quote(vector.name)).append("\", ")
				.append(vectorConfig).append(".builder().dimension(").append(vector.dimension)
				.append(").similarityFunction(").append(similarity).append(".").append(vector.similarity)
				.append(").onDisk(false).build(), new ").append(VECTORIZER_CLASS).append("());\n");
		}

		b.append("\t}\n");
	}

	private static String joinIdentifiers(final List<GeneratedConstant> constants)
	{
		final StringBuilder b = new StringBuilder();
		for(int i = 0; i < constants.size(); i++)
		{
			if(i > 0)
			{
				b.append(", ");
			}
			b.append(constants.get(i).identifier);
		}
		return b.toString();
	}

	// ---- helpers -------------------------------------------------------------------------------

	private static boolean isIndexAnnotated(final Element element)
	{
		return element.getAnnotation(Index.class) != null
			|| element.getAnnotation(Unique.class) != null
			|| element.getAnnotation(Identity.class) != null;
	}

	/** The custom {@code Indexer.Creator} type of an {@code @Index}, or {@code null} for the default. */
	private static TypeMirror creatorType(final Index index)
	{
		try
		{
			index.creator();
			return null; // unreachable: accessing a Class-valued member throws
		}
		catch(final MirroredTypeException e)
		{
			final TypeMirror creator = e.getTypeMirror();
			return creator.toString().equals(DUMMY_CREATOR) ? null : creator;
		}
	}

	/**
	 * Builds a constant backed by a custom creator, or {@code null} (with a NOTE) when the creator is not
	 * referenceable from generated code — then the index falls back to the runtime generator.
	 */
	private IndexerEmitter.CreatorCode buildCreator(
		final TypeElement    entity,
		final String         entityRef,
		final IndexerEmitter emitter,
		final Member         member,
		final String         indexName,
		final String         identifier,
		final TypeMirror     creatorMirror
	)
	{
		if(creatorMirror.getKind() != TypeKind.DECLARED)
		{
			return null;
		}
		final TypeElement creatorElement = (TypeElement)((DeclaredType)creatorMirror).asElement();
		if(!this.isReferenceableCreator(creatorElement, entity))
		{
			this.note(member.element, "Index '" + indexName + "' creator " + creatorElement.getQualifiedName()
				+ " is not accessible for compile-time wiring; register it via the runtime IndexerGenerator.");
			return null;
		}

		final TypeMirror keyType   = this.creatorKeyType(creatorMirror);
		final String     keyRawFqn = keyType != null && keyType.getKind() == TypeKind.DECLARED
			? this.types.erasure(keyType).toString()
			: null;
		final TypeElement declaring = (TypeElement)member.element.getEnclosingElement();

		return emitter.creator(
			entityRef,
			identifier + "__creator",
			creatorElement.getQualifiedName().toString(),
			keyRawFqn,
			this.isMemberAware(creatorMirror),
			declaring.getQualifiedName().toString(),
			member.element.getKind() == ElementKind.METHOD,
			member.element.getSimpleName().toString(),
			indexName
		);
	}

	/** Whether a creator class can be referenced and {@code new}-ed from generated code in the entity's package. */
	private boolean isReferenceableCreator(final TypeElement creator, final TypeElement entity)
	{
		final Set<Modifier> modifiers = creator.getModifiers();
		if(modifiers.contains(Modifier.PRIVATE))
		{
			return false;
		}
		final Element enclosing = creator.getEnclosingElement();
		final boolean nested = enclosing != null
			&& (enclosing.getKind().isClass() || enclosing.getKind().isInterface());
		if(nested && !modifiers.contains(Modifier.STATIC))
		{
			return false;
		}
		final boolean samePackage = this.elements.getPackageOf(creator)
			.equals(this.elements.getPackageOf(entity));
		if(!modifiers.contains(Modifier.PUBLIC) && !samePackage)
		{
			return false;
		}
		return hasAccessibleNoArgConstructor(creator, samePackage);
	}

	private static boolean hasAccessibleNoArgConstructor(final TypeElement type, final boolean samePackage)
	{
		boolean anyConstructor = false;
		for(final Element e : type.getEnclosedElements())
		{
			if(e.getKind() != ElementKind.CONSTRUCTOR)
			{
				continue;
			}
			anyConstructor = true;
			final ExecutableElement constructor = (ExecutableElement)e;
			if(constructor.getParameters().isEmpty())
			{
				final Set<Modifier> m = constructor.getModifiers();
				return !m.contains(Modifier.PRIVATE) && (m.contains(Modifier.PUBLIC) || samePackage);
			}
		}
		// no explicit constructors -> implicit default ctor with the class's (already checked) visibility
		return !anyConstructor;
	}

	private boolean isMemberAware(final TypeMirror creatorMirror)
	{
		final TypeElement memberAware =
			this.elements.getTypeElement("org.eclipse.store.gigamap.types.Indexer.Creator.MemberAware");
		return memberAware != null
			&& this.types.isAssignable(creatorMirror, this.types.erasure(memberAware.asType()));
	}

	/** The {@code K} of the creator's {@code Indexer.Creator<E,K>} binding, or {@code null} if not concrete. */
	private TypeMirror creatorKeyType(final TypeMirror creatorMirror)
	{
		final TypeElement creator =
			this.elements.getTypeElement("org.eclipse.store.gigamap.types.Indexer.Creator");
		return creator == null ? null : this.findTypeArgument(creatorMirror, creator, 1, new HashSet<>());
	}

	private TypeMirror findTypeArgument(
		final TypeMirror  type,
		final TypeElement target,
		final int         index,
		final Set<String> seen
	)
	{
		if(type.getKind() != TypeKind.DECLARED)
		{
			return null;
		}
		final DeclaredType declared = (DeclaredType)type;
		if(((TypeElement)declared.asElement()).getQualifiedName().contentEquals(target.getQualifiedName()))
		{
			final List<? extends TypeMirror> args = declared.getTypeArguments();
			return args.size() > index ? args.get(index) : null;
		}
		if(!seen.add(declared.asElement().toString()))
		{
			return null;
		}
		for(final TypeMirror supertype : this.types.directSupertypes(type))
		{
			final TypeMirror found = this.findTypeArgument(supertype, target, index, seen);
			if(found != null)
			{
				return found;
			}
		}
		return null;
	}

	// ---- annotation mirror reading (for the off-classpath Lucene / JVector annotations) ---------

	private static AnnotationMirror findMirror(final Element element, final String annotationFqn)
	{
		for(final AnnotationMirror am : element.getAnnotationMirrors())
		{
			if(am.getAnnotationType().toString().equals(annotationFqn))
			{
				return am;
			}
		}
		return null;
	}

	/** The explicitly-set value of an annotation attribute, or {@code null} (then the default applies). */
	private static Object rawValue(final AnnotationMirror am, final String attribute)
	{
		for(final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : am.getElementValues().entrySet())
		{
			if(e.getKey().getSimpleName().contentEquals(attribute))
			{
				return e.getValue().getValue();
			}
		}
		return null;
	}

	private static String stringValue(final AnnotationMirror am, final String attribute)
	{
		final Object v = rawValue(am, attribute);
		return v == null ? "" : (String)v;
	}

	private static boolean boolValue(final AnnotationMirror am, final String attribute, final boolean def)
	{
		final Object v = rawValue(am, attribute);
		return v == null ? def : (Boolean)v;
	}

	private static int intValue(final AnnotationMirror am, final String attribute, final int def)
	{
		final Object v = rawValue(am, attribute);
		return v == null ? def : ((Number)v).intValue();
	}

	/** The simple name of an enum-valued attribute (e.g. {@code COSINE}), or {@code def} if unset. */
	private static String enumValue(final AnnotationMirror am, final String attribute, final String def)
	{
		final Object v = rawValue(am, attribute);
		return v instanceof VariableElement ? ((VariableElement)v).getSimpleName().toString() : def;
	}

	private static String nonEmpty(final String value, final String fallback)
	{
		return value == null || value.isEmpty() ? fallback : value;
	}

	private static boolean isFloatArray(final TypeMirror type)
	{
		return type.getKind() == TypeKind.ARRAY
			&& ((ArrayType)type).getComponentType().getKind() == TypeKind.FLOAT;
	}

	private static String quote(final String s)
	{
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static List<VariableElement> fieldsOf(final TypeElement t)
	{
		final List<VariableElement> fields = new ArrayList<>();
		for(final Element e : t.getEnclosedElements())
		{
			if(e.getKind() == ElementKind.FIELD)
			{
				fields.add((VariableElement)e);
			}
		}
		return fields;
	}

	private static List<ExecutableElement> methodsOf(final TypeElement t)
	{
		final List<ExecutableElement> methods = new ArrayList<>();
		for(final Element e : t.getEnclosedElements())
		{
			if(e.getKind() == ElementKind.METHOD)
			{
				methods.add((ExecutableElement)e);
			}
		}
		return methods;
	}

	private static String derivePropertyName(final ExecutableElement method)
	{
		final String name = method.getSimpleName().toString();
		final boolean bool = method.getReturnType().getKind() == TypeKind.BOOLEAN
			|| (method.getReturnType().getKind() == TypeKind.DECLARED
				&& ((DeclaredType)method.getReturnType()).asElement().getSimpleName().contentEquals("Boolean"));
		if(name.startsWith("get") && name.length() > 3)
		{
			return decapitalize(name.substring(3));
		}
		if(name.startsWith("is") && name.length() > 2 && bool)
		{
			return decapitalize(name.substring(2));
		}
		return name;
	}

	private static String decapitalize(final String s)
	{
		if(s.isEmpty())
		{
			return s;
		}
		if(s.length() > 1 && Character.isUpperCase(s.charAt(0)) && Character.isUpperCase(s.charAt(1)))
		{
			return s;
		}
		final char[] chars = s.toCharArray();
		chars[0] = Character.toLowerCase(chars[0]);
		return new String(chars);
	}

	private static String normalize(final String propertyName)
	{
		return propertyName.toLowerCase(Locale.ROOT);
	}

	/** A valid, unique-within-the-metamodel Java identifier for a constant. */
	private String identifier(final String raw, final List<GeneratedConstant> existing)
	{
		final String id = sanitizeIdentifier(raw);
		String candidate = id;
		int n = 1;
		while(containsIdentifier(existing, candidate))
		{
			candidate = id + "_" + (++n);
		}
		return candidate;
	}

	/**
	 * Turns an arbitrary string (a property name, or an arbitrary {@code @...(name = ...)} index name)
	 * into a valid Java identifier: non-identifier characters become {@code _}, and a leading {@code _}
	 * is prepended when the result is empty, starts with a non-identifier-start character, or is a
	 * reserved word. Valid property names pass through unchanged.
	 */
	private static String sanitizeIdentifier(final String raw)
	{
		final StringBuilder b = new StringBuilder(raw.length());
		for(int i = 0; i < raw.length(); i++)
		{
			final char c = raw.charAt(i);
			b.append(Character.isJavaIdentifierPart(c) ? c : '_');
		}
		if(b.length() == 0 || !Character.isJavaIdentifierStart(b.charAt(0)) || SourceVersion.isKeyword(b.toString()))
		{
			b.insert(0, '_');
		}
		return b.toString();
	}

	private static boolean containsIdentifier(final List<GeneratedConstant> existing, final String id)
	{
		for(final GeneratedConstant c : existing)
		{
			if(c.identifier.equals(id))
			{
				return true;
			}
		}
		return false;
	}

	private void error(final Element e, final String message)
	{
		this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, e);
	}

	private void note(final Element e, final String message)
	{
		this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, e);
	}

	private static final class Member
	{
		final Element    element;
		final TypeMirror type;
		final String     propertyName;

		Member(final Element element, final TypeMirror type, final String propertyName)
		{
			this.element      = element;
			this.type         = type;
			this.propertyName = propertyName;
		}
	}

	private static final class GeneratedConstant
	{
		final String  identifier;
		final String  declaredType;
		final String  initializer;
		final boolean unique;
		final boolean identity;

		GeneratedConstant(
			final String identifier, final String declaredType, final String initializer,
			final boolean unique, final boolean identity
		)
		{
			this.identifier   = identifier;
			this.declaredType = declaredType;
			this.initializer  = initializer;
			this.unique       = unique;
			this.identity     = identity;
		}
	}

	/** A generated Lucene full-text populator (nested class source) to embed and register. */
	private static final class FullTextPlan
	{
		final String source;

		FullTextPlan(final String source)
		{
			this.source = source;
		}
	}

	/** A generated JVector vectorizer (nested class source) plus the index config from {@code @Vector}. */
	private static final class VectorPlan
	{
		final String source;
		final String name;
		final int    dimension;
		final String similarity;

		VectorPlan(final String source, final String name, final int dimension, final String similarity)
		{
			this.source     = source;
			this.name       = name;
			this.dimension  = dimension;
			this.similarity = similarity;
		}
	}
}
