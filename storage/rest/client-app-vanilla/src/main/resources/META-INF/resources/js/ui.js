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
     * @returns {HTMLElement}
     */
    function renderObjectTree(obj, typeDictionary, rootName, loadObject) {
        const container = el("div", "tree-view");
        const table = document.createElement("table");
        table.className = "tree-table";

        const thead = el("thead");
        const headRow = el("tr");
        for (const label of ["Name", "Type", "Value"]) {
            headRow.appendChild(el("th", null, label));
        }
        thead.appendChild(headRow);
        table.appendChild(thead);

        const tbody = el("tbody");
        table.appendChild(tbody);

        // Render root node
        const typeName = resolveTypeName(obj.typeId, typeDictionary);
        const rootRow = createTreeRow(rootName, simpleName(typeName), "", 0, true, false);
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
    function createTreeRow(name, typeName, value, depth, expandable, isRef) {
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

        // Type cell
        tr.appendChild(el("td", "field-type", typeName || ""));

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

        if (!data || data.length === 0) {
            if (varLength > 0) {
                // Pure collection — load variable members
                addVariableChildren(tbody, parentRow, obj, typeDictionary, loadObject, depth, varLength);
            }
            return;
        }

        const insertAfter = parentRow;

        for (let i = 0; i < data.length; i++) {
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
                const displayValue = simpleName(refTypeName) + " #" + ref.objectId;
                const row = createTreeRow(fieldName, fieldType, displayValue, depth, true, true);
                insertRowAfterLastChild(tbody, parentRow, row.tr, depth);

                setupLazyExpand(tbody, row, ref.objectId, typeDictionary, loadObject, depth);
            } else if (isArray) {
                // Inline array of values
                const row = createTreeRow(fieldName, fieldType, "[" + value.length + " elements]", depth, value.length > 0, false);
                insertRowAfterLastChild(tbody, parentRow, row.tr, depth);

                if (value.length > 0) {
                    setupArrayExpand(tbody, row, value, fieldType, typeDictionary, loadObject, depth);
                }
            } else {
                // Primitive value
                const displayValue = value === null || value === undefined ? "null" : String(value);
                const row = createTreeRow(fieldName, fieldType, displayValue, depth, false, false);
                insertRowAfterLastChild(tbody, parentRow, row.tr, depth);
            }
        }

        // Variable length members (e.g. collection elements after fixed fields)
        if (varLength > 0 && data.length > 0) {
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

            const row = createTreeRow(rangeLabel, varLength + " elements", "", depth, true, false);
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
                loadingCell.colSpan = 3;
                loadingCell.innerHTML = '<span class="tree-indent" style="padding-left:' + ((depth + 1) * 20) + 'px"></span><span class="spinner"></span> Loading...';
                loadingRow.appendChild(loadingCell);
                loadingRow.dataset.depth = depth + 1;
                insertRowAfterLastChild(tbody, row.tr, loadingRow, depth + 1);

                try {
                    const childObj = await loadObject(objectId);
                    // Remove loading row
                    loadingRow.remove();
                    addObjectChildren(tbody, row.tr, childObj, typeDictionary, loadObject, depth + 1);
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
                    loadingCell.colSpan = 3;
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
                                    const displayValue = simpleName(refTypeName) + " #" + ref.objectId;
                                    const childRow = createTreeRow("[" + idx + "]", simpleName(refTypeName), displayValue, depth + 1, true, true);
                                    insertRowAfterLastChild(tbody, row.tr, childRow.tr, depth + 1);
                                    setupLazyExpand(tbody, childRow, ref.objectId, typeDictionary, loadObject, depth + 1);
                                } else {
                                    const displayValue = val === null || val === undefined ? "null" : String(val);
                                    const childRow = createTreeRow("[" + idx + "]", "", displayValue, depth + 1, false, false);
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

        // Summary cards
        const grid = el("div", "stats-grid");

        const cards = [
            { label: "Created", value: stats.creationTime || "–" },
            { label: "Total Data Size", value: formatBytes(stats.totalDataLength) },
            { label: "Total Files", value: formatNumber(stats.fileCount) },
            { label: "Live Data Size", value: formatBytes(stats.liveDataLength) },
        ];

        for (const card of cards) {
            const c = el("div", "stat-card");
            c.appendChild(el("div", "stat-label", card.label));
            c.appendChild(el("div", "stat-value", card.value));
            grid.appendChild(c);
        }
        container.appendChild(grid);

        // Channel files table
        if (stats.channelStatistics && stats.channelStatistics.length > 0) {
            container.appendChild(el("h3", null, "Channel Statistics"));
            const table = document.createElement("table");
            table.className = "channel-table";

            const thead = el("thead");
            const headRow = el("tr");
            for (const h of ["Channel", "Files", "Data Size"]) {
                headRow.appendChild(el("th", null, h));
            }
            thead.appendChild(headRow);
            table.appendChild(thead);

            const tbodyEl = el("tbody");
            for (const ch of stats.channelStatistics) {
                const row = el("tr");
                row.appendChild(el("td", null, String(ch.channelIndex !== undefined ? ch.channelIndex : "–")));
                row.appendChild(el("td", null, formatNumber(ch.fileCount)));
                row.appendChild(el("td", null, formatBytes(ch.totalDataLength)));
                tbodyEl.appendChild(row);
            }
            table.appendChild(tbodyEl);
            container.appendChild(table);
        }

        return container;
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
                currentTypeId = typeMatch[1];
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

    // Public API
    return Object.freeze({
        renderObjectTree,
        renderStatistics,
        renderDictionary,
        parseTypeDictionary,
        renderBreadcrumb,
        simpleName,
    });
})();
