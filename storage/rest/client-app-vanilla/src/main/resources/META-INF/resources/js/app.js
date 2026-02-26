/**
 * EclipseStore Vanilla Viewer — Application Controller
 *
 * Wires the API client and the UI renderer together.
 * Manages navigation, connection state, and local storage of recent URLs.
 */
(() => {
    "use strict";

    // ── Constants ────────────────────────────────────────────────────────
    const STORAGE_KEY = "eclipsestore-viewer-urls";
    const MAX_RECENT = 8;
    const DEFAULT_VALUE_LENGTH = 100;

    // ── State ────────────────────────────────────────────────────────────
    let typeDictionary = null;  // parsed dictionary { typeId → { typeName, members } }
    let rawDictionary = null;   // raw text
    let rootOid = null;         // root object ID
    let navigationTrail = [];   // breadcrumb [{label, oid}]

    // ── DOM references ───────────────────────────────────────────────────
    const $ = (id) => document.getElementById(id);

    const connectScreen = $("connect-screen");
    const appScreen     = $("app-screen");
    const baseUrlInput  = $("base-url");
    const btnConnect    = $("btn-connect");
    const connectError  = $("connect-error");
    const recentUrlsDiv = $("recent-urls");
    const recentList    = $("recent-urls-list");
    const headerUrl     = $("header-url");
    const btnRefresh    = $("btn-refresh");
    const btnDisconnect = $("btn-disconnect");
    const objectView    = $("object-view");
    const breadcrumbEl  = $("breadcrumb");
    const statsView     = $("statistics-view");
    const dictView      = $("dictionary-view");

    // ── Recent URLs (localStorage) ───────────────────────────────────────

    function loadRecentUrls() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
        } catch {
            return [];
        }
    }

    function saveRecentUrl(url) {
        let urls = loadRecentUrls().filter(u => u !== url);
        urls.unshift(url);
        if (urls.length > MAX_RECENT) urls = urls.slice(0, MAX_RECENT);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(urls));
    }

    function removeRecentUrl(url) {
        const urls = loadRecentUrls().filter(u => u !== url);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(urls));
    }

    function renderRecentUrls() {
        const urls = loadRecentUrls();
        if (urls.length === 0) {
            recentUrlsDiv.hidden = true;
            return;
        }
        recentUrlsDiv.hidden = false;
        recentList.innerHTML = "";
        for (const url of urls) {
            const li = document.createElement("li");

            const link = document.createElement("span");
            link.className = "recent-url-link";
            link.textContent = url;
            link.addEventListener("click", () => {
                baseUrlInput.value = url;
                connect();
            });

            const removeBtn = document.createElement("button");
            removeBtn.className = "recent-url-remove";
            removeBtn.textContent = "✕";
            removeBtn.title = "Remove";
            removeBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                removeRecentUrl(url);
                renderRecentUrls();
            });

            li.appendChild(link);
            li.appendChild(removeBtn);
            recentList.appendChild(li);
        }
    }

    // ── Tabs ─────────────────────────────────────────────────────────────

    function initTabs() {
        const tabs = document.querySelectorAll(".tab-bar .tab");
        const contents = document.querySelectorAll(".tab-content");

        tabs.forEach(tab => {
            tab.addEventListener("click", () => {
                const targetId = "tab-" + tab.dataset.tab;
                tabs.forEach(t => t.classList.remove("active"));
                tab.classList.add("active");
                contents.forEach(c => {
                    c.hidden = (c.id !== targetId);
                    if (c.id === targetId) c.classList.add("active");
                    else c.classList.remove("active");
                });

                // Lazy-load tab content
                if (tab.dataset.tab === "statistics" && statsView.querySelector(".placeholder")) {
                    loadStatistics();
                }
                if (tab.dataset.tab === "dictionary" && dictView.querySelector(".placeholder")) {
                    loadDictionary();
                }
            });
        });
    }

    // ── Connection ───────────────────────────────────────────────────────

    async function connect() {
        const url = baseUrlInput.value.trim();
        if (!url) {
            showConnectError("Please enter a URL.");
            return;
        }

        hideConnectError();
        btnConnect.disabled = true;
        btnConnect.textContent = "Connecting…";

        try {
            StorageApi.setBaseUrl(url);

            // Test connectivity and get root
            const root = await StorageApi.getRoot();
            if (!root || root.objectId === undefined) {
                throw new Error("Invalid response from server — not an EclipseStore REST endpoint?");
            }

            rootOid = root.objectId;
            saveRecentUrl(url);

            // Fetch and parse type dictionary for field resolution
            rawDictionary = await StorageApi.getDictionary();
            typeDictionary = UI.parseTypeDictionary(rawDictionary);

            showApp(url);
            navigateTo(String(rootOid), "root");

        } catch (err) {
            showConnectError(err.message || "Connection failed");
        } finally {
            btnConnect.disabled = false;
            btnConnect.textContent = "Connect";
        }
    }

    function disconnect() {
        appScreen.hidden = true;
        connectScreen.hidden = false;
        typeDictionary = null;
        rawDictionary = null;
        rootOid = null;
        navigationTrail = [];
        objectView.innerHTML = '<p class="placeholder">Loading root object…</p>';
        statsView.innerHTML = '<p class="placeholder">Loading statistics…</p>';
        dictView.innerHTML = '<p class="placeholder">Loading type dictionary…</p>';
    }

    function showApp(url) {
        connectScreen.hidden = true;
        appScreen.hidden = false;
        headerUrl.textContent = url;
    }

    function showConnectError(msg) {
        connectError.textContent = msg;
        connectError.hidden = false;
    }

    function hideConnectError() {
        connectError.hidden = true;
    }

    // ── Object Navigation ────────────────────────────────────────────────

    async function navigateTo(oid, label) {
        // Update breadcrumb trail
        const existingIndex = navigationTrail.findIndex(t => t.oid === oid);
        if (existingIndex >= 0) {
            // Navigating back — truncate trail
            navigationTrail = navigationTrail.slice(0, existingIndex + 1);
        } else {
            navigationTrail.push({ label: label || ("#" + oid), oid: oid });
        }

        renderBreadcrumb();

        // Show loading state
        objectView.innerHTML = '<span class="spinner"></span> Loading…';

        try {
            const obj = await StorageApi.getObject(oid, {
                valueLength: DEFAULT_VALUE_LENGTH,
                references: true,
            });

            objectView.innerHTML = "";
            const rendered = UI.renderObject(obj, typeDictionary, (refOid) => {
                const refTypeName = "object";
                navigateTo(refOid, UI.simpleName(refTypeName) + " #" + refOid);
            });
            objectView.appendChild(rendered);

        } catch (err) {
            objectView.innerHTML = "";
            const errDiv = document.createElement("div");
            errDiv.className = "error-message";
            errDiv.textContent = "Failed to load object " + oid + ": " + err.message;
            objectView.appendChild(errDiv);
        }
    }

    function renderBreadcrumb() {
        breadcrumbEl.innerHTML = "";
        const bc = UI.renderBreadcrumb(navigationTrail, (oid) => {
            navigateTo(oid);
        });
        breadcrumbEl.replaceWith(bc);
        // Re-assign the element reference since we replaced it
        // (we use the same id, so querySelector still works)
    }

    // ── Statistics ───────────────────────────────────────────────────────

    async function loadStatistics() {
        statsView.innerHTML = '<span class="spinner"></span> Loading statistics…';
        try {
            const stats = await StorageApi.getFileStatistics();
            statsView.innerHTML = "";
            statsView.appendChild(UI.renderStatistics(stats));
        } catch (err) {
            statsView.innerHTML = "";
            const errDiv = document.createElement("div");
            errDiv.className = "error-message";
            errDiv.textContent = "Failed to load statistics: " + err.message;
            statsView.appendChild(errDiv);
        }
    }

    // ── Dictionary ──────────────────────────────────────────────────────

    async function loadDictionary() {
        dictView.innerHTML = '<span class="spinner"></span> Loading type dictionary…';
        try {
            if (!rawDictionary) {
                rawDictionary = await StorageApi.getDictionary();
                typeDictionary = UI.parseTypeDictionary(rawDictionary);
            }
            dictView.innerHTML = "";
            dictView.appendChild(UI.renderDictionary(rawDictionary));
        } catch (err) {
            dictView.innerHTML = "";
            const errDiv = document.createElement("div");
            errDiv.className = "error-message";
            errDiv.textContent = "Failed to load dictionary: " + err.message;
            dictView.appendChild(errDiv);
        }
    }

    // ── Init ─────────────────────────────────────────────────────────────

    function init() {
        // Populate recent URLs
        renderRecentUrls();

        // Set default URL from most recent, or a sensible default
        const recent = loadRecentUrls();
        if (recent.length > 0) {
            baseUrlInput.value = recent[0];
        }

        // Event listeners
        btnConnect.addEventListener("click", connect);
        baseUrlInput.addEventListener("keydown", (e) => {
            if (e.key === "Enter") connect();
        });
        btnDisconnect.addEventListener("click", disconnect);
        btnRefresh.addEventListener("click", () => {
            if (rootOid) {
                navigationTrail = [];
                navigateTo(String(rootOid), "root");
            }
        });

        initTabs();
    }

    // Start when DOM is ready
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();

