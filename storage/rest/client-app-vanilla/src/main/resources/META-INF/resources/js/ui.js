/**
 * EclipseStore Vanilla Viewer — UI rendering helpers
 *
 * Pure DOM manipulation. No templating library.
 * Every function takes data and returns a DOM element or mutates a container.
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

    // ── Object View ─────────────────────────────────────────────────────

    /**
     * Render a ViewerObjectDescription as a data table.
     *
     * @param {Object} obj — ViewerObjectDescription from the REST API
     * @param {Object} [typeDictionary] — parsed type dictionary (typeId → description)
     * @param {function} onRefClick — callback(objectId) when a reference is clicked
     * @returns {HTMLElement}
     */
    function renderObject(obj, typeDictionary, onRefClick) {
        const container = el("div");

        // Header: type name + object ID
        const header = el("div", "object-header");
        const typeName = resolveTypeName(obj.typeId, typeDictionary);
        header.appendChild(el("span", "type-name", simpleName(typeName)));
        header.appendChild(el("span", "oid", "oid: " + obj.objectId));
        if (obj.length) {
            header.appendChild(el("span", "oid", "length: " + obj.length));
        }
        container.appendChild(header);

        // Data table
        const table = document.createElement("table");
        const thead = el("thead");
        const headRow = el("tr");
        for (const label of ["#", "Field", "Type", "Value"]) {
            headRow.appendChild(el("th", null, label));
        }
        thead.appendChild(headRow);
        table.appendChild(thead);

        const tbody = el("tbody");

        if (obj.data && obj.data.length > 0) {
            const members = resolveMembers(obj.typeId, typeDictionary);
            for (let i = 0; i < obj.data.length; i++) {
                const row = el("tr");
                // Index
                row.appendChild(el("td", null, String(i)));
                // Field name
                const memberInfo = members[i] || {};
                row.appendChild(el("td", "field-name", memberInfo.name || ("field_" + i)));
                // Field type
                row.appendChild(el("td", "field-type", memberInfo.type ? simpleName(memberInfo.type) : ""));
                // Value
                const valueCell = el("td", "field-value");
                renderValue(valueCell, obj.data[i], obj.references ? obj.references[i] : null, onRefClick);
                row.appendChild(valueCell);

                tbody.appendChild(row);
            }
        } else if (obj.simplified !== undefined) {
            // Primitive / simple value
            const row = el("tr");
            row.appendChild(el("td", null, "0"));
            row.appendChild(el("td", "field-name", "value"));
            row.appendChild(el("td", "field-type", simpleName(typeName)));
            const valueCell = el("td", "field-value");
            valueCell.appendChild(el("span", "value-string", String(obj.data || "")));
            row.appendChild(valueCell);
            tbody.appendChild(row);
        }

        table.appendChild(tbody);
        container.appendChild(table);
        return container;
    }

    /**
     * Render a single value cell.
     * If the value is a numeric string that matches a reference objectId, render as a clickable link.
     */
    function renderValue(cell, value, reference, onRefClick) {
        if (value === null || value === undefined) {
            cell.appendChild(el("span", "value-null", "null"));
            return;
        }

        // Array value — render as nested list
        if (Array.isArray(value)) {
            const list = el("span", "value-string", "[" + value.length + " elements]");
            cell.appendChild(list);
            return;
        }

        const str = String(value);

        // Check if this is an object reference (numeric string that could be an OID)
        if (reference && reference.objectId && reference.objectId !== "0") {
            const link = el("span", "value-ref", "→ " + simpleName(resolveTypeName(reference.typeId, null)) + " #" + reference.objectId);
            link.title = "Navigate to object " + reference.objectId;
            link.addEventListener("click", () => onRefClick(reference.objectId));
            cell.appendChild(link);
            return;
        }

        // Check if value looks like a pure object reference (large numeric string)
        if (/^\d{10,}$/.test(str) && str !== "0") {
            const link = el("span", "value-ref", "→ #" + str);
            link.title = "Navigate to object " + str;
            link.addEventListener("click", () => onRefClick(str));
            cell.appendChild(link);
            return;
        }

        // Boolean
        if (str === "true" || str === "false") {
            cell.appendChild(el("span", "value-boolean", str));
            return;
        }

        // Number
        if (str !== "" && !isNaN(str) && str.length < 20) {
            cell.appendChild(el("span", "value-number", str));
            return;
        }

        // Default: string
        cell.appendChild(el("span", "value-string", str));
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

            const tbody = el("tbody");
            for (const ch of stats.channelStatistics) {
                const row = el("tr");
                row.appendChild(el("td", null, String(ch.channelIndex !== undefined ? ch.channelIndex : "–")));
                row.appendChild(el("td", null, formatNumber(ch.fileCount)));
                row.appendChild(el("td", null, formatBytes(ch.totalDataLength)));
                tbody.appendChild(row);
            }
            table.appendChild(tbody);
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
     * Very basic parser: extracts typeId, typeName, and member fields.
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
                // Save previous type if any
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

        // Save last type
        if (currentTypeId !== null) {
            dict[currentTypeId] = { typeName: currentTypeName, members: currentMembers };
        }

        return dict;
    }

    // ── Breadcrumb ──────────────────────────────────────────────────────

    /**
     * Render a breadcrumb trail.
     * @param {Array<{label: string, oid: string}>} trail
     * @param {function} onClick — callback(oid)
     * @returns {HTMLElement}
     */
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
        renderObject,
        renderStatistics,
        renderDictionary,
        parseTypeDictionary,
        renderBreadcrumb,
        simpleName,
    });
})();

