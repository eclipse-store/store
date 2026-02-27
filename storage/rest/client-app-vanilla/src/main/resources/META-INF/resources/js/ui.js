/**
 * EclipseStore Vanilla Viewer — UI rendering helpers
 *
 * Pure DOM manipulation. No templating library.
 * Renders a tree-based object explorer similar to the original Vaadin client.
 */
const UI = (() => {
    "use strict";

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Create an element with optional className and textContent. */
    function el(tag, className, text) {
        const e = document.createElement(tag);
        if (className) e.className = className;
        if (text !== undefined) e.textContent = text;
        return e;
    }

    /** Format bytes into human-readable string. */
    function formatBytes(bytes) {
        if (bytes === 0 || bytes === undefined || bytes === null) return "0 B";
        const num = Number(bytes);
        if (isNaN(num)) return String(bytes);
        const units = ["B", "KB", "MB", "GB", "TB"];
        const i = Math.min(Math.floor(Math.log(num) / Math.log(1024)), units.length - 1);
        return (num / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + " " + units[i];
    }

    /** Format a number with thousands separators. */
    function formatNumber(n) {
        if (n === undefined || n === null) return "–";
        return Number(n).toLocaleString();
    }

    /** Extract simple class name from a fully qualified Java type name. */
    function simpleName(fqn) {
        if (!fqn) return "?";
        const idx = fqn.lastIndexOf(".");
        return idx >= 0 ? fqn.substring(idx + 1) : fqn;
    }

    // ── Tree-based Object View ──────────────────────────────────────────

    /**
     * Render the root object as an expandable tree.
     *
     * @param {Object} obj — ViewerObjectDescription from the REST API
     * @param {Object} typeDictionary — parsed type dictionary
     * @param {string} rootName — display name for the root node
     * @param {function} loadObject — async callback(oid) → ViewerObjectDescription
     * @param {function} [onRowSelect] — optional callback(rowMeta) when a row is clicked
     * @returns {HTMLElement}
     */
    function renderObjectTree(obj, typeDictionary, rootName, loadObject, onRowSelect) {
        const container = el("div", "tree-view");
        const table = document.createElement("table");
        table.className = "tree-table";

        const thead = el("thead");
        const headRow = el("tr");
        for (const label of ["Name", "Value", "Type", "Object ID"]) {
            headRow.appendChild(el("th", null, label));
        }
        thead.appendChild(headRow);
        table.appendChild(thead);

        const tbody = el("tbody");
        table.appendChild(tbody);

        // Row selection via event delegation on tbody
        let selectedRow = null;
        tbody.addEventListener("click", (e) => {
            const tr = e.target.closest("tr");
            if (!tr || !tr._rowMeta) return;
            // Ignore clicks on toggle arrows (handled by expand/collapse)
            if (e.target.classList.contains("tree-toggle")) return;
            if (selectedRow) selectedRow.classList.remove("selected");
            tr.classList.add("selected");
            selectedRow = tr;
            if (onRowSelect) onRowSelect(tr._rowMeta);
        });

        // Render root node
        const typeName = resolveTypeName(obj.typeId, typeDictionary);
        const rootRow = createTreeRow(rootName, simpleName(typeName), "", 0, true, false, obj.objectId);
        rootRow.tr._rowMeta = {
            name: rootName,
            typeName: simpleName(typeName),
            value: "",
            objectId: obj.objectId,
            hasMembers: true,
            obj: obj,
        };
        tbody.appendChild(rootRow.tr);

        // Expand root immediately — add its children
        rootRow.setExpanded(true);
        addObjectChildren(tbody, rootRow.tr, obj, typeDictionary, loadObject, 1);

        container.appendChild(table);
        return container;
    }

    /**
     * Create a single tree row.
     * @returns {{ tr: HTMLElement, setExpanded: function, childContainer: string }}
     */
    function createTreeRow(name, typeName, value, depth, expandable, isRef, objectId) {
        const tr = el("tr", "tree-row");
        tr.dataset.depth = depth;
        tr.dataset.expanded = "false";

        // Name cell with indentation and expand/collapse toggle
        const nameCell = el("td", "tree-name-cell");
        const indent = el("span", "tree-indent");
        indent.style.paddingLeft = (depth * 20) + "px";
        nameCell.appendChild(indent);

        if (expandable) {
            const toggle = el("span", "tree-toggle", "▸");
            toggle.dataset.state = "collapsed";
            nameCell.appendChild(toggle);
        } else {
            nameCell.appendChild(el("span", "tree-toggle-placeholder", " "));
        }

        nameCell.appendChild(el("span", "field-name", name));
        tr.appendChild(nameCell);

        // Value cell
        const valueCell = el("td", "field-value");
        if (isRef && value) {
            const link = el("span", "value-ref", value);
            valueCell.appendChild(link);
        } else if (value === "null" || value === null) {
            valueCell.appendChild(el("span", "value-null", "null"));
        } else if (value === "true" || value === "false") {
            valueCell.appendChild(el("span", "value-boolean", value));
        } else if (value !== "" && value !== undefined && !isNaN(value) && String(value).length < 20) {
            valueCell.appendChild(el("span", "value-number", String(value)));
        } else if (value !== undefined && value !== "") {
            valueCell.appendChild(el("span", "value-string", String(value)));
        }
        tr.appendChild(valueCell);

        // Type cell
        tr.appendChild(el("td", "field-type", typeName || ""));

        // Object ID cell
        tr.appendChild(el("td", "field-oid", objectId || ""));

        // Expand/collapse logic
        let expanded = false;
        const setExpanded = (state) => {
            expanded = state;
            tr.dataset.expanded = String(state);
            const toggle = tr.querySelector(".tree-toggle");
            if (toggle) {
                toggle.textContent = state ? "▾" : "▸";
                toggle.dataset.state = state ? "expanded" : "collapsed";
            }
        };

        return { tr, setExpanded, expanded: () => expanded };
    }

    /**
     * Add children rows for a loaded object description.
     */
    function addObjectChildren(tbody, parentRow, obj, typeDictionary, loadObject, depth) {
        const data = obj.data;
        const references = obj.references || [];
        const members = resolveMembers(obj.typeId, typeDictionary);
        const varLengthArray = obj.variableLength;
        const varLength = varLengthArray && varLengthArray.length === 1
            ? parseInt(varLengthArray[0], 10) : 0;

        // Number of fixed-size members (from obj.length), not data.length
        const fixedLength = obj.length !== undefined && obj.length !== null
            ? parseInt(obj.length, 10) : (data ? data.length : 0);

        if (!data || fixedLength === 0) {
            if (varLength > 0) {
                // Pure collection — load variable members directly as children
                addVariableChildren(tbody, parentRow, obj, typeDictionary, loadObject, depth, varLength);
            }
            return;
        }

        for (let i = 0; i < fixedLength && i < data.length; i++) {
            const memberInfo = members[i] || {};
            const fieldName = memberInfo.name || ("field_" + i);
            const fieldType = memberInfo.type ? simpleName(memberInfo.type) : "";
            const ref = references[i] || null;
            const value = data[i];

            const isReference = ref && ref.objectId && ref.objectId !== "0";
            const isArray = Array.isArray(value);

            if (isReference) {
                // Reference to another object — expandable on click
                const refTypeName = resolveTypeName(ref.typeId, typeDictionary);
                const refSimpleName = simpleName(refTypeName);
                // Mimic Vaadin: if simplified, show data[0] as value; otherwise show (typeName)
                const displayValue = ref.simplified && ref.data && ref.data.length > 0
                    ? String(ref.data[0])
                    : "(" + refSimpleName + ")";
                const row = createTreeRow(fieldName, refSimpleName, displayValue, depth, !ref.simplified, true, ref.objectId);
                row.tr._rowMeta = {
                    name: fieldName, typeName: refSimpleName, value: displayValue,
                    objectId: ref.objectId, hasMembers: !ref.simplified, ref: ref,
                };
                insertRowAfterLastChild(tbody, parentRow, row.tr, depth);

                if (!ref.simplified) {
                    setupLazyExpand(tbody, row, ref.objectId, typeDictionary, loadObject, depth);
                }
            } else if (isArray) {
                // Inline array of values
                const row = createTreeRow(fieldName, fieldType, "[" + value.length + " elements]", depth, value.length > 0, false);
                row.tr._rowMeta = {
                    name: fieldName, typeName: fieldType, value: "[" + value.length + " elements]",
                    hasMembers: value.length > 0, arrayValues: value,
                };
                insertRowAfterLastChild(tbody, parentRow, row.tr, depth);

                if (value.length > 0) {
                    setupArrayExpand(tbody, row, value, fieldType, typeDictionary, loadObject, depth);
                }
            } else {
                // Primitive value
                const displayValue = value === null || value === undefined ? "null" : String(value);
                const row = createTreeRow(fieldName, fieldType, displayValue, depth, false, false);
                row.tr._rowMeta = {
                    name: fieldName, typeName: fieldType, value: displayValue, hasMembers: false,
                };
                insertRowAfterLastChild(tbody, parentRow, row.tr, depth);
            }
        }

        // Variable length members (e.g. collection elements after fixed fields)
        if (varLength > 0 && fixedLength > 0) {
            addVariableChildren(tbody, parentRow, obj, typeDictionary, loadObject, depth, varLength);
        }
    }

    /**
     * Add variable-length collection elements (e.g. List entries).
     */
    function addVariableChildren(tbody, parentRow, obj, typeDictionary, loadObject, depth, varLength) {
        // For collections, we need to load with variableOffset/variableLength
        const oid = obj.objectId;
        const batchSize = 50;
        const batches = Math.ceil(varLength / batchSize);

        for (let batch = 0; batch < batches && batch < 20; batch++) {
            const offset = batch * batchSize;
            const length = Math.min(batchSize, varLength - offset);
            const rangeLabel = varLength > batchSize
                ? "[" + offset + ".." + (offset + length - 1) + "]"
                : "elements";

            const row = createTreeRow(rangeLabel, "", "", depth, true, false);
            row.tr._rowMeta = {
                name: rangeLabel, typeName: "", value: "",
                hasMembers: true, isRange: true,
            };
            insertRowAfterLastChild(tbody, parentRow, row.tr, depth);

            setupVariableExpand(tbody, row, oid, offset, length, typeDictionary, loadObject, depth);
        }
    }

    /**
     * Set up lazy-load expand for a reference node.
     */
    function setupLazyExpand(tbody, row, objectId, typeDictionary, loadObject, depth) {
        let loaded = false;
        const toggle = row.tr.querySelector(".tree-toggle");
        const refLink = row.tr.querySelector(".value-ref");

        const doExpand = async () => {
            if (!loaded) {
                // Show loading indicator
                row.setExpanded(true);
                const loadingRow = el("tr", "tree-row loading-row");
                const loadingCell = el("td");
                loadingCell.colSpan = 4;
                loadingCell.innerHTML = '<span class="tree-indent" style="padding-left:' + ((depth + 1) * 20) + 'px"></span><span class="spinner"></span> Loading...';
                loadingRow.appendChild(loadingCell);
                loadingRow.dataset.depth = depth + 1;
                insertRowAfterLastChild(tbody, row.tr, loadingRow, depth + 1);

                try {
                    const childObj = await loadObject(objectId);
                    // Remove loading row
                    loadingRow.remove();
                    addObjectChildren(tbody, row.tr, childObj, typeDictionary, loadObject, depth + 1);
                    // Store loaded object for detail panel
                    if (row.tr._rowMeta) {
                        row.tr._rowMeta.obj = childObj;
                    }
                    loaded = true;
                } catch (err) {
                    loadingRow.querySelector("td").textContent = "Error: " + err.message;
                }
            } else {
                row.setExpanded(true);
                toggleChildVisibility(tbody, row.tr, depth, true);
            }
        };

        const doCollapse = () => {
            row.setExpanded(false);
            toggleChildVisibility(tbody, row.tr, depth, false);
        };

        if (toggle) {
            toggle.style.cursor = "pointer";
            toggle.addEventListener("click", (e) => {
                e.stopPropagation();
                if (row.tr.dataset.expanded === "true") doCollapse();
                else doExpand();
            });
        }
        if (refLink) {
            refLink.style.cursor = "pointer";
            refLink.addEventListener("click", (e) => {
                e.stopPropagation();
                if (row.tr.dataset.expanded === "true") doCollapse();
                else doExpand();
            });
        }
    }

    /**
     * Set up expand for inline array values.
     */
    function setupArrayExpand(tbody, row, values, fieldType, typeDictionary, loadObject, depth) {
        let childrenAdded = false;
        const toggle = row.tr.querySelector(".tree-toggle");

        if (toggle) {
            toggle.style.cursor = "pointer";
            toggle.addEventListener("click", (e) => {
                e.stopPropagation();
                if (row.tr.dataset.expanded === "true") {
                    row.setExpanded(false);
                    toggleChildVisibility(tbody, row.tr, depth, false);
                } else {
                    row.setExpanded(true);
                    if (!childrenAdded) {
                        for (let i = 0; i < values.length; i++) {
                            const val = values[i];
                            const childRow = createTreeRow("[" + i + "]", "", String(val), depth + 1, false, false);
                            childRow.tr._rowMeta = {
                                name: "[" + i + "]", typeName: "", value: String(val), hasMembers: false,
                            };
                            insertRowAfterLastChild(tbody, row.tr, childRow.tr, depth + 1);
                        }
                        childrenAdded = true;
                    } else {
                        toggleChildVisibility(tbody, row.tr, depth, true);
                    }
                }
            });
        }
    }

    /**
     * Set up lazy-load expand for variable-length collection ranges.
     */
    function setupVariableExpand(tbody, row, oid, offset, length, typeDictionary, loadObject, depth) {
        let loaded = false;
        const toggle = row.tr.querySelector(".tree-toggle");

        if (toggle) {
            toggle.style.cursor = "pointer";
            toggle.addEventListener("click", async (e) => {
                e.stopPropagation();
                if (row.tr.dataset.expanded === "true") {
                    row.setExpanded(false);
                    toggleChildVisibility(tbody, row.tr, depth, false);
                    return;
                }

                row.setExpanded(true);
                if (!loaded) {
                    const loadingRow = el("tr", "tree-row loading-row");
                    const loadingCell = el("td");
                    loadingCell.colSpan = 4;
                    loadingCell.innerHTML = '<span class="tree-indent" style="padding-left:' + ((depth + 1) * 20) + 'px"></span><span class="spinner"></span> Loading...';
                    loadingRow.appendChild(loadingCell);
                    loadingRow.dataset.depth = depth + 1;
                    insertRowAfterLastChild(tbody, row.tr, loadingRow, depth + 1);

                    try {
                        const childObj = await loadObject(oid, {
                            fixedLength: 0,
                            variableOffset: offset,
                            variableLength: length,
                            references: true,
                        });
                        loadingRow.remove();

                        // Variable members: data[0] is typically the array of values
                        const data = childObj.data;
                        const references = childObj.references || [];

                        if (data && data.length > 0) {
                            const items = Array.isArray(data[0]) ? data[0] : data;
                            for (let i = 0; i < items.length; i++) {
                                const val = items[i];
                                const ref = references[i] || null;
                                const isRef = ref && ref.objectId && ref.objectId !== "0";
                                const idx = offset + i;

                                if (isRef) {
                                    const refTypeName = resolveTypeName(ref.typeId, typeDictionary);
                                    const refSimpleName = simpleName(refTypeName);
                                    // Mimic Vaadin: if simplified, show data[0] as value; otherwise show (typeName)
                                    const displayValue = ref.simplified && ref.data && ref.data.length > 0
                                        ? String(ref.data[0])
                                        : "(" + refSimpleName + ")";
                                    const childRow = createTreeRow("[" + idx + "]", refSimpleName, displayValue, depth + 1, !ref.simplified, true, ref.objectId);
                                    childRow.tr._rowMeta = {
                                        name: "[" + idx + "]", typeName: refSimpleName, value: displayValue,
                                        objectId: ref.objectId, hasMembers: !ref.simplified, ref: ref,
                                    };
                                    insertRowAfterLastChild(tbody, row.tr, childRow.tr, depth + 1);
                                    if (!ref.simplified) {
                                        setupLazyExpand(tbody, childRow, ref.objectId, typeDictionary, loadObject, depth + 1);
                                    }
                                } else {
                                    const displayValue = val === null || val === undefined ? "null" : String(val);
                                    const childRow = createTreeRow("[" + idx + "]", "", displayValue, depth + 1, false, false);
                                    childRow.tr._rowMeta = {
                                        name: "[" + idx + "]", typeName: "", value: displayValue, hasMembers: false,
                                    };
                                    insertRowAfterLastChild(tbody, row.tr, childRow.tr, depth + 1);
                                }
                            }
                        }
                        loaded = true;
                    } catch (err) {
                        loadingRow.querySelector("td").textContent = "Error: " + err.message;
                    }
                } else {
                    toggleChildVisibility(tbody, row.tr, depth, true);
                }
            });
        }
    }

    /**
     * Insert a row after the last child of parentRow at the given depth or deeper.
     */
    function insertRowAfterLastChild(tbody, parentRow, newRow, depth) {
        let sibling = parentRow.nextElementSibling;
        while (sibling && parseInt(sibling.dataset.depth, 10) >= depth) {
            sibling = sibling.nextElementSibling;
        }
        if (sibling) {
            tbody.insertBefore(newRow, sibling);
        } else {
            tbody.appendChild(newRow);
        }
    }

    /**
     * Show or hide all children of a row (recursively).
     */
    function toggleChildVisibility(tbody, parentRow, parentDepth, visible) {
        let sibling = parentRow.nextElementSibling;
        while (sibling) {
            const sibDepth = parseInt(sibling.dataset.depth, 10);
            if (sibDepth <= parentDepth) break;

            if (visible) {
                // Only show direct children; deeper ones stay hidden unless their parent is expanded
                if (sibDepth === parentDepth + 1) {
                    sibling.hidden = false;
                } else {
                    // Check if the parent of this row is expanded
                    const immediateParent = findParentRow(tbody, sibling, sibDepth);
                    if (immediateParent && immediateParent.dataset.expanded === "true" && !immediateParent.hidden) {
                        sibling.hidden = false;
                    }
                }
            } else {
                sibling.hidden = true;
            }
            sibling = sibling.nextElementSibling;
        }
    }

    /**
     * Find the nearest parent row (one depth level up).
     */
    function findParentRow(tbody, row, rowDepth) {
        let prev = row.previousElementSibling;
        while (prev) {
            const d = parseInt(prev.dataset.depth, 10);
            if (d === rowDepth - 1) return prev;
            if (d < rowDepth - 1) return null;
            prev = prev.previousElementSibling;
        }
        return null;
    }

    /** Resolve a typeId to a fully qualified type name using the dictionary. */
    function resolveTypeName(typeId, dictionary) {
        if (!dictionary || !typeId) return typeId || "?";
        const entry = dictionary[typeId];
        return entry ? entry.typeName : ("typeId:" + typeId);
    }

    /**
     * Resolve member (field) definitions from the type dictionary.
     * Returns an array of { name, type } objects.
     */
    function resolveMembers(typeId, dictionary) {
        if (!dictionary || !typeId) return [];
        const entry = dictionary[typeId];
        if (!entry || !entry.members) return [];
        return entry.members;
    }

    // ── Statistics View ─────────────────────────────────────────────────

    /**
     * Render file statistics.
     * @param {Object} stats — from /maintenance/filesStatistics
     * @returns {HTMLElement}
     */
    function renderStatistics(stats) {
        const container = el("div");

        const table = document.createElement("table");
        table.className = "tree-table";

        const thead = el("thead");
        const headRow = el("tr");
        headRow.appendChild(el("th", null, "Name"));
        headRow.appendChild(el("th", null, "Value"));
        thead.appendChild(headRow);
        table.appendChild(thead);

        const tbody = el("tbody");
        table.appendChild(tbody);

        // Top-level items (always visible)
        addStatRow(tbody, "Creation Time", stats.creationTime || "–", 0);
        addStatRow(tbody, "File Count", formatNumber(stats.fileCount), 0);
        addStatRow(tbody, "Live Data Size", formatBytes(stats.liveDataLength), 0);
        addStatRow(tbody, "Total Data Size", formatBytes(stats.totalDataLength), 0);

        // Channels — REST API returns a HashMap (object with integer keys), not an array
        const channelMap = stats.channelStatistics;
        if (channelMap && typeof channelMap === "object") {
            const channelEntries = Object.entries(channelMap)
                .map(([key, ch]) => ({ index: ch.channelIndex !== undefined ? ch.channelIndex : parseInt(key, 10), data: ch }))
                .sort((a, b) => a.index - b.index);

            if (channelEntries.length > 0) {
                const channelsRow = addStatToggleRow(tbody, "Channels", "", 0);

                for (const entry of channelEntries) {
                    const ch = entry.data;

                    // Channel N — child of Channels
                    const channelRow = addStatToggleRow(tbody, "Channel " + entry.index, "", 1);
                    channelRow.tr.style.display = "none";
                    channelsRow.childRows.push(channelRow);

                    // Channel stats — children of Channel N
                    addStatRow(tbody, "File Count", formatNumber(ch.fileCount), 2, channelRow);
                    addStatRow(tbody, "Live Data Size", formatBytes(ch.liveDataLength), 2, channelRow);
                    addStatRow(tbody, "Total Data Size", formatBytes(ch.totalDataLength), 2, channelRow);

                    // Files group within this channel
                    if (ch.files && ch.files.length > 0) {
                        const filesRow = addStatToggleRow(tbody, "Files", "", 2);
                        filesRow.tr.style.display = "none";
                        channelRow.childRows.push(filesRow);

                        for (const file of ch.files) {
                            // Individual file — child of Files
                            const fileRow = addStatToggleRow(tbody, "File " + file.fileNumber, file.file || "", 3);
                            fileRow.tr.style.display = "none";
                            filesRow.childRows.push(fileRow);

                            // File stats — children of File N
                            addStatRow(tbody, "Live Data Size", formatBytes(file.liveDataLength), 4, fileRow);
                            addStatRow(tbody, "Total Data Size", formatBytes(file.totalDataLength), 4, fileRow);
                        }
                    }
                }
            }
        }

        container.appendChild(table);
        return container;
    }

    /**
     * Add a simple (non-expandable) statistics row.
     */
    function addStatRow(tbody, name, value, depth, parentToggle) {
        const tr = el("tr", "tree-row");
        tr.dataset.depth = depth;

        const nameCell = el("td", "tree-name-cell");
        const indent = el("span", "tree-indent");
        indent.style.paddingLeft = (depth * 20) + "px";
        nameCell.appendChild(indent);
        nameCell.appendChild(el("span", "tree-toggle-placeholder", " "));
        nameCell.appendChild(el("span", "field-name", name));
        tr.appendChild(nameCell);

        tr.appendChild(el("td", "field-value", value));
        tbody.appendChild(tr);

        // Hide by default if it has a parent toggle
        if (parentToggle) {
            tr.style.display = "none";
            if (!parentToggle.childRows) parentToggle.childRows = [];
            parentToggle.childRows.push({ tr: tr });
        }

        return { tr: tr };
    }

    /**
     * Add an expandable statistics row (with toggle arrow).
     */
    function addStatToggleRow(tbody, name, value, depth) {
        const tr = el("tr", "tree-row");
        tr.dataset.depth = depth;
        tr.dataset.expanded = "false";

        const nameCell = el("td", "tree-name-cell");
        const indent = el("span", "tree-indent");
        indent.style.paddingLeft = (depth * 20) + "px";
        nameCell.appendChild(indent);

        const toggle = el("span", "tree-toggle", "▸");
        toggle.dataset.state = "collapsed";
        toggle.style.cursor = "pointer";
        nameCell.appendChild(toggle);
        nameCell.appendChild(el("span", "field-name", name));
        tr.appendChild(nameCell);

        tr.appendChild(el("td", "field-value", value));
        tbody.appendChild(tr);

        const rowObj = { tr: tr, childRows: [] };

        toggle.addEventListener("click", () => {
            const isExpanded = tr.dataset.expanded === "true";
            tr.dataset.expanded = isExpanded ? "false" : "true";
            toggle.textContent = isExpanded ? "▸" : "▾";
            toggle.dataset.state = isExpanded ? "collapsed" : "expanded";

            // Show/hide direct children
            setStatChildrenVisible(rowObj, !isExpanded);
        });

        return rowObj;
    }

    /**
     * Recursively show/hide stat tree children.
     */
    function setStatChildrenVisible(parentObj, visible) {
        if (!parentObj.childRows) return;
        for (const child of parentObj.childRows) {
            child.tr.style.display = visible ? "" : "none";
            // If hiding, also collapse and hide grandchildren
            if (!visible && child.childRows && child.childRows.length > 0) {
                child.tr.dataset.expanded = "false";
                const toggle = child.tr.querySelector(".tree-toggle");
                if (toggle) {
                    toggle.textContent = "▸";
                    toggle.dataset.state = "collapsed";
                }
                setStatChildrenVisible(child, false);
            }
        }
    }

    // ── Dictionary View ─────────────────────────────────────────────────

    /**
     * Render the type dictionary as a preformatted text block.
     * @param {string} raw — raw type dictionary text
     * @returns {HTMLElement}
     */
    function renderDictionary(raw) {
        const container = el("div");
        const pre = el("pre", null, raw);
        container.appendChild(pre);
        return container;
    }

    /**
     * Parse the type dictionary text into a lookup map.
     * @param {string} raw — raw dictionary text
     * @returns {Object} — { [typeId]: { typeName, members: [{name, type}] } }
     */
    function parseTypeDictionary(raw) {
        const dict = {};
        if (!raw) return dict;

        const lines = raw.split("\n");
        let currentTypeId = null;
        let currentTypeName = null;
        let currentMembers = [];

        for (const line of lines) {
            const trimmed = line.trim();

            // Type definition line: e.g. "12345 java.util.ArrayList {"
            const typeMatch = trimmed.match(/^(\d+)\s+(.+?)\s*\{?\s*$/);
            if (typeMatch) {
                if (currentTypeId !== null) {
                    dict[currentTypeId] = { typeName: currentTypeName, members: currentMembers };
                }
                // Strip leading zeros so keys match the REST API typeId format
                currentTypeId = typeMatch[1].replace(/^0+/, "") || "0";
                currentTypeName = typeMatch[2];
                currentMembers = [];
                continue;
            }

            // Closing brace
            if (trimmed === "}" || trimmed === "};") {
                if (currentTypeId !== null) {
                    dict[currentTypeId] = { typeName: currentTypeName, members: currentMembers };
                    currentTypeId = null;
                }
                continue;
            }

            // Member line: e.g. "  java.lang.String name,"
            if (currentTypeId !== null && trimmed.length > 0) {
                const memberMatch = trimmed.replace(/[,;]$/, "").trim().split(/\s+/);
                if (memberMatch.length >= 2) {
                    currentMembers.push({
                        type: memberMatch[0],
                        name: memberMatch[memberMatch.length - 1],
                    });
                } else if (memberMatch.length === 1) {
                    currentMembers.push({
                        type: memberMatch[0],
                        name: memberMatch[0],
                    });
                }
            }
        }

        if (currentTypeId !== null) {
            dict[currentTypeId] = { typeName: currentTypeName, members: currentMembers };
        }

        return dict;
    }

    // ── Breadcrumb ──────────────────────────────────────────────────────

    function renderBreadcrumb(trail, onClick) {
        const nav = el("div", "breadcrumb");
        for (let i = 0; i < trail.length; i++) {
            if (i > 0) {
                nav.appendChild(el("span", "separator", " › "));
            }
            const crumb = el("span", "crumb", trail[i].label);
            const oid = trail[i].oid;
            crumb.addEventListener("click", () => onClick(oid));
            nav.appendChild(crumb);
        }
        return nav;
    }

    // ── Detail Panel ────────────────────────────────────────────────────

    /**
     * Render the detail panel content for a selected tree row.
     * Matches Vaadin behavior:
     *   - If the element has members (is an object) → show a detail tree grid with its fields
     *   - If the element is a leaf → show its value in a read-only text area
     *
     * @param {Object} meta — row metadata from _rowMeta
     * @param {Object} typeDictionary — parsed type dictionary
     * @param {function} loadObject — async callback(oid) → ViewerObjectDescription
     * @returns {HTMLElement}
     */
    function renderDetailPanel(meta, typeDictionary, loadObject) {
        const container = el("div", "detail-content");

        if (meta.hasMembers && meta.obj) {
            // Object with members — show detail tree grid with element header
            container.appendChild(renderDetailTree(meta, meta.obj, typeDictionary));
        } else if (meta.hasMembers && meta.objectId) {
            // Reference not yet loaded — load and display
            const spinner = el("div", null);
            spinner.innerHTML = '<span class="spinner"></span> Loading details…';
            container.appendChild(spinner);

            loadObject(meta.objectId).then(obj => {
                // Cache for future clicks
                meta.obj = obj;
                container.innerHTML = "";
                container.appendChild(renderDetailTree(meta, obj, typeDictionary));
            }).catch(err => {
                container.innerHTML = "";
                container.appendChild(el("div", "error-message", "Failed to load: " + err.message));
            });
        } else if (meta.arrayValues) {
            // Array — show all values
            container.appendChild(renderDetailTree(meta, { data: meta.arrayValues, typeId: null }, typeDictionary));
        } else if (meta.isRange) {
            // Range node — no detail to show
            container.appendChild(el("p", "detail-placeholder", "Expand the range to see individual elements"));
        } else {
            // Leaf value — show in text area
            const textarea = document.createElement("textarea");
            textarea.className = "detail-textarea";
            textarea.readOnly = true;
            textarea.value = meta.value || "";
            container.appendChild(textarea);
        }

        return container;
    }

    /**
     * Render a detail tree grid showing the selected element as header row
     * with its children expanded below — matching the Vaadin detail panel.
     *
     * @param {Object} meta — row metadata of the selected element
     * @param {Object} obj — loaded ViewerObjectDescription
     * @param {Object} typeDictionary — parsed type dictionary
     */
    function renderDetailTree(meta, obj, typeDictionary) {
        const table = document.createElement("table");

        const thead = el("thead");
        const headRow = el("tr");
        headRow.appendChild(el("th", null, "Name"));
        headRow.appendChild(el("th", null, "Value"));
        headRow.appendChild(el("th", null, "Type"));
        headRow.appendChild(el("th", null, "Object ID"));
        thead.appendChild(headRow);
        table.appendChild(thead);

        const tbody = el("tbody");
        table.appendChild(tbody);

        // Header row — the selected element itself (with Type & Object ID)
        const elementTypeName = obj.typeId
            ? simpleName(resolveTypeName(obj.typeId, typeDictionary))
            : (meta.typeName || "");
        const elementValue = meta.value || (elementTypeName ? "(" + elementTypeName + ")" : "");
        const elementOid = obj.objectId || meta.objectId || "";

        const headerTr = el("tr", "detail-header-row");
        headerTr.appendChild(el("td", "field-name", meta.name || ""));
        headerTr.appendChild(el("td", "field-value", elementValue));
        headerTr.appendChild(el("td", "field-type", elementTypeName));
        headerTr.appendChild(el("td", "field-oid", elementOid));
        tbody.appendChild(headerTr);

        // Children rows — fields of the object
        const data = obj.data || [];
        const references = obj.references || [];
        const members = obj.typeId ? resolveMembers(obj.typeId, typeDictionary) : [];
        const fixedLength = obj.length !== undefined && obj.length !== null
            ? parseInt(obj.length, 10) : data.length;

        for (let i = 0; i < fixedLength && i < data.length; i++) {
            const memberInfo = members[i] || {};
            const fieldName = memberInfo.name || ("[" + i + "]");
            const fieldType = memberInfo.type ? simpleName(memberInfo.type) : "";
            const ref = references[i] || null;
            const value = data[i];
            const isReference = ref && ref.objectId && ref.objectId !== "0";

            const tr = el("tr");

            if (isReference) {
                const refTypeName = resolveTypeName(ref.typeId, typeDictionary);
                const refSimpleName = simpleName(refTypeName);
                const displayValue = ref.simplified && ref.data && ref.data.length > 0
                    ? String(ref.data[0])
                    : "(" + refSimpleName + ")";
                tr.appendChild(el("td", "field-name", "  " + fieldName));
                tr.appendChild(el("td", "field-value", displayValue));
                tr.appendChild(el("td", "field-type", refSimpleName));
                tr.appendChild(el("td", "field-oid", ref.objectId));
            } else if (Array.isArray(value)) {
                tr.appendChild(el("td", "field-name", "  " + fieldName));
                tr.appendChild(el("td", "field-value", "[" + value.length + " elements]"));
                tr.appendChild(el("td", "field-type", fieldType));
                tr.appendChild(el("td", "field-oid", ""));
            } else {
                const displayValue = value === null || value === undefined ? "null" : String(value);
                tr.appendChild(el("td", "field-name", "  " + fieldName));
                tr.appendChild(el("td", "field-value", displayValue));
                tr.appendChild(el("td", "field-type", fieldType));
                tr.appendChild(el("td", "field-oid", ""));
            }

            tbody.appendChild(tr);
        }

        // Variable-length elements (for pure collections)
        const varLengthArray = obj.variableLength;
        const varLength = varLengthArray && varLengthArray.length === 1
            ? parseInt(varLengthArray[0], 10) : 0;
        if (varLength > 0 && fixedLength === 0) {
            const tr = el("tr");
            tr.appendChild(el("td", "field-name", "  elements"));
            tr.appendChild(el("td", "field-value", varLength + " elements"));
            tr.appendChild(el("td", "field-type", ""));
            tr.appendChild(el("td", "field-oid", ""));
            tbody.appendChild(tr);
        }

        return table;
    }

    // Public API
    return Object.freeze({
        renderObjectTree,
        renderDetailPanel,
        renderStatistics,
        renderDictionary,
        parseTypeDictionary,
        renderBreadcrumb,
        simpleName,
    });
})();
