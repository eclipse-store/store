/**
 * EclipseStore Vanilla Viewer — UI rendering
 *
 * Renders the same widgets the Vaadin client used:
 *   - a lazy, in-place expandable TreeGrid (Name / Value / Type / ObjectId) for the data view
 *   - a detail panel (a detail tree grid for objects, a read-only text area for leaf values)
 *   - a two-column statistics TreeGrid
 *
 * Column semantics mirror StorageViewTreeGridBuilder (elementValue / elementObjectId) and
 * StorageStatisticsComponent.
 */
const UI = (() => {
    "use strict";

    function el(tag, className, text) {
        const e = document.createElement(tag);
        if (className) e.className = className;
        if (text !== undefined) e.textContent = text;
        return e;
    }

    // ── Column specs ─────────────────────────────────────────────────────

    // elementValue(): value if non-empty, else "(SimpleType)" if a type is known, else "".
    function elementValue(element) {
        const value = element.value;
        if (value != null && value.length > 0) {
            return value;
        }
        const typeName = StorageView.simpleTypeName(element.typeName);
        return typeName != null && typeName.length > 0 ? "(" + typeName + ")" : "";
    }

    // elementObjectId(): objectId for StorageViewObject elements, "" otherwise.
    function elementObjectId(element) {
        return element.isObject ? (element.objectId || "") : "";
    }

    const DATA_COLUMNS = [
        { header: "Name",     hierarchical: true, sortable: true, width: "26%", get: e => e.name },
        { header: "Value",    width: "30%", get: elementValue },
        { header: "Type",     width: "22%", get: e => StorageView.simpleTypeName(e.typeName) },
        { header: "ObjectId", width: "22%", get: elementObjectId }
    ];

    const STATS_COLUMNS = [
        { header: "Name",  hierarchical: true, width: "35%", get: e => e.name },
        { header: "Value", width: "65%", get: e => e.value != null ? e.value : "" }
    ];

    // ── Generic lazy tree grid ───────────────────────────────────────────

    /**
     * Build a lazy expandable tree grid.
     *
     * @param roots       array of root elements
     * @param columns     column specs
     * @param onSelect    optional callback(element) invoked on row selection
     * @param expandRoots if true, roots are expanded immediately (used by the detail panel)
     * @returns {HTMLElement} the grid container
     */
    // Sorting on the Name column (mirrors StorageViewTreeGridBuilder.NameComparator):
    // range names "[n]" / "[n..m]" compare numerically, everything else lexicographically.
    function rangeIndex(name) {
        if (name.length > 2 && name.charAt(0) === "[" && name.charAt(name.length - 1) === "]") {
            const dots = name.indexOf("..");
            if (name.length > 4 && dots !== -1) {
                const n = parseInt(name.substring(1, dots), 10);
                if (!isNaN(n)) return n;
            } else {
                const n = parseInt(name.substring(1, name.length - 1), 10);
                if (!isNaN(n)) return n;
            }
        }
        return null;
    }

    function nameCompare(a, b) {
        const n1 = a.name, n2 = b.name;
        const r1 = rangeIndex(n1), r2 = rangeIndex(n2);
        if (r1 !== null && r2 !== null) {
            return r1 - r2;
        }
        return n1 < n2 ? -1 : n1 > n2 ? 1 : 0;
    }

    function buildTreeGrid(roots, columns, onSelect, expandRoots) {
        const container = el("div", "tree-grid");
        const table = el("table", "tree-table");

        const colgroup = el("colgroup");
        for (const col of columns) {
            const c = document.createElement("col");
            if (col.width) c.style.width = col.width;
            colgroup.appendChild(c);
        }
        table.appendChild(colgroup);

        // Per-grid state so a detail grid never shares expansion/selection with the main grid.
        const state = { sortDir: 0 };      // 0 = none, 1 = ascending, -1 = descending
        const expanded = new Set();        // element objects currently expanded in THIS grid
        let selectedElement = null;

        const thead = el("thead");
        const headRow = el("tr");
        for (const col of columns) {
            const th = el("th");
            if (col.sortable) {
                th.className = "sortable";
                th.appendChild(document.createTextNode(col.header + " "));
                const indicator = el("span", "sort-indicator", "⇕");
                th.appendChild(indicator);
                th.addEventListener("click", () => {
                    state.sortDir = state.sortDir === 0 ? 1 : state.sortDir === 1 ? -1 : 0;
                    indicator.textContent = state.sortDir === 1 ? "↑" : state.sortDir === -1 ? "↓" : "⇕";
                    indicator.classList.toggle("active", state.sortDir !== 0);
                    rebuild();
                });
            } else {
                th.textContent = col.header;
            }
            headRow.appendChild(th);
        }
        thead.appendChild(headRow);
        table.appendChild(thead);

        const tbody = el("tbody");
        table.appendChild(tbody);

        if (onSelect) {
            tbody.addEventListener("click", (event) => {
                if (event.target.classList.contains("tree-toggle")) {
                    return;
                }
                const tr = event.target.closest("tr");
                if (!tr || !tr._element) {
                    return;
                }
                selectElement(tr._element);
                onSelect(tr._element);
            });
        }

        function selectElement(element) {
            selectedElement = element;
            for (const tr of tbody.children) {
                tr.classList.toggle("selected", tr._element === element);
            }
        }

        function sortSiblings(list) {
            if (!state.sortDir) {
                return list;
            }
            const sorted = [...list].sort(nameCompare);
            if (state.sortDir < 0) {
                sorted.reverse();
            }
            return sorted;
        }

        function createRow(element, depth) {
            const tr = el("tr", "tree-row");
            tr._element = element;
            tr.dataset.depth = depth;   // tree level, exposed for tests (mirrors Vaadin's --_level)
            if (element === selectedElement) {
                tr.classList.add("selected");
            }

            for (const col of columns) {
                if (col.hierarchical) {
                    const cell = el("td", "tree-name-cell");
                    const indent = el("span", "tree-indent");
                    indent.style.paddingLeft = (depth * 18) + "px";
                    cell.appendChild(indent);

                    if (element.hasMembers) {
                        const isOpen = expanded.has(element);
                        const toggle = el("span", "tree-toggle", isOpen ? "▾" : "▸");
                        // Keyboard-accessible expand/collapse control.
                        toggle.setAttribute("role", "button");
                        toggle.setAttribute("tabindex", "0");
                        toggle.setAttribute("aria-expanded", String(isOpen));
                        const toggleAction = (event) => {
                            event.stopPropagation();
                            event.preventDefault();
                            if (expanded.has(element)) {
                                expanded.delete(element);
                            } else {
                                expanded.add(element);
                            }
                            // Member loading happens in collectRows(), under rebuild()'s error handling.
                            rebuild();
                        };
                        toggle.addEventListener("click", toggleAction);
                        toggle.addEventListener("keydown", (event) => {
                            if (event.key === "Enter" || event.key === " ") {
                                toggleAction(event);
                            }
                        });
                        cell.appendChild(toggle);
                    } else {
                        cell.appendChild(el("span", "tree-toggle-placeholder"));
                    }

                    cell.appendChild(el("span", "field-name", col.get(element)));
                    tr.appendChild(cell);
                } else {
                    tr.appendChild(el("td", col.className || null, col.get(element)));
                }
            }

            return tr;
        }

        async function collectRows(elements, depth, out) {
            for (const element of sortSiblings(elements)) {
                out.push(createRow(element, depth));
                if (expanded.has(element) && element.hasMembers) {
                    const children = await element.loadMembers();
                    await collectRows(children, depth + 1, out);
                }
            }
        }

        function showError(message) {
            const tr = el("tr");
            const td = el("td", "error-message", "Error: " + message);
            td.colSpan = columns.length;
            tr.appendChild(td);
            tbody.replaceChildren(tr);
        }

        async function rebuild() {
            try {
                const rows = [];
                await collectRows(roots, 0, rows);
                tbody.replaceChildren(...rows);
            } catch (err) {
                showError(err && err.message ? err.message : String(err));
            }
        }

        if (expandRoots) {
            for (const root of roots) {
                if (root.hasMembers) {
                    expanded.add(root);
                }
            }
        }
        rebuild();

        container.appendChild(table);
        return container;
    }

    // ── Data view ────────────────────────────────────────────────────────

    function renderDataTree(rootElement, onSelect) {
        return buildTreeGrid([rootElement], DATA_COLUMNS, onSelect, false);
    }

    /**
     * Detail panel content for a selected element (mirrors the Vaadin selection listener):
     *   - element with members  → a detail tree grid rooted at the element, expanded once
     *   - leaf element          → a read-only text area with its value
     */
    function renderDetail(element) {
        if (element.hasMembers) {
            return buildTreeGrid([element], DATA_COLUMNS, null, true);
        }
        const textarea = el("textarea", "detail-textarea");
        textarea.readOnly = true;
        textarea.value = element.value != null ? element.value : "";
        return textarea;
    }

    // ── Statistics view ──────────────────────────────────────────────────

    function statItem(name, value, children) {
        const kids = children || [];
        return {
            name: name,
            value: value,
            hasMembers: kids.length > 0,
            loadMembers: async () => kids
        };
    }

    function humanReadableByteSize(byteSize) {
        const n = Number(byteSize);
        const fmt = (d) => d.toLocaleString(undefined, { maximumFractionDigits: 2, minimumFractionDigits: 0 });
        if (n < 1024) {
            return fmt(n) + " Bytes";
        }
        let d = n / 1024;
        if (d < 1024) {
            return fmt(d) + " KB";
        }
        d /= 1024;
        if (d < 1024) {
            return fmt(d) + " MB";
        }
        d /= 1024;
        return fmt(d) + " GB";
    }

    function dataItems(stats) {
        return [
            statItem("Live data size", humanReadableByteSize(stats.liveDataLength)),
            statItem("Total data size", humanReadableByteSize(stats.totalDataLength))
        ];
    }

    function commonItems(stats) {
        return [statItem("File count", String(stats.fileCount))].concat(dataItems(stats));
    }

    function buildStatisticsItems(stats) {
        const items = [];

        const creationTime = stats.creationTime != null
            ? new Date(stats.creationTime).toLocaleString()
            : "";
        items.push(statItem("Creation time", creationTime));
        commonItems(stats).forEach(i => items.push(i));

        const channelMap = stats.channelStatistics || {};
        const channels = Object.keys(channelMap)
            .map(key => channelMap[key])
            .sort((a, b) => a.channelIndex - b.channelIndex)
            .map(channel => {
                const children = commonItems(channel);

                const files = (channel.files || []).map(file =>
                    statItem("File " + file.fileNumber, file.file, dataItems(file))
                );
                children.push(statItem("Files", "", files));

                return statItem("Channel " + channel.channelIndex, "", children);
            });

        items.push(statItem("Channels", "", channels));
        return items;
    }

    function renderStatistics(stats) {
        return buildTreeGrid(buildStatisticsItems(stats), STATS_COLUMNS, null, false);
    }

    return Object.freeze({
        renderDataTree,
        renderDetail,
        renderStatistics
    });
})();
