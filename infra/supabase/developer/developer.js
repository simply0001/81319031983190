(() => {
  "use strict";

  const API_URL = String(window.POCKETPASS_DEVELOPER_API_URL || "").replace(/\/+$/, "");
  const API_KEY = String(window.POCKETPASS_DEVELOPER_KEY || "");
  const secureApiUrl = (() => {
    try { const url = new URL(API_URL); return url.protocol === "https:" && url.origin === API_URL; }
    catch (_) { return false; }
  })();
  const SESSION_KEY = "pp_developer_session";
  const USAGE_DAYS = 30;
  const MAX_REDIRECT_URIS = 10;
  const DISCORD_RETURN_URL = `${location.origin}/`;
  const CLIENT_TYPE_LABELS = { public: "Public", confidential: "Confidential" };

  const HINTS = {
    APP_LIMIT: "You already have the maximum number of apps. Delete one to register another.",
    APP_NAME_RESERVED: "App names cannot contain \"PocketPass\".",
    APP_SUSPENDED: "This app is suspended. It cannot be changed or connected until PocketPass reactivates it.",
    APP_NOT_FOUND: "That app does not exist or is not yours.",
    LIMIT_REQUEST_PENDING: "A request for this app is already waiting for review.",
    LIMITS_UNCHANGED: "Ask for at least one limit above the current one.",
  };

  const $ = (id) => document.getElementById(id);
  const el = (tag, props = {}, children = []) => {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
      if (value === undefined || value === null) continue;
      if (key === "class") node.className = value;
      else if (key === "text") node.textContent = value;
      else if (key === "dataset") Object.assign(node.dataset, value);
      else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2), value);
      else node.setAttribute(key, value);
    }
    for (const child of [].concat(children)) {
      if (child === undefined || child === null) continue;
      node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
  };
  const clear = (node) => { while (node.firstChild) node.removeChild(node.firstChild); };

  const state = {
    session: null,
    me: null,
    view: "apps",
    apps: { rows: [] },
    app: { id: null, data: null, usage: [] },
    secret: null,
    lastLoginEmail: "",
  };

  const loadSession = () => {
    try {
      const raw = sessionStorage.getItem(SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) {
      return null;
    }
  };
  const saveSession = (session) => {
    state.session = session;
    if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    else sessionStorage.removeItem(SESSION_KEY);
  };
  const sessionFromResponse = (body) => ({
    access_token: body.access_token,
    refresh_token: body.refresh_token,
    expires_at: body.expires_at || Math.floor(Date.now() / 1000) + Number(body.expires_in || 3600),
    user: { id: body.user && body.user.id, email: body.user && body.user.email },
  });
  const sessionFromFragment = (params) => ({
    access_token: params.get("access_token"),
    refresh_token: params.get("refresh_token"),
    expires_at: Number(params.get("expires_at")) || Math.floor(Date.now() / 1000) + Number(params.get("expires_in") || 3600),
    user: { id: null, email: null },
  });
  const consumeFragment = () => {
    const raw = location.hash.replace(/^#/, "");
    if (!/(^|&)(access_token|error|error_description)=/.test(raw)) return null;
    const params = new URLSearchParams(raw);
    history.replaceState(null, "", `${location.pathname}${location.search}`);
    if (!params.get("access_token")) {
      return { error: params.get("error_description") || params.get("error") || "Discord sign-in failed." };
    }
    return { session: sessionFromFragment(params) };
  };

  class ApiError extends Error {
    constructor(status, body) {
      super((body && (body.msg || body.message || body.error_description || body.error)) || `HTTP ${status}`);
      this.status = status;
      this.body = body || {};
      this.code = this.body.error_code || this.body.code || null;
      this.hint = this.body.hint || null;
    }
  }

  const readBody = async (response) => {
    const text = await response.text();
    if (!text) return {};
    try {
      return JSON.parse(text);
    } catch (_) {
      return { message: text };
    }
  };

  const authFetch = async (path, body, token) => {
    const headers = { apikey: API_KEY, "Content-Type": "application/json" };
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${API_URL}${path}`, {
      method: "POST",
      headers,
      body: JSON.stringify(body || {}),
    });
    const data = await readBody(response);
    if (!response.ok) throw new ApiError(response.status, data);
    return data;
  };

  const sendCode = (email) => authFetch("/auth/v1/otp", { email, create_user: false });
  const verifyCode = async (email, token) => {
    const body = await authFetch("/auth/v1/verify", { type: "email", email, token });
    saveSession(sessionFromResponse(body));
  };
  const loginEmailFor = (identifier) => (identifier.includes("@") ? identifier : `${identifier}@users.pocketpass.xyz`);
  const signInWithPassword = async (identifier, password) => {
    const body = await authFetch("/auth/v1/token?grant_type=password", { email: loginEmailFor(identifier), password });
    saveSession(sessionFromResponse(body));
  };
  const startDiscord = () => {
    location.assign(`${API_URL}/auth/v1/authorize?provider=discord&redirect_to=${encodeURIComponent(DISCORD_RETURN_URL)}`);
  };

  let refreshing = null;
  const refreshSession = () => {
    if (refreshing) return refreshing;
    const current = state.session;
    if (!current || !current.refresh_token) return Promise.reject(new ApiError(401, { message: "Signed out" }));
    refreshing = authFetch("/auth/v1/token?grant_type=refresh_token", { refresh_token: current.refresh_token })
      .then((body) => saveSession(sessionFromResponse(body)))
      .finally(() => { refreshing = null; });
    return refreshing;
  };

  const signOut = async () => {
    const current = state.session;
    saveSession(null);
    state.me = null;
    state.secret = null;
    if (current && current.access_token) {
      try {
        await fetch(`${API_URL}/auth/v1/logout?scope=local`, {
          method: "POST",
          headers: { apikey: API_KEY, Authorization: `Bearer ${current.access_token}` },
        });
      } catch (_) {}
    }
    render();
  };

  const rpc = async (name, args = {}, retry = true) => {
    if (!state.session) throw new ApiError(401, { message: "Signed out" });
    if (state.session.expires_at * 1000 - Date.now() < 60_000) {
      try {
        await refreshSession();
      } catch (_) {}
    }
    const response = await fetch(`${API_URL}/rest/v1/rpc/${name}`, {
      method: "POST",
      headers: {
        apikey: API_KEY,
        Authorization: `Bearer ${state.session.access_token}`,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(args),
    });
    const data = await readBody(response);
    if (response.status === 401 && retry) {
      try {
        await refreshSession();
      } catch (_) {
        await signOut();
        throw new ApiError(401, { message: "Your session expired. Please sign in again." });
      }
      return rpc(name, args, false);
    }
    if (!response.ok) throw new ApiError(response.status, data);
    return data;
  };

  const banner = $("banner");
  let bannerTimer = null;
  const hideBanner = () => {
    if (banner.hidden) return;
    banner.classList.add("is-hiding");
    bannerTimer = setTimeout(() => {
      banner.hidden = true;
      banner.classList.remove("is-hiding");
    }, 220);
  };
  const notify = (message, kind = "ok") => {
    clearTimeout(bannerTimer);
    banner.hidden = true;
    banner.textContent = message;
    banner.className = `banner is-${kind}`;
    void banner.offsetWidth;
    banner.hidden = false;
    bannerTimer = setTimeout(hideBanner, kind === "error" ? 8000 : 4000);
  };
  const explain = (error) => {
    if (!(error instanceof ApiError)) return error && error.message ? error.message : String(error);
    if (error.code === "otp_disabled") return "No PocketPass account uses that email. If you signed up with Discord or a username, use those options below.";
    if (error.code === "invalid_credentials" || (error.status === 400 && /invalid login credentials/i.test(error.message || ""))) {
      return "Wrong username or password. If you linked an email address to the account, sign in with that email.";
    }
    if (error.code === "otp_expired") return "That code is wrong or has expired.";
    if (error.status === 429) return "Too many attempts. Wait a minute and try again.";
    if (error.hint && HINTS[error.hint]) return HINTS[error.hint];
    if (error.code === "42501") return "Sign in with your PocketPass account to continue.";
    return error.message;
  };

  const fmtDate = (value) => {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
  };
  const fmtDay = (value) => (value ? String(value).slice(0, 10) : "—");
  const fmtNumber = (value) => (value === null || value === undefined ? "—" : Number(value).toLocaleString());
  const plural = (count, noun) => `${fmtNumber(count)} ${noun}${Number(count) === 1 ? "" : "s"}`;
  const typeLabel = (app) => CLIENT_TYPE_LABELS[app.client_type] || app.client_type || "—";
  const statusBadge = (status) => (status === "active"
    ? el("span", { class: "badge badge-good", text: "active" })
    : el("span", { class: "badge badge-warn", text: status || "unknown" }));
  const typeBadge = (app) => el("span", { class: "badge badge-type", text: typeLabel(app).toLowerCase() });

  const table = (columns, rows, options = {}) => {
    if (!rows.length) return el("div", { class: "empty", text: options.empty || "Nothing here yet." });
    const head = el("tr", {}, columns.map((column) => el("th", { text: column.label })));
    const body = rows.map((row) => {
      const tr = el("tr", {}, columns.map((column) => {
        const value = column.render ? column.render(row) : row[column.key];
        return el("td", { class: column.numeric ? "num" : null }, value === undefined || value === null ? "—" : value);
      }));
      if (options.onRow) {
        tr.classList.add("is-clickable");
        tr.addEventListener("click", () => options.onRow(row));
      }
      return tr;
    });
    return el("table", {}, [el("thead", {}, head), el("tbody", {}, body)]);
  };

  const confirmAction = (message) => window.confirm(message);

  const copyText = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      notify("Copied.");
    } catch (_) {
      notify("Copy failed. Select the text and copy it by hand.", "error");
    }
  };
  const copyButton = (text, label = "Copy") => el("button", {
    type: "button",
    class: "btn btn-grey btn-small",
    text: label,
    onclick: () => copyText(text),
  });
  const copyable = (text) => el("span", { class: "copy-row" }, [el("code", { class: "mono", text }), copyButton(text)]);

  const base64url = (bytes) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const randomToken = (size) => base64url(crypto.getRandomValues(new Uint8Array(size)));
  const pkceChallenge = async (verifier) => base64url(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier))));
  const authorizeUrl = (app, redirectUri, challenge, stateToken) => {
    const params = new URLSearchParams({
      client_id: app.client_id,
      redirect_uri: redirectUri,
      response_type: "code",
      scope: "openid",
      code_challenge: challenge,
      code_challenge_method: "S256",
      state: stateToken,
    });
    return `${API_URL}/auth/v1/oauth/authorize?${params.toString()}`;
  };
  const curlFor = (app, test) => {
    const lines = [`curl -X POST ${API_URL}/auth/v1/oauth/token \\`];
    if (app.client_type === "confidential") lines.push(`  -u "${app.client_id}:<client secret>" \\`);
    lines.push("  -d grant_type=authorization_code \\");
    if (app.client_type !== "confidential") lines.push(`  -d client_id=${app.client_id} \\`);
    lines.push(`  -d "redirect_uri=${test.redirectUri}" \\`);
    lines.push(`  -d code_verifier=${test.verifier} \\`);
    lines.push("  -d \"code=<code from the redirect>\"");
    lines.push("");
    lines.push(`curl -X POST ${API_URL}/v1/session.get \\`);
    lines.push("  -H \"Authorization: Bearer <access_token>\" \\");
    lines.push("  -H \"Content-Type: application/json\" \\");
    lines.push("  -d '{}'");
    return lines.join("\n");
  };

  const scopeList = () => (state.me && Array.isArray(state.me.scopes) ? state.me.scopes : []);
  const renderPermGrid = (container, selected) => {
    clear(container);
    for (const scope of scopeList()) {
      const input = el("input", { type: "checkbox", name: "scope", value: scope.key });
      input.checked = selected.includes(scope.key);
      container.append(el("label", { class: "perm-option" }, [
        input,
        el("span", {}, [
          el("span", { class: "perm-title perm-key", text: scope.key }),
          el("br"),
          el("span", { class: "perm-desc", text: scope.description || "" }),
        ]),
      ]));
    }
  };
  const readPermGrid = (container) => Array.from(container.querySelectorAll("input[name=scope]"))
    .filter((input) => input.checked)
    .map((input) => input.value);

  const parseRedirectUris = (text) => {
    const uris = [];
    for (const line of String(text || "").split(/\r?\n/)) {
      const value = line.trim();
      if (value && !uris.includes(value)) uris.push(value);
    }
    return uris;
  };
  const readAppForm = (form) => {
    const name = form.elements.name.value.trim();
    const redirectUris = parseRedirectUris(form.elements.redirect_uris.value);
    const scopes = readPermGrid(form.querySelector("[data-perm-grid]"));
    if (!name) throw new Error("Give the app a name.");
    if (!redirectUris.length) throw new Error("Add at least one redirect URI.");
    if (redirectUris.length > MAX_REDIRECT_URIS) throw new Error(`An app can have at most ${MAX_REDIRECT_URIS} redirect URIs.`);
    if (!scopes.length) throw new Error("Tick at least one permission.");
    return {
      p_name: name,
      p_description: form.elements.description.value.trim(),
      p_website: form.elements.website.value.trim(),
      p_logo_url: form.elements.logo_url.value.trim(),
      p_redirect_uris: redirectUris,
      p_scopes: scopes,
    };
  };

  const views = ["login", "apps", "app"];
  const showView = (name) => {
    for (const view of views) $(`view-${view}`).hidden = view !== name;
    const authScreen = name === "login";
    $("login-shell").hidden = !authScreen;
    $("app-shell").hidden = authScreen;
    $("session-name").textContent = (state.me && state.me.display_name)
      || (state.session && state.session.user && state.session.user.email)
      || "";
    for (const link of document.querySelectorAll(".nav-link[data-view]")) {
      const target = link.dataset.view;
      link.classList.toggle("is-active", target === name || (target === "apps" && name === "app"));
    }
  };

  const syncCreateForm = () => {
    const form = $("app-create-form");
    const grid = form.querySelector("[data-perm-grid]");
    if (!grid.children.length) renderPermGrid(grid, []);
    const full = Boolean(state.me) && Number(state.me.app_count) >= Number(state.me.max_apps);
    form.querySelector("button[type=submit]").disabled = full;
    const hint = $("app-create-hint");
    hint.hidden = !full;
    hint.textContent = full ? `You have reached the limit of ${plural(state.me.max_apps, "app")}. Delete one to register another.` : "";
  };

  const loadApps = async () => {
    const [me, result] = await Promise.all([rpc("developer_whoami"), rpc("developer_list_apps")]);
    state.me = me;
    const rows = Array.isArray(result.items) ? result.items : [];
    state.apps.rows = rows;
    $("apps-count").textContent = `${fmtNumber(me.app_count)} of ${fmtNumber(me.max_apps)} apps`;
    $("session-name").textContent = me.display_name || "";
    const wrap = $("apps-table");
    clear(wrap);
    wrap.append(table([
      { label: "App", render: (row) => el("div", {}, [
        el("div", {}, [el("strong", { text: row.name }), " ", typeBadge(row)]),
        el("div", { class: "muted", text: row.description || "" }),
      ]) },
      { label: "Status", render: (row) => statusBadge(row.status) },
      { label: "Connected users", numeric: true, render: (row) => fmtNumber(row.connected_users) },
      { label: `Requests (${USAGE_DAYS} days)`, numeric: true, render: (row) => fmtNumber(row.requests_30d) },
      { label: `Denied (${USAGE_DAYS} days)`, numeric: true, render: (row) => fmtNumber(row.denied_30d) },
      { label: "Created", render: (row) => fmtDate(row.created_at) },
    ], rows, {
      empty: "No apps yet. Register your first one below.",
      onRow: (row) => { location.hash = `#app/${row.client_id}`; },
    }));
    syncCreateForm();
  };

  const externalLink = (url) => (url
    ? el("a", { href: url, target: "_blank", rel: "noopener noreferrer", text: url })
    : "—");

  const renderTestConnect = (root, app, test) => {
    const panel = root.querySelector(".test-panel");
    const facts = panel.querySelector(".test-facts");
    clear(facts);
    const fact = (label, value) => {
      facts.append(el("dt", { text: label }));
      facts.append(el("dd", {}, value));
    };
    fact("Redirect URI", el("code", { class: "mono", text: test.redirectUri }));
    fact("State", copyable(test.stateToken));
    fact("Code verifier", copyable(test.verifier));
    fact("Authorize URL", el("a", { href: test.url, target: "_blank", rel: "noopener noreferrer", text: "Open the consent flow again" }));
    panel.querySelector(".test-curl").textContent = curlFor(app, test);
    panel.hidden = false;
    panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
  };

  const renderApp = (app, usage) => {
    const root = $("app-detail");
    clear(root);
    const fragment = $("tpl-app-detail").content.cloneNode(true);
    const suspended = app.status !== "active";
    const redirectUris = Array.isArray(app.redirect_uris) ? app.redirect_uris : [];
    const scopes = Array.isArray(app.scopes) ? app.scopes : [];

    fragment.querySelector(".app-name").textContent = app.name || "Untitled app";
    fragment.querySelector(".app-sub").textContent = `${typeLabel(app)} client · registered ${fmtDate(app.created_at)}`;
    const badges = fragment.querySelector(".app-badges");
    badges.append(statusBadge(app.status));
    badges.append(typeBadge(app));

    const secretPanel = fragment.querySelector(".secret-panel");
    if (state.secret && state.secret.client_id === app.client_id) {
      secretPanel.hidden = false;
      secretPanel.querySelector(".secret-value").textContent = state.secret.client_secret;
      secretPanel.querySelector(".secret-copy").addEventListener("click", () => copyText(state.secret.client_secret));
      secretPanel.querySelector(".secret-dismiss").addEventListener("click", () => {
        state.secret = null;
        secretPanel.hidden = true;
      });
    }

    const facts = fragment.querySelector(".app-facts");
    const fact = (label, value) => {
      facts.append(el("dt", { text: label }));
      facts.append(el("dd", {}, value instanceof Node ? value : (value === null || value === undefined || value === "" ? "—" : String(value))));
    };
    fact("Client id", copyable(app.client_id));
    fact("Client type", typeLabel(app));
    fact("Status", statusBadge(app.status));
    fact("Description", app.description);
    fact("Website", externalLink(app.website));
    fact("Logo URL", externalLink(app.logo_url));
    fact("Redirect URIs", redirectUris.length
      ? el("ul", { class: "uri-list" }, redirectUris.map((uri) => el("li", {}, el("code", { class: "mono", text: uri }))))
      : "—");
    fact("Permissions", scopes.length
      ? el("div", { class: "chips" }, scopes.map((key) => el("span", { class: "chip chip-perm", text: key })))
      : "—");
    fact("Connected users", fmtNumber(app.connected_users));
    fact(`Requests (${USAGE_DAYS} days)`, fmtNumber(app.requests_30d));
    fact(`Denied (${USAGE_DAYS} days)`, fmtNumber(app.denied_30d));
    fact("Registered", fmtDate(app.created_at));
    fact("Updated", fmtDate(app.updated_at));

    const testButton = fragment.querySelector(".test-connect");
    const testHint = fragment.querySelector(".test-connect-hint");
    testButton.disabled = suspended || !redirectUris.length;
    testHint.textContent = suspended
      ? "Suspended apps cannot connect."
      : (redirectUris.length ? `Opens the consent flow for ${redirectUris[0]} in a new tab.` : "Add a redirect URI first.");
    testButton.addEventListener("click", () => {
      const popup = window.open("about:blank", "_blank");
      if (!popup) {
        notify("Allow pop-ups for this site to open the consent flow.", "error");
        return;
      }
      run(async () => {
        try {
          const verifier = randomToken(32);
          const stateToken = randomToken(16);
          const challenge = await pkceChallenge(verifier);
          const test = { verifier, stateToken, redirectUri: redirectUris[0], url: authorizeUrl(app, redirectUris[0], challenge, stateToken) };
          renderTestConnect(root, app, test);
          popup.location.replace(test.url);
        } catch (error) {
          popup.close();
          throw error;
        }
      });
    });

    const rotateRow = fragment.querySelector(".rotate-row");
    rotateRow.hidden = app.client_type !== "confidential";
    const rotateButton = fragment.querySelector(".rotate-secret");
    rotateButton.disabled = suspended;
    rotateButton.addEventListener("click", () => run(async () => {
      if (!confirmAction("Rotate the client secret? The current secret stops working immediately — deploy the new one first.")) return;
      const result = await rpc("developer_rotate_secret", { p_client_id: app.client_id });
      state.secret = { client_id: app.client_id, client_secret: result.client_secret };
      notify("Secret rotated. Copy the new secret now — it is shown once.");
      await loadApp();
    }));

    fragment.querySelector(".delete-app").addEventListener("click", () => run(async () => {
      if (!confirmAction(`Delete ${app.name}? Every connected user is disconnected immediately and the client id stops working. This cannot be undone.`)) return;
      await rpc("developer_delete_app", { p_client_id: app.client_id });
      state.secret = null;
      notify(`${app.name} deleted.`);
      location.hash = "#apps";
    }));

    const usageRows = [...usage].sort((a, b) => String(b.day).localeCompare(String(a.day)));
    const totals = usage.reduce((acc, row) => ({
      requests: acc.requests + Number(row.requests || 0),
      denied: acc.denied + Number(row.denied || 0),
    }), { requests: 0, denied: 0 });
    fragment.querySelector(".usage-sub").textContent =
      `Last ${USAGE_DAYS} days: ${plural(totals.requests, "request")}, ${fmtNumber(totals.denied)} denied. Denied requests count towards the rate limit too.`;
    fragment.querySelector(".usage-table").append(table([
      { label: "Day", render: (row) => fmtDay(row.day) },
      { label: "Requests", numeric: true, render: (row) => fmtNumber(row.requests) },
      { label: "Denied", numeric: true, render: (row) => fmtNumber(row.denied) },
      { label: "Users", numeric: true, render: (row) => fmtNumber(row.users) },
    ], usageRows, { empty: `No requests in the last ${USAGE_DAYS} days.` }));

    const limits = app.limits || {};
    const limitFacts = fragment.querySelector(".limits-facts");
    const limitFact = (label, value) => {
      limitFacts.append(el("dt", { text: label }));
      limitFacts.append(el("dd", { text: value }));
    };
    const realtimeLabel = (value) => (value === null || value === undefined ? "Shared pool" : fmtNumber(value));
    limitFact("Per user, per minute", fmtNumber(limits.user_per_minute));
    limitFact("Per app, per minute", fmtNumber(limits.app_per_minute));
    limitFact("Per app, per second (burst)", fmtNumber(limits.app_per_second));
    limitFact("Realtime connections", realtimeLabel(limits.realtime_connections));
    const limitSummary = (values) => (values
      ? `${fmtNumber(values.user_per_minute)} / user / min · ${fmtNumber(values.app_per_minute)} / app / min · ${fmtNumber(values.app_per_second)} / app / s · Realtime ${realtimeLabel(values.realtime_connections).toLowerCase()}`
      : "—");
    const limitRequests = Array.isArray(state.app.limitRequests) ? state.app.limitRequests : [];
    const pendingRequest = limitRequests.find((row) => row.status === "pending");
    const pendingBox = fragment.querySelector(".limits-pending");
    if (pendingRequest) {
      pendingBox.hidden = false;
      pendingBox.append(
        el("span", { class: "badge badge-warn", text: "Request pending" }),
        el("span", { class: "hint", text: ` Sent ${fmtDate(pendingRequest.created_at)}: ${limitSummary(pendingRequest.requested_limits)}. The decision appears here.` }),
      );
    }
    const requestButton = fragment.querySelector(".limits-request");
    const limitsForm = fragment.querySelector(".limits-form");
    requestButton.hidden = Boolean(pendingRequest);
    requestButton.disabled = suspended;
    requestButton.addEventListener("click", () => {
      limitsForm.hidden = false;
      requestButton.hidden = true;
      limitsForm.elements.user_per_minute.value = limits.user_per_minute ?? "";
      limitsForm.elements.app_per_minute.value = limits.app_per_minute ?? "";
      limitsForm.elements.app_per_second.value = limits.app_per_second ?? "";
      limitsForm.elements.realtime_connections.value = limits.realtime_connections ?? "";
      limitsForm.elements.reason.focus();
    });
    limitsForm.querySelector(".limits-cancel").addEventListener("click", () => {
      limitsForm.hidden = true;
      requestButton.hidden = Boolean(pendingRequest);
    });
    limitsForm.addEventListener("submit", (event) => {
      event.preventDefault();
      runForm(limitsForm, "Sending…", async () => {
        const number = (name) => {
          const raw = limitsForm.elements[name].value.trim();
          return raw === "" ? null : Number(raw);
        };
        await rpc("developer_request_limits", {
          p_client_id: app.client_id,
          p_limits: {
            user_per_minute: number("user_per_minute"),
            app_per_minute: number("app_per_minute"),
            app_per_second: number("app_per_second"),
            realtime_connections: number("realtime_connections"),
          },
          p_reason: limitsForm.elements.reason.value.trim(),
        });
        notify("Request sent. PocketPass will review it; the decision appears on this page.");
        await loadApp();
      });
    });
    fragment.querySelector(".limits-table").append(table([
      { label: "Sent", render: (row) => fmtDate(row.created_at) },
      { label: "Requested", render: (row) => limitSummary(row.requested_limits) },
      { label: "Status", render: (row) => el("span", {
        class: `badge ${row.status === "approved" ? "badge-good" : (row.status === "denied" ? "badge-bad" : "badge-warn")}`,
        text: row.status,
      }) },
      { label: "Decision", render: (row) => {
        if (row.status === "pending") return "—";
        const lines = [el("div", { text: row.status === "approved" ? `Granted: ${limitSummary(row.granted_limits)}` : "Not granted" })];
        if (row.resolution_note) lines.push(el("div", { class: "muted", text: row.resolution_note }));
        lines.push(el("div", { class: "muted", text: fmtDate(row.resolved_at) }));
        return el("div", {}, lines);
      } },
    ], limitRequests, { empty: "No requests yet." }));

    const form = fragment.querySelector(".app-edit-form");
    form.elements.name.value = app.name || "";
    form.elements.description.value = app.description || "";
    form.elements.website.value = app.website || "";
    form.elements.logo_url.value = app.logo_url || "";
    form.elements.redirect_uris.value = redirectUris.join("\n");
    renderPermGrid(form.querySelector("[data-perm-grid]"), scopes);
    if (suspended) {
      form.querySelector("button[type=submit]").disabled = true;
      form.append(el("span", { class: "hint", text: HINTS.APP_SUSPENDED }));
    }
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      runForm(form, "Saving…", async () => {
        const args = readAppForm(form);
        const added = args.p_scopes.filter((key) => !scopes.includes(key));
        const connected = Number(app.connected_users || 0);
        if (added.length && connected > 0) {
          const message = `Adding ${added.join(", ")} will disconnect all ${plural(connected, "connected user")}. They stay signed out until they reconnect and approve the new permissions. Removing scopes does not disconnect anyone.`;
          if (!confirmAction(message)) return;
        }
        const result = await rpc("developer_update_app", { p_client_id: app.client_id, ...args });
        const revoked = Number(result.consents_revoked || 0);
        notify(revoked > 0 ? `Saved. ${plural(revoked, "connected user")} disconnected.` : "Saved.");
        await loadApp();
      });
    });

    root.append(fragment);
  };

  const loadApp = async () => {
    const id = state.app.id;
    if (!id) return;
    const list = await rpc("developer_list_apps");
    const app = (Array.isArray(list.items) ? list.items : []).find((row) => String(row.client_id).toLowerCase() === id);
    if (!app) {
      notify(HINTS.APP_NOT_FOUND, "error");
      location.hash = "#apps";
      return;
    }
    const usage = await rpc("developer_app_usage", { p_client_id: app.client_id, p_days: USAGE_DAYS });
    const limitRequests = await rpc("developer_list_limit_requests", { p_client_id: app.client_id });
    if (state.app.id !== id) return;
    state.app.data = app;
    state.app.usage = Array.isArray(usage.items) ? usage.items : [];
    state.app.limitRequests = Array.isArray(limitRequests.items) ? limitRequests.items : [];
    renderApp(app, state.app.usage);
  };

  let pending = 0;
  const run = async (task) => {
    pending += 1;
    document.body.style.cursor = "progress";
    try {
      await task();
    } catch (error) {
      notify(explain(error), "error");
    } finally {
      pending -= 1;
      if (pending === 0) document.body.style.cursor = "";
    }
  };
  const runForm = (form, busyLabel, task) => {
    const button = form.querySelector("button[type=submit]");
    if (button.disabled) return Promise.resolve();
    const label = button.textContent;
    button.disabled = true;
    button.textContent = busyLabel;
    return run(task).finally(() => {
      button.disabled = false;
      button.textContent = label;
    });
  };

  const routeFromHash = () => {
    const hash = location.hash.replace(/^#/, "");
    const match = /^app\/([0-9a-f-]{36})$/i.exec(hash);
    if (match) return { view: "app", id: match[1].toLowerCase() };
    return { view: "apps" };
  };

  const render = () => {
    if (!state.session || !state.me) {
      showView("login");
      return;
    }
    const route = routeFromHash();
    if (!(route.view === "app" && state.secret && state.secret.client_id === route.id)) state.secret = null;
    state.view = route.view;
    showView(route.view);
    run(async () => {
      if (route.view === "apps") await loadApps();
      else if (route.view === "app") {
        if (state.app.id !== route.id) {
          state.app = { id: route.id, data: null, usage: [] };
          clear($("app-detail"));
        }
        await loadApp();
      }
    });
  };

  const bootstrapSession = async () => {
    const fragment = consumeFragment();
    if (fragment && fragment.error) notify(fragment.error, "error");
    if (fragment && fragment.session) saveSession(fragment.session);
    else state.session = loadSession();
    if (!state.session) {
      render();
      return;
    }
    try {
      state.me = await rpc("developer_whoami");
      if (fragment && fragment.session) notify(`Signed in as ${state.me.display_name || "your PocketPass account"}.`);
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) notify(explain(error), "error");
      saveSession(null);
      state.me = null;
    }
    render();
  };

  const wire = () => {
    $("login-email-form").addEventListener("submit", (event) => {
      event.preventDefault();
      runForm(event.currentTarget, "Sending…", async () => {
        const email = $("login-email").value.trim().toLowerCase();
        await sendCode(email);
        state.lastLoginEmail = email;
        $("login-code-hint").textContent = `We sent a code to ${email}. It expires in 10 minutes.`;
        $("login-email-form").hidden = true;
        $("login-alt").hidden = true;
        $("login-code-form").hidden = false;
        $("login-code").value = "";
        $("login-code").focus();
      });
    });
    $("login-back").addEventListener("click", () => {
      $("login-code-form").hidden = true;
      $("login-email-form").hidden = false;
      $("login-alt").hidden = false;
      $("login-email").focus();
    });
    $("login-code-form").addEventListener("submit", (event) => {
      event.preventDefault();
      runForm(event.currentTarget, "Verifying…", async () => {
        await verifyCode(state.lastLoginEmail, $("login-code").value.trim());
        state.me = await rpc("developer_whoami");
        $("login-code-form").hidden = true;
        $("login-email-form").hidden = false;
        $("login-alt").hidden = false;
        notify(`Signed in as ${state.me.display_name || state.lastLoginEmail}.`);
      }).then(render);
    });
    $("login-discord").addEventListener("click", startDiscord);
    const passwordToggle = $("login-password-toggle");
    const passwordForm = $("login-password-form");
    if (passwordToggle && passwordForm) {
      passwordToggle.addEventListener("click", () => {
        passwordForm.hidden = !passwordForm.hidden;
        passwordToggle.textContent = passwordForm.hidden ? "Use a username and password" : "Hide username sign-in";
        if (!passwordForm.hidden) $("login-username").focus();
      });
      passwordForm.addEventListener("submit", (event) => {
        event.preventDefault();
        runForm(event.currentTarget, "Signing in…", async () => {
          const identifier = $("login-username").value.trim().toLowerCase();
          await signInWithPassword(identifier, $("login-password").value);
          $("login-password").value = "";
          state.me = await rpc("developer_whoami");
          notify(`Signed in as ${state.me.display_name || identifier}.`);
        }).then(render);
      });
    }
    $("sign-out").addEventListener("click", () => run(signOut));
    for (const link of document.querySelectorAll(".nav-link[data-view]")) {
      link.addEventListener("click", () => { location.hash = `#${link.dataset.view}`; });
    }
    for (const button of document.querySelectorAll("[data-refresh]")) {
      button.addEventListener("click", () => render());
    }
    $("app-back").addEventListener("click", () => { location.hash = "#apps"; });
    const createForm = $("app-create-form");
    createForm.addEventListener("submit", (event) => {
      event.preventDefault();
      runForm(createForm, "Registering…", async () => {
        const args = readAppForm(createForm);
        const clientType = createForm.querySelector("input[name=client_type]:checked");
        const result = await rpc("developer_create_app", { ...args, p_client_type: clientType ? clientType.value : "public" });
        const app = result.app || {};
        state.secret = result.client_secret ? { client_id: app.client_id, client_secret: result.client_secret } : null;
        createForm.reset();
        renderPermGrid(createForm.querySelector("[data-perm-grid]"), []);
        notify(result.client_secret ? `${app.name} registered. Copy the client secret now — it is shown once.` : `${app.name} registered.`);
        location.hash = `#app/${app.client_id}`;
      });
    });
    window.addEventListener("hashchange", render);
  };

  if (!secureApiUrl || !API_KEY) {
    notify("Developer portal requires a secure HTTPS API URL and key.", "error");
    return;
  }
  wire();
  bootstrapSession();
})();
