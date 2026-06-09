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
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
import java.util.Set;

/**
 * Compile-time annotation processor that emits, for each entity carrying GigaMap index annotations
 * ({@link Index}, {@link Unique}, {@link Identity}, {@link SpatialIndex} or the {@link Indexed}
 * marker), a sibling {@code <Entity>_} metamodel class of typed {@code public static final} indexer
 * constants plus a {@code registerIndices(GigaMap)} helper.
 * <p>
 * This is the compile-time, reflection-free counterpart of the runtime
 * {@code IndexerGenerator.AnnotationBased}: the type/kind to indexer-flavor mapping is reproduced in
 * {@link IndexerEmitter}. Members carrying a custom {@code @Index(creator=...)}, and any full-text /
 * vector annotations, cannot be reproduced at compile time and are skipped with a note — those
 * entities must still use the runtime generator for the skipped indices.
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
	"org.eclipse.store.gigamap.annotations.Indexed"
})
@SupportedOptions(IndexMetamodelProcessor.SUFFIX_OPTION)
public final class IndexMetamodelProcessor extends AbstractProcessor
{
	/** Processor option selecting the metamodel type-name suffix; defaults to {@value #DEFAULT_SUFFIX}. */
	static final String SUFFIX_OPTION  = "gigamap.metamodel.suffix";
	static final String DEFAULT_SUFFIX = "_";

	private static final String DUMMY_CREATOR = "org.eclipse.store.gigamap.types.Indexer.Creator.Dummy";

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
			final TypeElement entity = e.getKind().isClass() || e.getKind().isInterface()
				? (TypeElement)e
				: enclosingType(e);
			if(entity != null && (entity.getKind() == ElementKind.CLASS || entity.getKind() == ElementKind.RECORD))
			{
				entities.add(entity);
			}
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

		final List<GeneratedConstant> constants  = new ArrayList<>();
		final Set<String>             indexNames = new HashSet<>();

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

			if(index != null && !isDummyCreator(index))
			{
				this.note(member.element,
					"Index '" + indexName + "' uses a custom creator and is not generated into "
					+ metamodelName + "; register it via the runtime IndexerGenerator.");
				continue;
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

		this.writeSource(entity, pkg, metamodelName, entityRef, imports, constants);
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
		final List<Member> result    = new ArrayList<>();
		final Set<String>  seenProps = new HashSet<>();

		for(final TypeElement t : this.hierarchy(entity))
		{
			for(final VariableElement field : fieldsOf(t))
			{
				if(!field.getModifiers().contains(Modifier.STATIC) && isIndexAnnotated(field))
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
				if(isIndexAnnotated(method))
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
	 * when reachable from the same package (non-private field), otherwise an accessible getter call.
	 * Methods are called directly when non-private.
	 */
	private String readExpression(final TypeElement entity, final Member member)
	{
		if(member.element.getKind() == ElementKind.METHOD)
		{
			if(member.element.getModifiers().contains(Modifier.PRIVATE))
			{
				this.error(member.element, "Indexed accessor '" + member.element.getSimpleName()
					+ "' must not be private to be reachable from the generated metamodel");
				return null;
			}
			return "e." + member.element.getSimpleName() + "()";
		}

		// field
		if(!member.element.getModifiers().contains(Modifier.PRIVATE))
		{
			return "e." + member.element.getSimpleName();
		}
		final ExecutableElement getter = this.findGetter(entity, member);
		if(getter != null)
		{
			return "e." + getter.getSimpleName() + "()";
		}
		this.error(member.element, "Indexed private field '" + member.element.getSimpleName()
			+ "' has no accessible getter reachable from the generated metamodel");
		return null;
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
				if(n.equals("get" + cap)
					|| (isBool && n.equals("is" + cap))
					|| n.equals(prop)) // record component accessor
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
		final List<GeneratedConstant> constants
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

		this.appendRegisterIndices(body, entityRef, imports, constants);

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
		final List<GeneratedConstant> constants
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

		final String gigaMap = imports.ref(IndexerEmitter.TYPES + "GigaMap");
		final String bitmaps = imports.ref(IndexerEmitter.TYPES + "BitmapIndices");

		b.append("\t/**\n");
		b.append("\t * Idempotently registers all generated indices on the given map; safe to call on a\n");
		b.append("\t * freshly created or a reloaded {@link ").append(gigaMap).append("}.\n");
		b.append("\t */\n");
		b.append("\tpublic static void registerIndices(final ").append(gigaMap).append("<")
			.append(entityRef).append("> map)\n\t{\n");
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

	private static boolean isDummyCreator(final Index index)
	{
		try
		{
			index.creator();
			return false; // unreachable: accessing a Class-valued member throws
		}
		catch(final MirroredTypeException e)
		{
			return e.getTypeMirror().toString().equals(DUMMY_CREATOR);
		}
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

	/** A Java identifier for a constant, kept unique within the metamodel. */
	private String identifier(final String propertyName, final List<GeneratedConstant> existing)
	{
		String id = propertyName;
		if(SourceVersion.isKeyword(id) || !SourceVersion.isIdentifier(id))
		{
			id = "_" + id;
		}
		String candidate = id;
		int n = 1;
		while(containsIdentifier(existing, candidate))
		{
			candidate = id + "_" + (++n);
		}
		return candidate;
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
}
