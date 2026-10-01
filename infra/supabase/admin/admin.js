(() => {
  "use strict";

  const API_URL = String(window.POCKETPASS_ADMIN_API_URL || "").replace(/\/+$/, "");
  const API_KEY = String(window.POCKETPASS_ADMIN_KEY || "");
  const secureApiUrl = (() => {
    try { const url = new URL(API_URL); return url.protocol === "https:" && url.origin === API_URL; }
    catch (_) { return false; }
  })();
  const STUDIO_URL = String(window.POCKETPASS_ADMIN_STUDIO_URL || "").replace(/\/+$/, "");
  const SESSION_KEY = "pp_admin_session";
  const PAGE_SIZE = 50;
  const AUDIT_PAGE_SIZE = 100;

  const ACHIEVEMENT_NAMES = {
    day_one: "Day One",
    saving_up: "Saving Up",
    icebreaker: "Icebreaker",
    streak: "Streak",
    plus_one: "Plus One",
    first_encounter: "First Encounter",
    small_world: "Small World",
    passport_stamped: "Passport Stamped",
    continental: "Continental",
    full_set: "Full Set",
    missing_piece: "Missing Piece",
  };

  const PERMISSIONS = [
    ["users", "View users & emails", "See the user list, search it, and open account details"],
    ["audit", "View audit log", "Read the audit log and the recent-actions panel"],
    ["legacy", "Legacy accounts", "Mark or unmark accounts as legacy (Day One)"],
    ["tokens", "Tokens", "Add or remove tokens on an account"],
    ["achievements", "Achievements", "Force-unlock or revoke achievements"],
    ["admins", "Manage admins", "Add, edit and remove other admins"],
    ["apps", "Developer apps", "Review and suspend third-party apps"],
    ["supporters", "Supporters", "Ko-fi payments and supporter status"],
    ["bans", "Bans", "Ban and unban accounts"],
    ["board_requests", "Board requests", "Approve or reject community proposals"],
    ["boards", "Board management", "Create and configure communities"],
    ["board_content", "Board content and reports", "Review content, resolve reports and moderate notes"],
    ["board_members", "Board members and staff", "Manage membership, restrictions, owners and moderators"],
    ["board_suspensions", "Global board suspensions", "Suspend participation across all boards"],
    ["board_private_review", "Private-board review", "Explicit access to private board content, with an access log"],
    ["board_delete", "Permanent board deletion", "Permanently purge a board and its content"],
    ["board_filters", "Board word filters", "Configure literal phrase filters and bio rejection rules"],
    ["board_stationery", "Stationery catalogue", "Manage stationery versions and access rules"],
    ["board_settings", "Board feature settings", "Enable Boards, proposals and per-account burst limits"],
  ];
  const PERMISSION_LABELS = Object.fromEntries(PERMISSIONS.map(([key, label]) => [key, label]));

  const STAT_TILES = [
    ["users", "Accounts"],
    ["profiles", "Profiles"],
    ["legacy_accounts", "Legacy accounts"],
    ["new_7d", "New (7 days)"],
    ["active_7d", "Active (7 days)"],
    ["friendships", "Friendships"],
    ["messages", "Messages"],
    ["encounters", "Encounters"],
    ["encounters_confirmed", "Confirmed encounters"],
    ["achievement_unlocks", "Achievement unlocks"],
    ["tokens_in_circulation", "Tokens in circulation"],
    ["admins", "Admins"],
  ];

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
    view: "overview",
    users: { query: "", offset: 0, total: 0, rows: [] },
    user: { id: null, data: null },
    audit: { offset: 0, rows: [] },
    admins: { rows: [], editing: null },
    apps: { query: "", offset: 0, total: 0, rows: [] },
    limitRequests: { status: "pending", rows: [] },
    supporters: { filter: "unmatched", events: [], rows: [] },
    bans: { status: "active", offset: 0, total: 0, rows: [] },
    blockedSignups: { offset: 0, total: 0, rows: [] },
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

  class ApiError extends Error {
    constructor(status, body) {
      super((body && (body.msg || body.message || body.error_description || body.error)) || `HTTP ${status}`);
      this.status = status;
      this.body = body || {};
      this.code = this.body.error_code || this.body.code || null;
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
    if (current && state.me && state.me.is_owner) {
      try {
        await rpc("admin_studio_sessions_revoke", {}, false);
      } catch (_) {
      }
    }
    saveSession(null);
    state.me = null;
    if (current && current.access_token) {
      try {
        await fetch(`${API_URL}/auth/v1/logout?scope=local`, {
          method: "POST",
          headers: { apikey: API_KEY, Authorization: `Bearer ${current.access_token}` },
        });
      } catch (_) {
      }
    }
    render();
  };

  const rpc = async (name, args = {}, retry = true) => {
    if (!state.session) throw new ApiError(401, { message: "Signed out" });
    if (state.session.expires_at * 1000 - Date.now() < 60_000) {
      try {
        await refreshSession();
      } catch (_) {
      }
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
    if (error.code === "otp_disabled") return "No PocketPass account uses that email.";
    if (error.code === "otp_expired") return "That code is wrong or has expired.";
    if (error.status === 429) return "Too many attempts. Wait a minute and try again.";
    if (error.code === "42501") {
      const match = /^Permission required: (\w+)$/.exec(error.message || "");
      if (match) return `You need the "${PERMISSION_LABELS[match[1]] || match[1]}" permission.`;
      if (/^(Owners are managed|Admins and owners cannot be banned|You cannot ban yourself)/.test(error.message || "")) return error.message;
      return "This account is not an admin.";
    }
    return error.message;
  };

  const fmtDate = (value) => {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
  };
  const fmtNumber = (value) => (value === null || value === undefined ? "—" : Number(value).toLocaleString());
  const fmtMoney = (amount, currency) => (amount === null || amount === undefined ? "—" : `${Number(amount).toFixed(2)} ${currency || ""}`.trim());
  const toLocalInput = (date) => {
    const pad = (value) => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  };
  const shortId = (id) => (id ? `${String(id).slice(0, 8)}…` : "—");
  const displayName = (row) => row.display_name || row.username || row.email || shortId(row.user_id);
  const appLabel = (payload) => (payload.name ? `“${payload.name}”` : shortId(payload.client_id));

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

  const describeAction = (entry) => {
    const payload = entry.payload || {};
    switch (entry.action) {
      case "set_legacy_account":
        return payload.legacy ? "Marked as legacy account" : "Removed legacy flag";
      case "adjust_tokens":
        return `${payload.delta > 0 ? "+" : ""}${payload.delta} tokens (${payload.balance_before} → ${payload.balance_after}) — ${payload.reason || ""}`;
      case "set_achievement":
        return `${payload.unlocked ? "Unlocked" : "Revoked"} ${ACHIEVEMENT_NAMES[payload.key] || payload.key}${payload.changed === false ? " (no change)" : ""}`;
      case "set_developer_app_status":
        return `${payload.status === "suspended" ? "Suspended" : "Reactivated"} app ${appLabel(payload)}`;
      case "resolve_limit_request":
        return `${payload.approved ? "Approved" : "Denied"} limit request for ${appLabel(payload)}${payload.approved ? ` — ${limitSummary(payload.granted)}` : ""}`;
      case "link_kofi_email":
        return `Linked Ko-fi payer to account${payload.applied_events ? ` — ${payload.applied_events} payment(s) applied, hats unlocked until ${fmtDate(payload.active_until)}` : ""}`;
      case "unlink_kofi_email":
        return "Unlinked Ko-fi payer from account";
      case "set_supporter":
        return `Supporter status ${fmtDate(payload.old)} → ${fmtDate(payload.new)} — ${payload.reason || ""}`;
      case "ban_account":
        return `Banned ${payload.ends_at ? `until ${fmtDate(payload.ends_at)}` : "permanently"}${payload.reason ? ` — ${payload.reason}` : ""}`;
      case "lift_ban":
        return `Unbanned${payload.note ? ` — ${payload.note}` : ""}`;
      default:
        return entry.action;
    }
  };

  const auditColumns = (withTarget) => {
    const columns = [
      { label: "When", render: (row) => fmtDate(row.created_at) },
      { label: "Admin", render: (row) => row.admin_email || shortId(row.admin_id) },
    ];
    if (withTarget) {
      columns.push({
        label: "Account",
        render: (row) => el("a", {
          href: `#user/${row.target_user_id}`,
          text: row.target_username || row.target_email || shortId(row.target_user_id),
        }),
      });
    }
    columns.push({ label: "Action", render: describeAction });
    return columns;
  };

  const can = (permission) => Boolean(
    state.me && (state.me.is_owner || (Array.isArray(state.me.permissions) && state.me.permissions.includes(permission))),
  );
  const VIEW_PERMISSION = { users: "users", user: "users", audit: "audit", admins: "admins", apps: "apps", supporters: "supporters", bans: "bans" };
  const viewAllowed = (name) => name === "boards" ? PERMISSIONS.some(([key]) => (key === "boards" || key.startsWith("board_")) && can(key)) : !VIEW_PERMISSION[name] || can(VIEW_PERMISSION[name]);

  const views = ["login", "forbidden", "overview", "users", "user", "audit", "admins", "apps", "supporters", "bans", "boards"];
  const showView = (name) => {
    for (const view of views) $(`view-${view}`).hidden = view !== name;
    const authScreen = name === "login" || name === "forbidden";
    $("login-shell").hidden = !authScreen;
    $("app-shell").hidden = authScreen;
    if (state.session) $("session-email").textContent = (state.me && state.me.email) || (state.session.user && state.session.user.email) || "";
    const studio = $("open-studio");
    studio.hidden = !(STUDIO_URL && state.me && state.me.is_owner);
    for (const link of document.querySelectorAll(".nav-link")) {
      const target = link.dataset.view;
      link.hidden = !viewAllowed(target);
      link.classList.toggle("is-active", target === name || (target === "users" && name === "user"));
    }
  };

  const loadOverview = async () => {
    const auditAllowed = can("audit");
    const [stats, audit] = await Promise.all([
      rpc("admin_stats"),
      auditAllowed ? rpc("admin_list_audit", { p_limit: 15 }) : Promise.resolve([]),
    ]);
    $("overview-audit").closest(".panel").hidden = !auditAllowed;
    const tiles = $("stats");
    clear(tiles);
    for (const [key, label] of STAT_TILES) {
      tiles.append(el("div", { class: "tile" }, [
        el("div", { class: "tile-value", text: fmtNumber(stats[key]) }),
        el("div", { class: "tile-label", text: label }),
      ]));
    }
    const chips = $("unlocks-by-key");
    clear(chips);
    const byKey = stats.unlocks_by_key || {};
    for (const key of Object.keys(ACHIEVEMENT_NAMES)) {
      chips.append(el("span", { class: "chip" }, [ACHIEVEMENT_NAMES[key], el("strong", { text: fmtNumber(byKey[key] || 0) })]));
    }
    const wrap = $("overview-audit");
    clear(wrap);
    wrap.append(table(auditColumns(true), audit, { empty: "No admin actions recorded yet." }));
    stampOverview();
  };

  const OVERVIEW_REFRESH_MS = 45_000;
  let overviewTimer = null;
  const stampOverview = () => {
    const stamp = $("overview-updated");
    if (!stamp) return;
    stamp.textContent = `Updated ${new Date().toLocaleTimeString()}`;
  };
  const overviewIsLive = () =>
    state.view === "overview" &&
    Boolean(state.session) &&
    Boolean(state.me && state.me.is_admin) &&
    document.visibilityState === "visible";
  const stopOverviewRefresh = () => {
    if (overviewTimer === null) return;
    clearInterval(overviewTimer);
    overviewTimer = null;
  };
  const scheduleOverviewRefresh = () => {
    stopOverviewRefresh();
    if (!overviewIsLive()) return;
    overviewTimer = setInterval(() => {
      if (!overviewIsLive()) {
        stopOverviewRefresh();
        return;
      }
      loadOverview().catch(() => {});
    }, OVERVIEW_REFRESH_MS);
  };

  const loadUsers = async () => {
    const rows = await rpc("admin_list_users", {
      p_search: state.users.query || null,
      p_limit: PAGE_SIZE,
      p_offset: state.users.offset,
    });
    state.users.rows = rows;
    state.users.total = rows.length ? Number(rows[0].total_count) : 0;
    const wrap = $("users-table");
    clear(wrap);
    wrap.append(table([
      { label: "Name", render: (row) => el("div", {}, [
        el("div", {}, [
          el("strong", { text: displayName(row) }),
          " ",
          row.is_admin ? el("span", { class: "badge badge-admin", text: "admin" }) : null,
          row.legacy_account ? el("span", { class: "badge badge-warn", text: "legacy" }) : null,
        ]),
        el("div", { class: "muted", text: row.username ? `@${row.username}` : "no profile" }),
      ]) },
      { label: "Email", key: "email" },
      { label: "Country", key: "country_code" },
      { label: "Joined", render: (row) => fmtDate(row.created_at) },
      { label: "Last seen", render: (row) => fmtDate(row.last_seen_at) },
      { label: "Tokens", numeric: true, render: (row) => fmtNumber(row.token_balance) },
      { label: "Achievements", numeric: true, render: (row) => `${row.achievements_unlocked}/11` },
      { label: "Friends", numeric: true, key: "friend_count" },
      { label: "Encounters", numeric: true, key: "encounter_count" },
    ], rows, {
      empty: state.users.query ? "No accounts match that search." : "No accounts yet.",
      onRow: (row) => { location.hash = `#user/${row.user_id}`; },
    }));
    const start = state.users.total ? state.users.offset + 1 : 0;
    const end = Math.min(state.users.offset + rows.length, state.users.total);
    $("users-page").textContent = `${start}–${end} of ${state.users.total}`;
    $("users-prev").disabled = state.users.offset === 0;
    $("users-next").disabled = state.users.offset + PAGE_SIZE >= state.users.total;
  };

  const loadAudit = async () => {
    const rows = await rpc("admin_list_audit", { p_limit: AUDIT_PAGE_SIZE, p_offset: state.audit.offset });
    state.audit.rows = rows;
    const wrap = $("audit-table");
    clear(wrap);
    wrap.append(table(auditColumns(true), rows, { empty: "No admin actions recorded yet." }));
    $("audit-page").textContent = rows.length ? `${state.audit.offset + 1}–${state.audit.offset + rows.length}` : "";
    $("audit-prev").disabled = state.audit.offset === 0;
    $("audit-next").disabled = rows.length < AUDIT_PAGE_SIZE;
  };

  const confirmAction = (message) => window.confirm(message);

  const LIMIT_FIELDS = [
    ["user_per_minute", "per user / min"],
    ["app_per_minute", "per app / min"],
    ["app_per_second", "per app / s"],
  ];
  const limitValue = (values, key) => (values && values[key] !== null && values[key] !== undefined ? fmtNumber(values[key]) : "—");
  const limitSummary = (values) => (values ? LIMIT_FIELDS.map(([key, label]) => `${limitValue(values, key)} ${label}`).join(" · ") : "—");
  const limitLines = (values) => el("div", {}, LIMIT_FIELDS.map(([key, label]) => el("div", { text: `${limitValue(values, key)} ${label}` })));

  const resolveEditor = (row, cell) => {
    clear(cell);
    const inputs = {};
    const fields = LIMIT_FIELDS.map(([key, label]) => {
      const input = el("input", {
        type: "number",
        min: "1",
        max: "1000000",
        step: "1",
        value: row.requested_limits && row.requested_limits[key] !== null && row.requested_limits[key] !== undefined ? String(row.requested_limits[key]) : "",
      });
      inputs[key] = input;
      return el("label", { class: "field" }, [el("span", { text: `Grant ${label}` }), input]);
    });
    const note = el("input", { type: "text", maxlength: "1000", placeholder: "Note for the developer (optional)" });
    cell.append(
      el("div", { class: "stack" }, fields),
      el("label", { class: "field" }, [el("span", { text: "Note" }), note]),
      el("div", { class: "actions" }, [
        el("button", {
          type: "button",
          class: "btn btn-small btn-green",
          text: "Confirm approval",
          onclick: () => run(async () => {
            const limits = {};
            for (const [key] of LIMIT_FIELDS) {
              const raw = inputs[key].value.trim();
              limits[key] = raw === "" ? null : Number(raw);
            }
            await rpc("admin_resolve_limit_request", { p_request_id: row.id, p_approve: true, p_note: note.value.trim(), p_limits: limits });
            notify(`Approved limits for “${row.app_name}”.`);
            await loadLimitRequests();
            await loadApps();
          }),
        }),
        el("button", { type: "button", class: "btn btn-small btn-grey", text: "Cancel", onclick: () => run(loadLimitRequests) }),
      ]),
    );
  };

  const loadLimitRequests = async () => {
    const filter = state.limitRequests;
    const result = await rpc("admin_list_limit_requests", { p_status: filter.status, p_limit: 100, p_offset: 0 });
    const rows = result.items || [];
    filter.rows = rows;
    for (const button of document.querySelectorAll("#limit-requests-filter button")) {
      button.className = `btn btn-small ${button.dataset.status === filter.status ? "btn-green" : "btn-grey"}`;
    }
    const wrap = $("limit-requests-table");
    clear(wrap);
    wrap.append(table([
      { label: "App", render: (row) => el("div", {}, [
        el("div", {}, [el("strong", { text: row.app_name || shortId(row.client_id) })]),
        el("div", { class: "muted" }, [el("code", { text: row.client_id })]),
      ]) },
      { label: "Developer", render: (row) => (can("users")
        ? el("a", { href: `#user/${row.owner_user_id}`, text: row.owner_display_name || shortId(row.owner_user_id) })
        : row.owner_display_name || shortId(row.owner_user_id)) },
      { label: "Current", render: (row) => limitLines(row.current_limits) },
      { label: "Requested", render: (row) => limitLines(row.requested_limits) },
      { label: "Reason", render: (row) => el("div", { class: "muted", text: row.reason || "" }) },
      { label: "Sent", render: (row) => fmtDate(row.created_at) },
      { label: "Decision", render: (row) => {
        if (row.status === "pending") return el("span", { class: "badge badge-warn", text: "pending" });
        return el("div", {}, [
          el("span", { class: `badge ${row.status === "approved" ? "badge-good" : "badge-bad"}`, text: row.status }),
          el("div", { class: "muted", text: row.status === "approved" ? limitSummary(row.granted_limits) : "" }),
          el("div", { class: "muted", text: row.resolution_note || "" }),
          el("div", { class: "muted", text: fmtDate(row.resolved_at) }),
        ]);
      } },
      { label: "", render: (row) => {
        if (row.status !== "pending") return "";
        const cell = el("div", { class: "actions" });
        cell.append(
          el("button", { type: "button", class: "btn btn-small btn-green", text: "Approve…", onclick: () => resolveEditor(row, cell) }),
          el("button", {
            type: "button",
            class: "btn btn-small btn-red",
            text: "Deny",
            onclick: () => run(async () => {
              const note = window.prompt(`Deny the request from “${row.app_name}”? Add a note for the developer (optional):`, "");
              if (note === null) return;
              await rpc("admin_resolve_limit_request", { p_request_id: row.id, p_approve: false, p_note: note.trim() });
              notify(`Denied the request from “${row.app_name}”.`);
              await loadLimitRequests();
            }),
          }),
        );
        return cell;
      } },
    ], rows, { empty: `No ${filter.status} limit requests.` }));
  };

  const loadApps = async () => {
    const result = await rpc("admin_list_developer_apps", {
      p_query: state.apps.query,
      p_limit: PAGE_SIZE,
      p_offset: state.apps.offset,
    });
    const rows = result.items || [];
    state.apps.rows = rows;
    state.apps.total = Number(result.total_count || 0);
    const wrap = $("apps-table");
    clear(wrap);
    wrap.append(table([
      { label: "App", render: (row) => el("div", {}, [
        el("div", {}, [el("strong", { text: row.name })]),
        el("div", { class: "muted" }, [el("code", { text: row.client_id })]),
      ]) },
      { label: "Owner", render: (row) => (can("users")
        ? el("a", { href: `#user/${row.owner_user_id}`, text: row.owner_display_name || shortId(row.owner_user_id) })
        : row.owner_display_name || shortId(row.owner_user_id)) },
      { label: "Type", key: "client_type" },
      { label: "Status", render: (row) => el("span", {
        class: `badge ${row.status === "active" ? "badge-good" : "badge-bad"}`,
        text: row.status,
      }) },
      { label: "Scopes", render: (row) => el("div", { class: "chips" },
        (row.scopes || []).map((scope) => el("span", { class: "chip chip-perm", text: scope }))) },
      { label: "Connected users", numeric: true, render: (row) => fmtNumber(row.connected_users) },
      { label: "Requests (30d)", numeric: true, render: (row) => fmtNumber(row.requests_30d) },
      { label: "Created", render: (row) => fmtDate(row.created_at) },
      { label: "", render: (row) => el("div", { class: "actions" }, [
        el("button", {
          type: "button",
          class: `btn btn-small ${row.status === "active" ? "btn-red" : "btn-green"}`,
          text: row.status === "active" ? "Suspend" : "Reactivate",
          onclick: () => run(async () => {
            const suspend = row.status === "active";
            if (!confirmAction(suspend
              ? `Suspend “${row.name}”? Its tokens stop working right away and every request is refused until it is reactivated.`
              : `Reactivate “${row.name}”? It can sign users in again; people who connected before keep their approval.`)) return;
            await rpc("admin_set_developer_app_status", { p_client_id: row.client_id, p_status: suspend ? "suspended" : "active" });
            notify(suspend ? `“${row.name}” suspended.` : `“${row.name}” reactivated.`);
            await loadApps();
          }),
        }),
      ]) },
    ], rows, {
      empty: state.apps.query ? "No apps match that search." : "No developer apps registered yet.",
    }));
    const start = state.apps.total ? state.apps.offset + 1 : 0;
    const end = Math.min(state.apps.offset + rows.length, state.apps.total);
    $("apps-page").textContent = `${start}–${end} of ${state.apps.total}`;
    $("apps-prev").disabled = state.apps.offset === 0;
    $("apps-next").disabled = state.apps.offset + PAGE_SIZE >= state.apps.total;
  };

  const userLink = (userId, label) => (can("users") ? el("a", { href: `#user/${userId}`, text: label }) : label);
  const supporterLabel = (row) => (row.username ? `@${row.username}` : (row.display_name || shortId(row.user_id)));

  const linkKofiEmail = (row) => run(async () => {
    const username = (window.prompt(`Link ${row.email} to which PocketPass username?`, "") || "").trim().replace(/^@/, "").toLowerCase();
    if (!username) return;
    const users = await rpc("admin_list_users", { p_search: username, p_limit: PAGE_SIZE, p_offset: 0 });
    const matches = users.filter((user) => String(user.username || "").toLowerCase() === username);
    if (matches.length !== 1) {
      throw new Error(matches.length ? `Several accounts match @${username}; narrow it down on the Users page.` : `No account has the username @${username}.`);
    }
    const user = matches[0];
    if (!confirmAction(`Link ${row.email} to ${displayName(user)} (@${user.username})? Every membership payment from that email unlocks their hats from now on.`)) return;
    const result = await rpc("admin_link_kofi_email", { p_email: row.email, p_user_id: user.user_id });
    notify(result.applied_events
      ? `Linked ${row.email}: ${result.applied_events} payment(s) applied, hats unlocked until ${fmtDate(result.active_until)}.`
      : `Linked ${row.email}.`);
    await loadKofiEvents();
    await loadSupporters();
  });

  const unlinkKofiEmail = (email) => run(async () => {
    if (!confirmAction(`Unlink ${email}? Payments from it stop unlocking hats for that account; time already granted stays.`)) return;
    await rpc("admin_unlink_kofi_email", { p_email: email });
    notify(`Unlinked ${email}.`);
    await loadSupporters();
    await loadKofiEvents();
  });

  const loadKofiEvents = async () => {
    const filter = state.supporters.filter;
    const result = await rpc("admin_list_kofi_events", { p_filter: filter, p_limit: 100, p_offset: 0 });
    const rows = result.items || [];
    state.supporters.events = rows;
    for (const button of document.querySelectorAll("#kofi-events-filter button")) {
      button.className = `btn btn-small ${button.dataset.filter === filter ? "btn-green" : "btn-grey"}`;
    }
    $("kofi-events-count").textContent = rows.length ? `${rows.length} of ${fmtNumber(result.total)}` : "";
    const wrap = $("kofi-events-table");
    clear(wrap);
    wrap.append(table([
      { label: "Paid", render: (row) => fmtDate(row.paid_at) },
      { label: "Type", render: (row) => el("div", {}, [
        el("span", { class: `badge ${row.qualifies ? "badge-good" : ""}`, text: row.event_type || "—" }),
        el("div", { class: "muted", text: row.tier_name || "" }),
      ]) },
      { label: "From", key: "from_name" },
      { label: "Email", render: (row) => (row.email ? el("code", { text: row.email }) : "—") },
      { label: "Amount", numeric: true, render: (row) => fmtMoney(row.amount, row.currency) },
      { label: "Status", render: (row) => el("div", {}, [
        row.user_id
          ? userLink(row.user_id, supporterLabel(row))
          : el("span", { class: "badge badge-warn", text: "Unmatched" }),
        row.error ? el("div", { class: "muted", text: `Error: ${row.error}` }) : null,
      ]) },
      { label: "Unlocked until", render: (row) => fmtDate(row.granted_until) },
      { label: "", render: (row) => (row.user_id || !row.email ? "" : el("div", { class: "actions" }, [
        el("button", { type: "button", class: "btn btn-small btn-green", text: "Link…", onclick: () => linkKofiEmail(row) }),
      ])) },
    ], rows, { empty: filter === "unmatched" ? "Every Ko-fi payment is matched to an account." : "No Ko-fi payments received yet." }));
  };

  const loadSupporters = async () => {
    const result = await rpc("admin_list_supporters", { p_limit: 100, p_offset: 0 });
    const rows = result.items || [];
    state.supporters.rows = rows;
    const wrap = $("supporters-table");
    clear(wrap);
    wrap.append(table([
      { label: "User", render: (row) => el("div", {}, [
        el("div", {}, [el("strong", {}, userLink(row.user_id, row.display_name || row.username || shortId(row.user_id)))]),
        el("div", { class: "muted", text: row.username ? `@${row.username}` : "" }),
      ]) },
      { label: "Until", render: (row) => {
        const active = row.active_until && new Date(row.active_until).getTime() > Date.now();
        return el("div", {}, [
          el("span", { class: `badge ${active ? "badge-good" : "badge-bad"}`, text: active ? "active" : "lapsed" }),
          el("div", { class: "muted", text: fmtDate(row.active_until) }),
        ]);
      } },
      { label: "Source", key: "source" },
      { label: "Last payment", render: (row) => el("div", {}, [
        el("div", { text: fmtDate(row.last_paid_at) }),
        el("div", { class: "muted", text: row.last_tier_name || "" }),
      ]) },
      { label: "Ko-fi emails", render: (row) => ((row.kofi_emails || []).length
        ? el("div", {}, row.kofi_emails.map((email) => el("div", { class: "actions" }, [
          el("code", { text: email }),
          el("button", { type: "button", class: "btn btn-small btn-grey", text: "Unlink", onclick: () => unlinkKofiEmail(email) }),
        ])))
        : el("span", { class: "muted", text: "account email" })) },
    ], rows, { empty: "No supporters yet." }));
  };

  const SIGNUP_LABELS = { email: "Email", discord: "Discord", username: "Username", network: "Network" };

  const showPage = (prefix, page, count) => {
    const start = page.total ? page.offset + 1 : 0;
    const end = Math.min(page.offset + count, page.total);
    $(`${prefix}-page`).textContent = `${start}–${end} of ${page.total}`;
    $(`${prefix}-prev`).disabled = page.offset === 0;
    $(`${prefix}-next`).disabled = page.offset + PAGE_SIZE >= page.total;
  };

  const accountCell = (row) => el("div", {}, [
    el("div", {}, [el("strong", {}, userLink(row.user_id, displayName(row)))]),
    el("div", { class: "muted", text: row.username ? `@${row.username}` : "" }),
  ]);

  const banEnds = (row) => {
    if (row.lifted_at) {
      const liftedBy = row.lifted_by_name || (row.lifted_by ? shortId(row.lifted_by) : "");
      return el("div", {}, [
        el("span", { class: "badge badge-good", text: "lifted" }),
        el("div", { class: "muted", text: `Lifted ${fmtDate(row.lifted_at)}${liftedBy ? ` by ${liftedBy}` : ""}` }),
        row.lift_note ? el("div", { class: "muted", text: row.lift_note }) : null,
      ]);
    }
    return el("div", {}, [
      el("span", { class: `badge ${row.active ? "badge-bad" : ""}`, text: row.active ? "active" : "ended" }),
      el("div", { class: "muted", text: row.ends_at ? fmtDate(row.ends_at) : "Permanent" }),
    ]);
  };

  const liftBan = (row, label, reload) => run(async () => {
    const note = window.prompt(`Unban ${label}? Add a note (optional):`, "");
    if (note === null) return;
    if (note.trim().length > 300) throw new Error("The note must be 300 characters or fewer.");
    await rpc("admin_lift_ban", { p_ban_id: row.id, p_note: note.trim() || null });
    notify(`${label} unbanned.`);
    await reload();
  });

  const banColumns = (withUser, reload) => {
    const columns = withUser ? [{ label: "User", render: accountCell }] : [];
    columns.push(
      { label: "Reason", key: "reason" },
      { label: "Staff note", render: (row) => row.staff_note || null },
      { label: "Banned by", render: (row) => row.banned_by_name || shortId(row.banned_by) },
      { label: "Banned", render: (row) => fmtDate(row.created_at) },
      { label: "Ends", render: banEnds },
    );
    if (reload) {
      columns.push({ label: "", render: (row) => (row.active ? el("div", { class: "actions" }, [
        el("button", { type: "button", class: "btn btn-small btn-red", text: "Unban", onclick: () => liftBan(row, displayName(row), reload) }),
      ]) : "") });
    }
    return columns;
  };

  const loadBans = async () => {
    const filter = state.bans;
    const rows = await rpc("admin_list_bans", { p_status: filter.status, p_limit: PAGE_SIZE, p_offset: filter.offset });
    if (!rows.length && filter.offset > 0) {
      filter.offset = Math.max(0, filter.offset - PAGE_SIZE);
      return loadBans();
    }
    filter.rows = rows;
    filter.total = rows.length ? Number(rows[0].total_count) : 0;
    for (const button of document.querySelectorAll("#bans-filter button")) {
      button.className = `btn btn-small ${button.dataset.status === filter.status ? "btn-green" : "btn-grey"}`;
    }
    const wrap = $("bans-table");
    clear(wrap);
    wrap.append(table(banColumns(true, loadBans), rows, { empty: filter.status === "active" ? "No active bans." : "No bans yet." }));
    showPage("bans", filter, rows.length);
  };

  const loadBlockedSignups = async () => {
    const page = state.blockedSignups;
    const rows = await rpc("admin_list_blocked_signups", { p_limit: PAGE_SIZE, p_offset: page.offset });
    page.rows = rows;
    page.total = rows.length ? Number(rows[0].total_count) : 0;
    const wrap = $("blocked-signups-table");
    clear(wrap);
    wrap.append(table([
      { label: "Time", render: (row) => fmtDate(row.created_at) },
      { label: "Method", render: (row) => SIGNUP_LABELS[row.method] || row.method },
      { label: "Matched on", render: (row) => SIGNUP_LABELS[row.kind] || row.kind },
      { label: "Banned account", render: (row) => (row.user_id ? accountCell(row) : null) },
    ], rows, { empty: "No blocked sign-ups." }));
    showPage("blocked-signups", page, rows.length);
  };

  const renderUserBans = (fragment, data, bans, name) => {
    const panel = fragment.querySelector(".ban-panel");
    const historyPanel = fragment.querySelector(".ban-history-panel");
    if (!bans) {
      panel.remove();
      historyPanel.remove();
      return;
    }
    const activeBan = bans.find((ban) => ban.active);
    const current = panel.querySelector(".ban-current");
    const form = panel.querySelector(".ban-form");
    panel.querySelector(".ban-status").textContent = activeBan ? "Banned" : "Not banned";
    if (activeBan) {
      form.remove();
      const facts = current.querySelector(".ban-facts");
      const fact = (label, value) => facts.append(el("dt", { text: label }), el("dd", { text: value || "—" }));
      fact("Reason", activeBan.reason);
      fact("Staff note", activeBan.staff_note);
      fact("Banned by", activeBan.banned_by_name || shortId(activeBan.banned_by));
      fact("Banned", fmtDate(activeBan.created_at));
      fact("Ends", activeBan.ends_at ? fmtDate(activeBan.ends_at) : "Permanent");
      current.querySelector(".ban-lift").addEventListener("click", () => liftBan(activeBan, name, loadUser));
    } else if (data.is_admin) {
      current.remove();
      form.remove();
      panel.append(el("span", { class: "hint", text: "Admins can't be banned." }));
    } else {
      current.remove();
      form.addEventListener("submit", (event) => {
        event.preventDefault();
        run(async () => {
          const reason = form.elements.reason.value.trim();
          const note = form.elements.note.value.trim();
          const duration = form.elements.duration;
          if (!reason) throw new Error("Enter a reason.");
          const length = duration.value === "permanent" ? "permanently" : `for ${duration.selectedOptions[0].textContent}`;
          if (!confirmAction(`Ban ${name} ${length}? They will be signed out of everything and hidden from other players.`)) return;
          await rpc("admin_ban_account", {
            p_user_id: data.user_id,
            p_reason: reason,
            p_staff_note: note || null,
            p_duration: duration.value,
          });
          notify("Account banned.");
          await loadUser();
        });
      });
    }
    historyPanel.querySelector(".ban-history").append(table(banColumns(false), bans, { empty: "No bans." }));
  };

  const TOKEN_SOURCES = {
    steps: "Steps",
    encounter: "Nearby encounters",
    bingo: "Bingo",
    staff: "Staff changes",
    shop: "Shop",
    puzzle: "Puzzle pieces",
    stationery: "Board stationery",
  };

  const tokenDetail = (row) => {
    const detail = row.detail || {};
    switch (row.source) {
      case "steps":
        return `${fmtNumber(detail.steps)} steps on ${detail.day}`;
      case "encounter": {
        const partner = detail.partner_name || (detail.partner_username ? `@${detail.partner_username}` : null) || shortId(detail.partner_id);
        return el("span", {}, ["Passed ", detail.partner_id ? userLink(detail.partner_id, partner) : partner]);
      }
      case "bingo":
        return `Week ${detail.week}: ${detail.lines} ${detail.lines === 1 ? "line" : "lines"}${detail.blackout ? " and blackout" : ""}`;
      case "staff":
        return `${detail.reason || "No reason"} (by ${detail.admin_name || (detail.admin_username ? `@${detail.admin_username}` : shortId(detail.admin_id))})`;
      case "puzzle":
        return `Piece ${Number(detail.piece) + 1} of ${detail.puzzle}`;
      default:
        return detail.item || "—";
    }
  };

  const renderTokenHistory = (fragment, history) => {
    const panel = fragment.querySelector(".token-history-panel");
    if (!history) {
      panel.querySelector(".token-history").append(el("span", { class: "hint", text: "You do not have the Tokens permission." }));
      return;
    }
    const totals = history.totals || {};
    const facts = panel.querySelector(".token-totals");
    const fact = (label, value) => facts.append(el("dt", { text: label }), el("dd", { text: value }));
    const signed = (value) => `${value > 0 ? "+" : ""}${fmtNumber(value)}`;
    Object.entries(TOKEN_SOURCES).forEach(([key, label]) => {
      if (totals[key]) fact(label, signed(totals[key]));
    });
    const listed = Object.values(totals).reduce((sum, value) => sum + Number(value || 0), 0);
    const other = Number(history.balance || 0) - listed;
    if (other) fact("Not itemised", signed(other));
    fact("Balance", fmtNumber(history.balance));
    const entries = history.entries || [];
    if (history.entry_count > entries.length) {
      panel.querySelector(".token-history-note").textContent = `Showing the newest ${entries.length} of ${fmtNumber(history.entry_count)} entries.`;
    } else if (other) {
      panel.querySelector(".token-history-note").textContent = "Not itemised covers tokens with no record left, such as rewards from passing accounts that were later deleted.";
    }
    panel.querySelector(".token-history").append(table([
      { label: "When", render: (row) => fmtDate(row.at) },
      { label: "Source", render: (row) => TOKEN_SOURCES[row.source] || row.source },
      { label: "Details", render: tokenDetail },
      { label: "Tokens", numeric: true, render: (row) => el("span", { class: `badge ${row.amount > 0 ? "badge-good" : "badge-bad"}`, text: signed(row.amount) }) },
    ], entries, { empty: "No token activity yet." }));
  };

  const renderUser = (data, bans, history) => {
    const root = $("user-detail");
    clear(root);
    const fragment = $("tpl-user-detail").content.cloneNode(true);
    const profile = data.profile || {};
    const name = profile.display_name || profile.username || data.email || shortId(data.user_id);
    fragment.querySelector(".user-name").textContent = name;
    fragment.querySelector(".user-sub").textContent = [profile.username ? `@${profile.username}` : "no profile", data.email].filter(Boolean).join(" · ");
    const badges = fragment.querySelector(".user-badges");
    if (data.is_admin) badges.append(el("span", { class: "badge badge-admin", text: "admin" }));
    if (profile.legacy_account) badges.append(el("span", { class: "badge badge-warn", text: "legacy account" }));
    if (!data.profile) badges.append(el("span", { class: "badge", text: "auth user without profile" }));
    if (bans && bans.some((ban) => ban.active)) badges.append(el("span", { class: "badge badge-bad", text: "banned" }));

    const facts = fragment.querySelector(".user-facts");
    const fact = (label, value) => {
      facts.append(el("dt", { text: label }));
      facts.append(el("dd", {}, value instanceof Node ? value : (value === null || value === undefined || value === "" ? "—" : String(value))));
    };
    fact("User id", el("code", { text: data.user_id }));
    fact("Email", data.email);
    fact("Email confirmed", fmtDate(data.email_confirmed_at));
    fact("Providers", Array.isArray(data.providers) ? data.providers.join(", ") : "—");
    fact("Signed up", fmtDate(data.created_at));
    fact("Last sign-in", fmtDate(data.last_sign_in_at));
    fact("Last seen", fmtDate(profile.last_seen_at));
    fact("Country", profile.country_code);
    fact("Age", profile.age);
    fact("Bio", profile.bio);
    fact("Friends", fmtNumber(data.counts && data.counts.friends));
    fact("Confirmed encounters", fmtNumber(data.counts && data.counts.encounters_confirmed));
    fact("Messages sent", fmtNumber(data.counts && data.counts.messages_sent));

    const legacyStatus = fragment.querySelector(".legacy-status");
    const legacyToggle = fragment.querySelector(".legacy-toggle");
    legacyStatus.textContent = profile.legacy_account ? "Legacy account" : "Not a legacy account";
    legacyToggle.textContent = profile.legacy_account ? "Remove legacy flag" : "Mark as legacy account";
    legacyToggle.disabled = !data.profile || !can("legacy");
    if (!can("legacy")) legacyToggle.after(el("span", { class: "hint", text: "You do not have the Legacy accounts permission." }));
    legacyToggle.addEventListener("click", () => run(async () => {
      const next = !profile.legacy_account;
      if (!confirmAction(`${next ? "Mark" : "Unmark"} ${name} as a legacy account?`)) return;
      await rpc("admin_set_legacy_account", { p_user_id: data.user_id, p_legacy: next });
      notify(next ? `${name} is now a legacy account (Day One unlocked).` : `Legacy flag removed for ${name}.`);
      await loadUser();
    }));

    fragment.querySelector(".token-balance").textContent = `${fmtNumber(data.token_balance)} tokens`;
    const tokenForm = fragment.querySelector(".token-form");
    tokenForm.addEventListener("submit", (event) => {
      event.preventDefault();
      run(async () => {
        const delta = Number(tokenForm.elements.delta.value);
        const reason = tokenForm.elements.reason.value.trim();
        const message = tokenForm.elements.message.value.trim();
        if (!Number.isInteger(delta) || delta === 0) throw new Error("Enter a non-zero whole number of tokens.");
        if (!reason) throw new Error("Enter a reason for the audit log.");
        if (!confirmAction(`${delta > 0 ? "Add" : "Remove"} ${Math.abs(delta)} tokens ${delta > 0 ? "to" : "from"} ${name}?`)) return;
        const result = await rpc("admin_adjust_tokens", {
          p_user_id: data.user_id,
          p_delta: delta,
          p_reason: reason,
          p_user_message: message || null,
        });
        notify(`Balance for ${name}: ${result.balance_before} → ${result.balance_after} tokens.`);
        await loadUser();
      });
    });
    fragment.querySelector(".token-zero").addEventListener("click", () => {
      tokenForm.elements.delta.value = String(-Number(data.token_balance || 0));
      tokenForm.elements.delta.focus();
    });
    if (!data.profile || !can("tokens")) tokenForm.querySelector("button[type=submit]").disabled = true;
    if (!can("tokens")) tokenForm.append(el("span", { class: "hint", text: "You do not have the Tokens permission." }));
    renderTokenHistory(fragment, history);

    const supporterUntil = data.supporter_until ? new Date(data.supporter_until) : null;
    const supporterActive = Boolean(supporterUntil && supporterUntil.getTime() > Date.now());
    fragment.querySelector(".supporter-status").textContent = supporterActive
      ? `Every hat unlocked until ${fmtDate(data.supporter_until)}`
      : (supporterUntil ? `Not a supporter (lapsed ${fmtDate(data.supporter_until)})` : "Not a supporter");
    const kofiEmails = Array.isArray(data.kofi_emails) ? data.kofi_emails : [];
    fragment.querySelector(".supporter-emails").textContent = kofiEmails.length
      ? `Linked Ko-fi emails: ${kofiEmails.join(", ")}`
      : "No Ko-fi email linked by an admin; payments match the account email.";
    const supporterForm = fragment.querySelector(".supporter-form");
    const supporterRemove = supporterForm.querySelector(".supporter-remove");
    supporterForm.elements.until.value = toLocalInput(supporterActive ? supporterUntil : new Date(Date.now() + 36 * 86_400_000));
    const supporterReason = () => {
      const reason = supporterForm.elements.reason.value.trim();
      if (reason.length < 3) throw new Error("Enter a reason of at least 3 characters for the audit log.");
      return reason;
    };
    supporterForm.addEventListener("submit", (event) => {
      event.preventDefault();
      run(async () => {
        const until = new Date(supporterForm.elements.until.value);
        if (Number.isNaN(until.getTime())) throw new Error("Enter a valid date and time.");
        const reason = supporterReason();
        if (!confirmAction(`Unlock every hat for ${name} until ${until.toLocaleString()}?`)) return;
        const result = await rpc("admin_set_supporter", { p_user_id: data.user_id, p_active_until: until.toISOString(), p_reason: reason });
        notify(`${name} is a supporter until ${fmtDate(result.active_until)}.`);
        await loadUser();
      });
    });
    supporterRemove.addEventListener("click", () => run(async () => {
      const reason = supporterReason();
      if (!confirmAction(`Remove supporter status from ${name}? Hats they have not bought lock again.`)) return;
      await rpc("admin_set_supporter", { p_user_id: data.user_id, p_active_until: null, p_reason: reason });
      notify(`Supporter status removed for ${name}.`);
      await loadUser();
    }));
    if (!data.profile || !can("supporters")) supporterForm.querySelector("button[type=submit]").disabled = true;
    if (!data.profile || !can("supporters") || !supporterActive) supporterRemove.disabled = true;
    if (!can("supporters")) supporterForm.append(el("span", { class: "hint", text: "You do not have the Supporters permission." }));

    const achievements = fragment.querySelector(".achievements");
    achievements.append(table([
      { label: "Achievement", render: (row) => ACHIEVEMENT_NAMES[row.key] || row.key },
      { label: "Status", render: (row) => (row.unlocked
        ? el("span", { class: "badge badge-good", text: `unlocked ${fmtDate(row.unlocked_at)}` })
        : el("span", { class: "badge", text: "locked" })) },
      { label: "", render: (row) => el("button", {
        type: "button",
        class: `btn btn-small ${row.unlocked ? "btn-red" : "btn-green"}`,
        text: row.unlocked ? "Revoke" : "Unlock",
        disabled: data.profile && can("achievements") ? null : "disabled",
        onclick: () => run(async () => {
          const label = ACHIEVEMENT_NAMES[row.key] || row.key;
          if (!confirmAction(`${row.unlocked ? "Revoke" : "Unlock"} “${label}” for ${name}?`)) return;
          await rpc("admin_set_achievement", { p_user_id: data.user_id, p_achievement_key: row.key, p_unlocked: !row.unlocked });
          notify(`${label} ${row.unlocked ? "revoked" : "unlocked"} for ${name}.`);
          await loadUser();
        }),
      }) },
    ], data.achievements || []));

    renderUserBans(fragment, data, bans, name);
    fragment.querySelector(".user-audit").append(table(auditColumns(false), data.audit || [], { empty: "No admin actions on this account yet." }));
    root.append(fragment);
  };

  const renderPermGrid = (container, selected, options = {}) => {
    clear(container);
    for (const [key, label, description] of PERMISSIONS) {
      const input = el("input", { type: "checkbox", name: "permission", value: key });
      input.checked = selected.includes(key);
      if (options.lock && options.lock.includes(key)) {
        input.disabled = true;
        input.checked = true;
      }
      container.append(el("label", { class: "perm-option" }, [
        input,
        el("span", {}, [
          el("span", { class: "perm-title", text: label }),
          el("br"),
          el("span", { class: "perm-desc", text: description }),
        ]),
      ]));
    }
  };
  const readPermGrid = (container) => Array.from(container.querySelectorAll("input[name=permission]"))
    .filter((input) => input.checked)
    .map((input) => input.value);

  const closeAdminEditor = () => {
    state.admins.editing = null;
    $("admin-editor").hidden = true;
  };

  const openAdminEditor = (row) => {
    state.admins.editing = row.user_id;
    const editor = $("admin-editor");
    const isSelf = state.me && row.user_id === state.me.user_id;
    $("admin-editor-name").textContent = row.email || shortId(row.user_id);
    $("admin-editor-hint").textContent = isSelf
      ? "This is your own account: you cannot drop Manage admins or remove yourself."
      : "Changes apply the next time they load a page.";
    const form = $("admin-edit-form");
    renderPermGrid(form.querySelector("[data-perm-grid]"), row.permissions || [], { lock: isSelf ? ["admins"] : [] });
    form.elements.note.value = row.note || "";
    $("admin-edit-remove").disabled = Boolean(isSelf);
    editor.hidden = false;
    editor.scrollIntoView({ behavior: "smooth", block: "nearest" });
  };

  const loadAdmins = async () => {
    const rows = await rpc("admin_list_admins");
    state.admins.rows = rows;
    const wrap = $("admins-table");
    clear(wrap);
    wrap.append(table([
      { label: "Admin", render: (row) => el("div", {}, [
        el("div", {}, [
          el("strong", { text: row.display_name || row.email || shortId(row.user_id) }),
          " ",
          row.is_owner ? el("span", { class: "badge badge-admin", text: "owner" }) : null,
          state.me && row.user_id === state.me.user_id ? el("span", { class: "badge badge-you", text: "you" }) : null,
        ]),
        el("div", { class: "muted", text: row.email || "" }),
      ]) },
      { label: "Permissions", render: (row) => el("div", { class: "chips" },
        row.is_owner
          ? [el("span", { class: "chip chip-owner", text: "Everything (owner)" })]
          : (row.permissions || []).length
            ? (row.permissions || []).map((key) => el("span", { class: "chip chip-perm", text: PERMISSION_LABELS[key] || key }))
            : [el("span", { class: "chip", text: "Overview only" })]) },
      { label: "Added", render: (row) => el("div", {}, [
        el("div", { text: fmtDate(row.granted_at) }),
        el("div", { class: "muted", text: row.granted_by_email ? `by ${row.granted_by_email}` : "seeded" }),
      ]) },
      { label: "Note", key: "note" },
      { label: "", render: (row) => el("div", { class: "actions" }, [
        el("button", {
          type: "button",
          class: "btn btn-grey btn-small",
          text: "Edit",
          disabled: row.is_owner ? "disabled" : null,
          title: row.is_owner ? "Owners are managed with psql" : null,
          onclick: () => openAdminEditor(row),
        }),
      ]) },
    ], rows, { empty: "No admins yet." }));
    if (state.admins.editing && !rows.some((row) => row.user_id === state.admins.editing)) closeAdminEditor();
  };

  const loadUser = async () => {
    if (!state.user.id) return;
    const [data, bans, history] = await Promise.all([
      rpc("admin_get_user", { p_user_id: state.user.id }),
      can("bans") ? rpc("admin_get_user_bans", { p_user_id: state.user.id }) : Promise.resolve(null),
      can("tokens") ? rpc("admin_get_user_token_history", { p_user_id: state.user.id, p_limit: 200 }) : Promise.resolve(null),
    ]);
    state.user.data = data;
    renderUser(data, bans, history);
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
    const match = /^user\/([0-9a-f-]{36})$/i.exec(hash);
    if (match) return { view: "user", id: match[1] };
    if (["overview", "users", "audit", "admins", "apps", "supporters", "bans", "boards"].includes(hash)) return { view: hash };
    return { view: "overview" };
  };

  const render = () => {
    if (!state.session) {
      stopOverviewRefresh();
      showView("login");
      return;
    }
    if (!state.me) return;
    if (!state.me.is_admin) {
      stopOverviewRefresh();
      $("forbidden-email").textContent = state.me.email || "";
      showView("forbidden");
      return;
    }
    let route = routeFromHash();
    if (!viewAllowed(route.view)) {
      route = { view: "overview" };
      if (location.hash && location.hash !== "#overview") notify("You do not have permission for that page.", "error");
    }
    state.view = route.view;
    showView(route.view);
    scheduleOverviewRefresh();
    run(async () => {
      if (route.view === "overview") await loadOverview();
      else if (route.view === "users") await loadUsers();
      else if (route.view === "audit") await loadAudit();
      else if (route.view === "admins") await loadAdmins();
      else if (route.view === "apps") {
        await loadLimitRequests();
        await loadApps();
      }
      else if (route.view === "supporters") {
        await loadKofiEvents();
        await loadSupporters();
      }
      else if (route.view === "bans") {
        await loadBans();
        await loadBlockedSignups();
      }
      else if (route.view === "boards") {
        await window.PocketPassBoardsAdmin.render({root: $("boards-console"), rpc, can, accountId: state.me.user_id, upload: async args => {
          if(state.session.expires_at * 1000 - Date.now() < 60_000) await refreshSession();
          const response = await fetch(`${API_URL}/boards/media`, {method: "POST", headers: {
            Authorization: `Bearer ${state.session.access_token}`, "Content-Type": "application/json"
          }, body: JSON.stringify(args)});
          const result = await readBody(response);
          if(!response.ok) throw new ApiError(response.status, result);
          return result;
        }});
      }
      else if (route.view === "user") {
        if (state.user.id !== route.id) {
          state.user = { id: route.id, data: null };
          clear($("user-detail"));
        }
        await loadUser();
      }
    });
  };

  const bootstrapSession = async () => {
    state.session = loadSession();
    if (!state.session) {
      render();
      return;
    }
    try {
      state.me = await rpc("admin_whoami");
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        saveSession(null);
      } else {
        notify(explain(error), "error");
      }
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
        $("login-code-form").hidden = false;
        $("login-code").value = "";
        $("login-code").focus();
      });
    });
    $("login-back").addEventListener("click", () => {
      $("login-code-form").hidden = true;
      $("login-email-form").hidden = false;
      $("login-email").focus();
    });
    $("login-code-form").addEventListener("submit", (event) => {
      event.preventDefault();
      runForm(event.currentTarget, "Verifying…", async () => {
        await verifyCode(state.lastLoginEmail, $("login-code").value.trim());
        state.me = await rpc("admin_whoami");
        $("login-code-form").hidden = true;
        $("login-email-form").hidden = false;
        if (state.me.is_admin) notify(`Signed in as ${state.me.email}.`);
      }).then(render);
    });
    $("open-studio").addEventListener("click", () => {
      const studioWindow = window.open("about:blank", "_blank");
      if (!studioWindow) {
        notify("Allow pop-ups for this site to open Studio.", "error");
        return;
      }
      run(async () => {
        try {
          const session = await rpc("admin_studio_session_create");
          studioWindow.location.replace(`${STUDIO_URL}/_pocketpass/login#${session.token}`);
        } catch (error) {
          studioWindow.close();
          throw error;
        }
      });
    });
    $("sign-out").addEventListener("click", () => run(signOut));
    $("forbidden-sign-out").addEventListener("click", () => run(signOut));
    for (const link of document.querySelectorAll(".nav-link")) {
      link.addEventListener("click", () => { location.hash = `#${link.dataset.view}`; });
    }
    for (const button of document.querySelectorAll("[data-refresh]")) {
      button.addEventListener("click", () => render());
    }
    $("user-search-form").addEventListener("submit", (event) => {
      event.preventDefault();
      state.users.query = $("user-search").value.trim();
      state.users.offset = 0;
      run(loadUsers);
    });
    $("users-prev").addEventListener("click", () => { state.users.offset = Math.max(0, state.users.offset - PAGE_SIZE); run(loadUsers); });
    $("users-next").addEventListener("click", () => { state.users.offset += PAGE_SIZE; run(loadUsers); });
    $("audit-prev").addEventListener("click", () => { state.audit.offset = Math.max(0, state.audit.offset - AUDIT_PAGE_SIZE); run(loadAudit); });
    $("audit-next").addEventListener("click", () => { state.audit.offset += AUDIT_PAGE_SIZE; run(loadAudit); });
    $("app-search-form").addEventListener("submit", (event) => {
      event.preventDefault();
      state.apps.query = $("app-search").value.trim();
      state.apps.offset = 0;
      run(loadApps);
    });
    for (const button of document.querySelectorAll("#limit-requests-filter button")) {
      button.addEventListener("click", () => {
        state.limitRequests.status = button.dataset.status;
        run(loadLimitRequests);
      });
    }
    for (const button of document.querySelectorAll("#kofi-events-filter button")) {
      button.addEventListener("click", () => {
        state.supporters.filter = button.dataset.filter;
        run(loadKofiEvents);
      });
    }
    $("apps-prev").addEventListener("click", () => { state.apps.offset = Math.max(0, state.apps.offset - PAGE_SIZE); run(loadApps); });
    $("apps-next").addEventListener("click", () => { state.apps.offset += PAGE_SIZE; run(loadApps); });
    for (const button of document.querySelectorAll("#bans-filter button")) {
      button.addEventListener("click", () => {
        state.bans.status = button.dataset.status;
        state.bans.offset = 0;
        run(loadBans);
      });
    }
    $("bans-prev").addEventListener("click", () => { state.bans.offset = Math.max(0, state.bans.offset - PAGE_SIZE); run(loadBans); });
    $("bans-next").addEventListener("click", () => { state.bans.offset += PAGE_SIZE; run(loadBans); });
    $("blocked-signups-prev").addEventListener("click", () => { state.blockedSignups.offset = Math.max(0, state.blockedSignups.offset - PAGE_SIZE); run(loadBlockedSignups); });
    $("blocked-signups-next").addEventListener("click", () => { state.blockedSignups.offset += PAGE_SIZE; run(loadBlockedSignups); });
    $("user-back").addEventListener("click", () => { location.hash = "#users"; });
    const addForm = $("admin-add-form");
    renderPermGrid(addForm.querySelector("[data-perm-grid]"), []);
    addForm.addEventListener("submit", (event) => {
      event.preventDefault();
      run(async () => {
        const email = addForm.elements.email.value.trim().toLowerCase();
        const permissions = readPermGrid(addForm.querySelector("[data-perm-grid]"));
        const note = addForm.elements.note.value.trim();
        if (!email) throw new Error("Enter the email of a PocketPass account.");
        const result = await rpc("admin_add_admin", { p_email: email, p_permissions: permissions, p_note: note });
        notify(`${result.email} is now an admin.`);
        addForm.reset();
        renderPermGrid(addForm.querySelector("[data-perm-grid]"), []);
        await loadAdmins();
      });
    });
    const editForm = $("admin-edit-form");
    editForm.addEventListener("submit", (event) => {
      event.preventDefault();
      run(async () => {
        const target = state.admins.editing;
        if (!target) return;
        const permissions = readPermGrid(editForm.querySelector("[data-perm-grid]"));
        const note = editForm.elements.note.value.trim();
        await rpc("admin_set_admin_permissions", { p_user_id: target, p_permissions: permissions, p_note: note });
        notify("Permissions saved.");
        if (state.me && target === state.me.user_id) state.me = await rpc("admin_whoami");
        closeAdminEditor();
        await loadAdmins();
        showView("admins");
      });
    });
    $("admin-edit-cancel").addEventListener("click", closeAdminEditor);
    $("admin-edit-remove").addEventListener("click", () => run(async () => {
      const target = state.admins.editing;
      const row = state.admins.rows.find((entry) => entry.user_id === target);
      if (!row) return;
      if (!confirmAction(`Remove ${row.email || shortId(row.user_id)} as an admin? They keep their PocketPass account.`)) return;
      await rpc("admin_remove_admin", { p_user_id: target });
      notify(`${row.email || "Admin"} removed.`);
      closeAdminEditor();
      await loadAdmins();
    }));
    window.addEventListener("hashchange", render);
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState !== "visible") {
        stopOverviewRefresh();
        return;
      }
      if (state.view !== "overview") return;
      scheduleOverviewRefresh();
      if (overviewIsLive()) loadOverview().catch(() => {});
    });
  };

  if (!secureApiUrl || !API_KEY) {
    notify("Admin console requires a secure HTTPS API URL and key.", "error");
    return;
  }
  wire();
  bootstrapSession();
})();
