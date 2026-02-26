/**
 * EclipseStore REST API Client
 *
 * Pure fetch-based client for the EclipseStore storage REST endpoints.
 * Zero dependencies — only uses the browser Fetch API.
 *
 * REST API routes (relative to baseUrl):
 *   GET /root                          → { name, objectId }
 *   GET /dictionary                    → plain text type dictionary
 *   GET /object/{oid}?params...        → ViewerObjectDescription JSON
 *   GET /maintenance/filesStatistics   → storage file statistics JSON
 */
const StorageApi = (() => {
    "use strict";

    let _baseUrl = "";

    /**
     * Set the base URL for all subsequent API calls.
     * Trailing slashes are removed automatically.
     * @param {string} url — e.g. "http://localhost:4567/store-data"
     */
    function setBaseUrl(url) {
        _baseUrl = url.replace(/\/+$/, "");
    }

    /** @returns {string} current base URL */
    function getBaseUrl() {
        return _baseUrl;
    }

    /**
     * Internal helper — performs a fetch and returns parsed result.
     * @param {string} path — relative path appended to baseUrl
     * @param {"json"|"text"} type — expected response type
     * @returns {Promise<any>}
     */
    async function request(path, type) {
        const url = _baseUrl + "/" + path;
        let response;
        try {
            response = await fetch(url);
        } catch (err) {
            // Typically a CORS or network error — provide a helpful message
            throw new Error(
                "Cannot reach " + url + ". " +
                "Possible causes: the server is not running, the URL is wrong, " +
                "or CORS is not enabled on the REST service. " +
                "(Browser error: " + err.message + ")"
            );
        }
        if (!response.ok) {
            throw new Error(`HTTP ${response.status} — ${response.statusText} (${url})`);
        }
        return type === "json" ? response.json() : response.text();
    }

    /**
     * Get the root object reference.
     * @returns {Promise<{name: string, objectId: number}>}
     */
    function getRoot() {
        return request("root", "json");
    }

    /**
     * Get the type dictionary as plain text.
     * @returns {Promise<string>}
     */
    function getDictionary() {
        return request("dictionary", "text");
    }

    /**
     * Get an object by its persistence object ID.
     * @param {number|string} oid — object ID
     * @param {Object} [opts] — optional query parameters
     * @param {number} [opts.valueLength]    — max value length
     * @param {number} [opts.fixedOffset]    — offset for fixed-size members
     * @param {number} [opts.fixedLength]    — number of fixed-size members
     * @param {number} [opts.variableOffset] — offset for variable-size members
     * @param {number} [opts.variableLength] — number of variable-size members
     * @param {boolean} [opts.resolveReferences] — resolve object references inline
     * @returns {Promise<ViewerObjectDescription>}
     */
    function getObject(oid, opts) {
        const params = new URLSearchParams();
        if (opts) {
            for (const [key, value] of Object.entries(opts)) {
                if (value !== undefined && value !== null) {
                    params.set(key, String(value));
                }
            }
        }
        const qs = params.toString();
        const path = "object/" + oid + (qs ? "?" + qs : "");
        return request(path, "json");
    }

    /**
     * Get storage file statistics.
     * @returns {Promise<Object>}
     */
    function getFileStatistics() {
        return request("maintenance/filesStatistics", "json");
    }

    /**
     * Quick connectivity check — tries to fetch the root.
     * @returns {Promise<boolean>}
     */
    async function ping() {
        try {
            await getRoot();
            return true;
        } catch {
            return false;
        }
    }

    // Public API
    return Object.freeze({
        setBaseUrl,
        getBaseUrl,
        getRoot,
        getDictionary,
        getObject,
        getFileStatistics,
        ping,
    });
})();


