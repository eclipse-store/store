/**
 * EclipseStore Vanilla Viewer — application controller
 *
 * Wires the connect screen, the two tabs (Data / Statistics) and the data tree + detail split,
 * reproducing the navigation of the original Vaadin client (ConnectView / InstanceView /
 * StorageViewComponent).
 */
(() => {
    "use strict";

    const PAGE_TITLE   = "Eclipse Store Client";
    const HISTORY_KEY  = "eclipsestore-viewer-urls";
    const PRESET_URLS  = [
        "http://localhost:8080/store-data/default/",
        "http://localhost:4567/store-data/"
    ];

    const $ = (id) => document.getElementById(id);

    const connectScreen = $("connect-screen");
    const appScreen     = $("app-screen");
    const urlInput      = $("base-url");
    const urlPresets    = $("url-presets");
    const btnConnect    = $("btn-connect");
    const connectError  = $("connect-error");
    const headerLabel   = $("header-label");
    const btnDisconnect = $("btn-disconnect");
    const dataTree      = $("data-tree");
    const dataDetail    = $("data-detail");
    const splitDivider  = $("split-divider");
    const statisticsView = $("statistics-view");

    let view            = null;   // StorageView
    let statisticsLoaded = false;

    // ── URL history (combobox items) ─────────────────────────────────────

    function loadHistory() {
        try {
            return JSON.parse(localStorage.getItem(HISTORY_KEY)) || [];
        } catch {
            return [];
        }
    }

    function rememberUrl(url) {
        const history = loadHistory().filter(u => u !== url && !PRESET_URLS.includes(u));
        if (!PRESET_URLS.includes(url)) {
            history.unshift(url);
        }
        localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    }

    function renderPresets() {
        urlPresets.innerHTML = "";
        for (const url of [...PRESET_URLS, ...loadHistory()]) {
            const option = document.createElement("option");
            option.value = url;
            urlPresets.appendChild(option);
        }
    }

    // ── Connection ───────────────────────────────────────────────────────

    async function connect() {
        const url = urlInput.value.trim();
        if (!url) {
            showConnectError("Please enter a URL.");
            return;
        }

        hideConnectError();
        btnConnect.disabled = true;

        try {
            StorageApi.setBaseUrl(url);
            await StorageApi.getRoot(); // validate the endpoint (ConnectView.tryConnect)

            view = StorageView.New(StorageApi);
            await view.loadDictionary();
            const root = await view.root();

            rememberUrl(url);
            renderPresets();
            showApp(url);

            dataDetail.innerHTML = "";
            dataTree.innerHTML = "";
            dataTree.appendChild(UI.renderDataTree(root, onElementSelected));

            statisticsLoaded = false;
            statisticsView.innerHTML = "";
            selectTab("data");
        } catch (err) {
            showConnectError("Error connecting to instance. Please ensure that a started REST service is available at " + url);
        } finally {
            btnConnect.disabled = false;
        }
    }

    function disconnect() {
        view = null;
        appScreen.hidden = true;
        connectScreen.hidden = false;
        headerLabel.textContent = "Client";
        document.title = "Connect - " + PAGE_TITLE;
        dataTree.innerHTML = "";
        dataDetail.innerHTML = "";
        statisticsView.innerHTML = "";
    }

    function showApp(url) {
        connectScreen.hidden = true;
        appScreen.hidden = false;
        headerLabel.textContent = "Client - " + url;
        document.title = url + " - " + PAGE_TITLE;
    }

    function showConnectError(message) {
        connectError.textContent = message;
        connectError.hidden = false;
    }

    function hideConnectError() {
        connectError.hidden = true;
    }

    // ── Detail panel ─────────────────────────────────────────────────────

    function onElementSelected(element) {
        dataDetail.innerHTML = "";
        dataDetail.appendChild(UI.renderDetail(element));
    }

    // ── Tabs ─────────────────────────────────────────────────────────────

    function selectTab(name) {
        document.querySelectorAll(".tab").forEach(tab => {
            tab.classList.toggle("active", tab.dataset.tab === name);
        });
        document.querySelectorAll(".tab-content").forEach(content => {
            const active = content.id === "tab-" + name;
            content.hidden = !active;
        });

        if (name === "statistics" && !statisticsLoaded) {
            loadStatistics();
        }
    }

    async function loadStatistics() {
        statisticsLoaded = true;
        statisticsView.innerHTML = "…";
        try {
            const stats = await StorageApi.getFileStatistics();
            statisticsView.innerHTML = "";
            statisticsView.appendChild(UI.renderStatistics(stats));
        } catch (err) {
            statisticsView.innerHTML = "";
            statisticsView.appendChild(
                Object.assign(document.createElement("div"), {
                    className: "error-message",
                    textContent: "Failed to load statistics: " + err.message
                })
            );
            statisticsLoaded = false;
        }
    }

    // ── Split divider drag (vertical split: tree over detail) ────────────

    function initSplitDivider() {
        let dragging = false;
        splitDivider.addEventListener("mousedown", (event) => {
            event.preventDefault();
            dragging = true;
            document.body.style.userSelect = "none";
        });
        document.addEventListener("mousemove", (event) => {
            if (!dragging) return;
            const layout = splitDivider.parentElement;
            const rect = layout.getBoundingClientRect();
            const pct = Math.max(15, Math.min(85, ((event.clientY - rect.top) / rect.height) * 100));
            dataTree.parentElement.style.flexBasis = pct + "%";
            dataDetail.parentElement.style.flexBasis = (100 - pct) + "%";
        });
        document.addEventListener("mouseup", () => {
            dragging = false;
            document.body.style.userSelect = "";
        });
    }

    // ── Init ─────────────────────────────────────────────────────────────

    function init() {
        renderPresets();
        urlInput.value = loadHistory()[0] || PRESET_URLS[1];
        document.title = "Connect - " + PAGE_TITLE;

        btnConnect.addEventListener("click", connect);
        urlInput.addEventListener("keydown", (event) => {
            if (event.key === "Enter") connect();
        });
        btnDisconnect.addEventListener("click", disconnect);
        document.querySelectorAll(".tab").forEach(tab => {
            tab.addEventListener("click", () => selectTab(tab.dataset.tab));
        });
        initSplitDivider();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
