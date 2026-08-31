/**
 * EclipseStore Vanilla Viewer — StorageView model
 *
 * Faithful port of the original restclient library (org.eclipse.store.storage.restclient.types.*):
 *   - StorageView (root/members/variableMembers/ranges/createElement)
 *   - StorageViewElement / Object / Value / Range / ComplexRangeEntry
 *   - ValueRenderer (String / char literal rendering)
 *   - the persistence type-dictionary parser (member classification, enum-constant filtering)
 *
 * The element objects produced here mirror the Java model so the UI can render exactly like the
 * Vaadin TreeGrid did.
 */
const StorageView = (() => {
    "use strict";

    // Mirrors StorageViewConfiguration.Default()
    const ELEMENT_RANGE_MAX_LENGTH = 100;
    const MAX_VALUE_LENGTH         = 10_000;

    const PRIMITIVE_TYPES = new Set([
        "byte", "boolean", "short", "char", "int", "float", "long", "double"
    ]);

    // ── Value rendering (ValueRenderer.DefaultProvider) ──────────────────

    const STRING_LITERAL_TYPES = new Set([
        "java.lang.String",
        "java.lang.StringBuffer",
        "java.lang.StringBuilder",
        "org.eclipse.serializer.chars.VarString"
    ]);

    function renderStringLiteral(value) {
        let out = '"';
        for (let i = 0; i < value.length; i++) {
            const ch = value.charAt(i);
            switch (ch) {
                case "\b": out += "\\b"; break;
                case "\t": out += "\\t"; break;
                case "\n": out += "\\n"; break;
                case "\f": out += "\\f"; break;
                case "\r": out += "\\r"; break;
                case '"':  out += '\\"'; break;
                case "\\": out += "\\\\"; break;
                default:   out += ch;
            }
        }
        return out + '"';
    }

    function renderCharLiteral(value) {
        let out = "'";
        const ch = value.charAt(0);
        switch (ch) {
            case "\b": out += "\\b"; break;
            case "\t": out += "\\t"; break;
            case "\n": out += "\\n"; break;
            case "\f": out += "\\f"; break;
            case "\r": out += "\\r"; break;
            case "'":  out += "\\'"; break;
            case "\\": out += "\\\\"; break;
            default:   out += ch;
        }
        return out + "'";
    }

    function renderValue(value, typeName) {
        if (typeName != null && STRING_LITERAL_TYPES.has(typeName)) {
            return renderStringLiteral(value);
        }
        if (typeName === "char") {
            return renderCharLiteral(value);
        }
        return value;
    }

    // ── Type-name helpers (StorageViewElement.Abstract) ──────────────────

    /** Decode a JVM binary array descriptor to a Java-source style name. */
    function qualifiedName(binaryName) {
        switch (binaryName.charAt(0)) {
            case "[": return qualifiedName(binaryName.substring(1)) + "[]";
            case "L": return binaryName.substring(1, binaryName.length - 1);
            case "B": return "byte";
            case "C": return "char";
            case "D": return "double";
            case "F": return "float";
            case "I": return "int";
            case "J": return "long";
            case "S": return "short";
            case "Z": return "boolean";
            default:  return binaryName;
        }
    }

    function qualifiedTypeName(typeName) {
        if (typeName == null) {
            return "";
        }
        return typeName.startsWith("[") ? qualifiedName(typeName) : typeName;
    }

    function simpleTypeName(typeName) {
        const qn = qualifiedTypeName(typeName);
        const i = qn.lastIndexOf(".");
        return i === -1 ? qn : qn.substring(i + 1);
    }

    // ── Element factories (mirror the StorageViewElement hierarchy) ──────

    function valueElement(name, value, typeName) {
        return {
            kind: "value",
            name: name,
            value: value,
            typeName: typeName,
            isObject: false,
            objectId: null,
            hasMembers: false,
            loadMembers: null
        };
    }

    function objectSimpleElement(view, name, value, typeName, objectId) {
        return {
            kind: "object-simple",
            name: name,
            value: value,
            typeName: typeName,
            isObject: true,
            objectId: objectId,
            hasMembers: false,
            loadMembers: null
        };
    }

    function objectComplexElement(view, name, value, typeName, objectId, typeId) {
        const element = {
            kind: "object-complex",
            name: name,
            value: value,
            typeName: typeName,
            isObject: true,
            objectId: objectId,
            typeId: typeId,
            _members: null
        };
        element.hasMembers = view.allMembersCount(typeId) > 0;
        element.loadMembers = async () => {
            if (element._members === null) {
                element._members = await view.members(element);
            }
            return element._members;
        };
        return element;
    }

    function rangeElement(view, name, objectId, offset, length) {
        const element = {
            kind: "range",
            name: name,
            value: null,
            typeName: null,
            isObject: false,
            objectId: objectId,
            offset: offset,
            length: length,
            hasMembers: true,
            _members: null
        };
        element.loadMembers = async () => {
            if (element._members === null) {
                element._members = await view.variableMembers(element, objectId, offset, length);
            }
            return element._members;
        };
        return element;
    }

    function complexRangeEntryElement(name, value, members) {
        return {
            kind: "complex-range-entry",
            name: name,
            value: value,
            typeName: null,
            isObject: false,
            objectId: null,
            hasMembers: members.length > 0,
            loadMembers: async () => members
        };
    }

    // ── Type dictionary parser ───────────────────────────────────────────

    /**
     * Parse the persistence type dictionary text into a map:
     *   { [typeId]: { typeName, members: [member], allCount } }
     *
     * member = { name, typeName, isReference, isPrimitive, isEnumConstant, isComplex, complexMembers? }
     *
     * Member order is preserved verbatim (storage order: references first, primitives last),
     * which is what the REST data array aligns with.
     */
    function parseTypeDictionary(raw) {
        const dict = {};
        if (!raw) {
            return dict;
        }

        const lines = raw.split(/\r?\n/);
        let type    = null;   // current type being built
        let complex = null;   // current complex ([list]) member collecting sub-members

        for (const rawLine of lines) {
            const line = rawLine.trim();
            if (line.length === 0) {
                continue;
            }

            // Type header: <19-digit id> <typeName>{   (optionally closed immediately: {})
            if (type === null) {
                const header = line.match(/^(\d+)\s+(.*?)\s*\{\s*(\})?\s*$/);
                if (header) {
                    const typeId   = header[1].replace(/^0+/, "") || "0";
                    const typeName = header[2].trim();
                    type = { typeName: typeName, members: [], allCount: 0 };
                    dict[typeId] = type;
                    if (header[3] === "}") {
                        type = null; // empty type {}
                    }
                }
                continue;
            }

            // Inside a complex ([list]) block: collect sub-members until ')'
            if (complex !== null) {
                if (/^\)\s*[,;]?\s*$/.test(line)) {
                    complex = null;
                    continue;
                }
                const sub = parseMember(line);
                if (sub) {
                    complex.complexMembers.push(sub);
                }
                continue;
            }

            // End of type definition
            if (line === "}" || line === "};") {
                type = null;
                continue;
            }

            // Complex member start: "<[list]> <name>("  (may carry trailing spaces before '(')
            const complexStart = line.match(/^(\S+)\s+(.*?)\(\s*$/);
            if (complexStart && complexStart[1] === "[list]") {
                const member = {
                    name: fieldName(complexStart[2].trim()),
                    typeName: "[list]",
                    isReference: false,
                    isPrimitive: false,
                    isEnumConstant: false,
                    isComplex: true,
                    complexMembers: []
                };
                type.members.push(member);
                type.allCount++;
                complex = member;
                continue;
            }

            // Ordinary member line
            const member = parseMember(line);
            if (member) {
                type.members.push(member);
                type.allCount++;
            }
        }

        return dict;
    }

    /** Extract the simple field name from a (possibly qualified) identifier "Class#field". */
    function fieldName(identifier) {
        const i = identifier.lastIndexOf("#");
        return i === -1 ? identifier : identifier.substring(i + 1);
    }

    /** Parse a single non-complex member line into a member descriptor, or null. */
    function parseMember(line) {
        const body   = line.replace(/[,;]\s*$/, "").trim();
        const tokens = body.split(/\s+/);
        if (tokens.length < 2) {
            return null; // e.g. a primitive type descriptor line — never used for object expansion
        }

        const typeToken = tokens[0];

        if (typeToken === "enum") {
            return {
                name: tokens[tokens.length - 1],
                typeName: "enum",
                isReference: false,
                isPrimitive: false,
                isEnumConstant: true,
                isComplex: false
            };
        }

        const name        = fieldName(tokens[tokens.length - 1]);
        const isPrimitive  = PRIMITIVE_TYPES.has(typeToken);
        // "[char]" / "[byte]" are inline variable-length primitives, not object references.
        const isInline     = typeToken === "[char]" || typeToken === "[byte]";
        const isReference  = !isPrimitive && !isInline;

        return {
            name: name,
            typeName: typeToken,
            isReference: isReference,
            isPrimitive: isPrimitive,
            isEnumConstant: false,
            isComplex: false
        };
    }

    // ── StorageView.Default ──────────────────────────────────────────────

    function asList(listOrArray) {
        return Array.isArray(listOrArray) ? listOrArray : [listOrArray];
    }

    class Default {
        constructor(client) {
            this._client = client;
            this._dict   = null;
        }

        async loadDictionary() {
            this._dict = parseTypeDictionary(await this._client.getDictionary());
        }

        _typeEntry(typeId) {
            let entry = this._dict ? this._dict[typeId] : null;
            if (entry == null) {
                throw new Error("Missing type description for typeId " + typeId);
            }
            return entry;
        }

        resolveTypeName(typeId) {
            return this._typeEntry(typeId).typeName;
        }

        allMembersCount(typeId) {
            return this._typeEntry(typeId).allCount;
        }

        /** Instance members used for indexing — enum constants filtered out (getTypeMembers). */
        _typeMembers(typeId) {
            return this._typeEntry(typeId).members.filter(m => !m.isEnumConstant);
        }

        // StorageView.root()
        async root() {
            const rootDesc = await this._client.getRoot();
            const objectId = rootDesc.objectId;

            if (objectId != null && objectId !== "0" && Number(objectId) > 0) {
                const objectDesc = await this._client.getObject(objectId, {
                    valueLength: MAX_VALUE_LENGTH
                });
                return this._createElementFromReference(rootDesc.name, objectDesc);
            }
            // special case for a not-yet-set root
            return valueElement(rootDesc.name, "NOT YET DEFINED", null);
        }

        // StorageView.members(StorageViewObject)
        async members(parent) {
            const objectId   = parent.objectId;
            const objectDesc = await this._client.getObject(objectId, {
                valueLength: MAX_VALUE_LENGTH,
                references: true,
                variableLength: 0
            });

            const typeMembers = this._typeMembers(objectDesc.typeId);
            const cursor      = { refs: objectDesc.references || [], i: 0 };
            const data        = objectDesc.data || [];
            const length      = parseInt(objectDesc.length, 10);

            const members = [];
            let index = 0;
            for (; index < length; index++) {
                const member = typeMembers[index];
                members.push(this._createElementFromData(member.name, cursor, member, data[index]));
            }

            const varLengthArray = objectDesc.variableLength;
            const varLength = varLengthArray && varLengthArray.length === 1
                ? parseInt(varLengthArray[0], 10)
                : 0;
            if (varLength > 0) {
                if (members.length === 0) {
                    const variable = await this.variableMembers(parent, objectId, 0, varLength);
                    members.push(...variable);
                } else {
                    const member = typeMembers[index];
                    members.push(rangeElement(this, member.name, objectId, 0, varLength));
                }
            }

            return members;
        }

        // StorageView.variableMembers(...)
        async variableMembers(parent, objectId, offset, length) {
            if (length > ELEMENT_RANGE_MAX_LENGTH) {
                return this._ranges(objectId, offset, length, ELEMENT_RANGE_MAX_LENGTH, ELEMENT_RANGE_MAX_LENGTH);
            }

            const objectDesc = await this._client.getObject(objectId, {
                valueLength: MAX_VALUE_LENGTH,
                references: true,
                fixedLength: 0,
                variableOffset: offset,
                variableLength: length
            });

            const typeMembers  = this._typeMembers(objectDesc.typeId);
            const memberOffset = parseInt(objectDesc.length, 10);
            const varMember    = typeMembers[memberOffset];
            const cursor       = { refs: objectDesc.references || [], i: 0 };
            const dataList     = asList(objectDesc.data[0]);
            const elemMembers  = varMember.complexMembers;

            const members = [];
            if (elemMembers.length === 1) {
                const elemMember = elemMembers[0];
                let index = offset;
                if (elemMember.isReference) {
                    for (const dataElem of dataList) {
                        members.push(this._createElementFromData("[" + (index++) + "]", cursor, elemMember, dataElem));
                    }
                } else {
                    for (const dataElem of dataList) {
                        members.push(valueElement(
                            "[" + (index++) + "]",
                            renderValue(String(dataElem), elemMember.typeName),
                            elemMember.typeName
                        ));
                    }
                }
            } else {
                let index = offset;
                for (const dataElem of dataList) {
                    const subMembers   = [];
                    const dataElemList = asList(dataElem);
                    let subIndex = 0;
                    for (const elemMember of elemMembers) {
                        const subDataElem = dataElemList[subIndex++];
                        if (elemMember.isReference) {
                            subMembers.push(this._createElementFromData(elemMember.name, cursor, elemMember, subDataElem));
                        } else {
                            subMembers.push(valueElement(
                                elemMember.name,
                                renderValue(String(subDataElem), elemMember.typeName),
                                elemMember.typeName
                            ));
                        }
                    }
                    members.push(complexRangeEntryElement("[" + (index++) + "]", "", subMembers));
                }
            }

            return members;
        }

        // StorageView.ranges(...)
        _ranges(objectId, offset, length, range, maxRange) {
            const nextRange = range * maxRange;
            if (length > nextRange) {
                return this._ranges(objectId, offset, length, nextRange, maxRange);
            }

            const ranges = [];
            for (let i = 0; i < length; i += range) {
                const rangeOffset = i + offset;
                const rangeLength = Math.min(range, length - i);
                const rangeEnd    = rangeOffset + rangeLength - 1;
                const name        = "[" + rangeOffset + ".." + rangeEnd + "]";
                ranges.push(rangeElement(this, name, objectId, rangeOffset, rangeLength));
            }
            return ranges;
        }

        // StorageView.createElement(parent, name, references, member, data)
        _createElementFromData(name, cursor, member, data) {
            let consumed  = false;
            let reference = null;
            if (cursor.i < cursor.refs.length) {
                reference = cursor.refs[cursor.i++];
                consumed  = true;
            }
            if (consumed && reference != null) {
                return this._createElementFromReference(name, reference);
            }

            const dataString = member.isReference
                ? "null"
                : renderValue(String(data), member.typeName);
            return valueElement(name, dataString, member.typeName);
        }

        // StorageView.createElement(parent, name, reference)
        _createElementFromReference(name, reference) {
            const typeName = this.resolveTypeName(reference.typeId);
            if (reference.simplified) {
                const value = renderValue(String(reference.data[0]), typeName);
                return objectSimpleElement(this, name, value, typeName, reference.objectId);
            }
            return objectComplexElement(this, name, null, typeName, reference.objectId, reference.typeId);
        }
    }

    return Object.freeze({
        New: (client) => new Default(client),
        // exposed for the UI (column rendering mirrors StorageViewTreeGridBuilder)
        simpleTypeName: simpleTypeName,
        MAX_VALUE_LENGTH: MAX_VALUE_LENGTH
    });
})();
