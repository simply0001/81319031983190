(() => {
  "use strict";

  const API_URL = String(window.POCKETPASS_OAUTH_API_URL || "").replace(/\/+$/, "");
  const API_KEY = String(window.POCKETPASS_OAUTH_KEY || "");
  const secureApiUrl = (() => {
    try { const url = new URL(API_URL); return url.protocol === "https:" && url.origin === API_URL; }
    catch (_) { return false; }
  })();
  const SESSION_KEY = "pp_links_session";

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
    lastLoginEmail: "",
  };
  const handlers = {
    onSignedOut: null,
  };

  const loadSession = () => {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) {
      return null;
    }
  };
  const saveSession = (session) => {
    state.session = session;
    try {
      if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session));
      else localStorage.removeItem(SESSION_KEY);
    } catch (_) {}
  };

  const decodeClaims = (token) => {
    try {
      const segment = String(token || "").split(".")[1] || "";
      const base64 = segment.replace(/-/g, "+").replace(/_/g, "/").padEnd(segment.length + ((4 - (segment.length % 4)) % 4), "=");
      const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
      const claims = JSON.parse(new TextDecoder().decode(bytes));
      return claims && typeof claims === "object" ? claims : {};
    } catch (_) {
      return {};
    }
  };
  const sessionFromResponse = (body) => {
    const claims = decodeClaims(body.access_token);
    const user = body.user || {};
    const previous = state.session || {};
    const id = user.id || claims.sub || null;
    return {
      access_token: body.access_token,
      refresh_token: body.refresh_token,
      expires_at: Number(body.expires_at) || Number(claims.exp) || Math.floor(Date.now() / 1000) + Number(body.expires_in || 3600),
      user: { id, email: user.email || claims.email || null },
      display_name: previous.user && previous.user.id === id ? previous.display_name || null : null,
    };
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
    if (current && current.access_token) {
      try {
        await fetch(`${API_URL}/auth/v1/logout?scope=local`, {
          method: "POST",
          headers: { apikey: API_KEY, Authorization: `Bearer ${current.access_token}` },
        });
      } catch (_) {}
    }
    if (handlers.onSignedOut) handlers.onSignedOut();
  };

  const authed = async (path, options = {}, retry = true) => {
    if (!state.session) throw new ApiError(401, { message: "Signed out" });
    if (state.session.expires_at * 1000 - Date.now() < 60_000) {
      try {
        await refreshSession();
      } catch (_) {}
    }
    const method = options.method || "POST";
    const headers = {
      apikey: API_KEY,
      Authorization: `Bearer ${state.session.access_token}`,
      Accept: "application/json",
    };
    const init = { method, headers };
    if (method !== "GET") {
      headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(options.body || {});
    }
    const response = await fetch(`${API_URL}${path}`, init);
    const data = await readBody(response);
    if (response.status === 401 && retry) {
      try {
        await refreshSession();
      } catch (_) {
        await signOut();
        throw new ApiError(401, { message: "Your session expired. Please sign in again." });
      }
      return authed(path, options, false);
    }
    if (!response.ok) throw new ApiError(response.status, data);
    return data;
  };
  const rpc = (name, args = {}) => authed(`/rest/v1/rpc/${name}`, { method: "POST", body: args });

  const bootstrapSession = async () => {
    state.session = loadSession();
    if (!state.session) return null;
    try {
      await refreshSession();
    } catch (error) {
      if (error instanceof ApiError && error.status >= 400 && error.status < 500) saveSession(null);
    }
    return state.session;
  };

  const consumeFragment = () => {
    const hash = location.hash.replace(/^#/, "");
    if (!hash) return null;
    const params = new URLSearchParams(hash);
    const hasTokens = params.has("access_token") && params.has("refresh_token");
    const hasError = params.has("error") || params.has("error_description");
    if (!hasTokens && !hasError) return null;
    history.replaceState(null, "", `${location.pathname}${location.search}`);
    if (hasTokens) {
      saveSession(sessionFromResponse({
        access_token: params.get("access_token"),
        refresh_token: params.get("refresh_token"),
        expires_in: params.get("expires_in"),
        expires_at: params.get("expires_at"),
      }));
      return { session: state.session };
    }
    return { error: params.get("error_description") || params.get("error") || "Discord sign-in failed." };
  };

  const discordSignInUrl = (returnTo) => `${API_URL}/auth/v1/authorize?provider=discord&redirect_to=${encodeURIComponent(returnTo)}`;

  const ensureDisplayName = async () => {
    const session = state.session;
    if (!session) return "";
    if (session.display_name) return session.display_name;
    if (!session.user || !session.user.id) return "";
    try {
      const rows = await authed(`/rest/v1/profiles?select=display_name&user_id=eq.${encodeURIComponent(session.user.id)}`, { method: "GET" });
      const name = Array.isArray(rows) && rows[0] && rows[0].display_name;
      if (name && state.session) saveSession({ ...state.session, display_name: String(name) });
    } catch (_) {
      return "";
    }
    return (state.session && state.session.display_name) || "";
  };
  const accountLabel = () => {
    const session = state.session;
    if (!session) return "";
    return session.display_name || (session.user && session.user.email) || "your PocketPass account";
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
    if (error.code === "otp_disabled" || (error.status === 422 && /signups not allowed/i.test(error.message || ""))) {
      return "No PocketPass account uses that email. If you signed up with Discord or a username, use those options below.";
    }
    if (error.code === "invalid_credentials" || (error.status === 400 && /invalid login credentials/i.test(error.message || ""))) {
      return "Wrong username or password. If you linked an email address to the account, sign in with that email.";
    }
    if (error.code === "otp_expired") return "That code is wrong or has expired.";
    if (error.status === 429) return "Too many attempts. Wait a minute and try again.";
    if (error.code === "PT403" && error.hint === "APP_SUSPENDED") return "This app has been suspended.";
    if (error.code === "PT404" && error.hint === "APP_NOT_FOUND") return "Unknown app.";
    if (error.code === "42501") return "Please sign in again.";
    return error.message;
  };

  const fmtDate = (value) => {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" });
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

  const resetSignIn = () => {
    $("login-code-form").hidden = true;
    $("login-email-form").hidden = false;
    $("login-code").value = "";
  };
  const wireSignIn = ({ returnTo, onSignedIn }) => {
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
      resetSignIn();
      $("login-email").focus();
    });
    $("login-code-form").addEventListener("submit", (event) => {
      event.preventDefault();
      runForm(event.currentTarget, "Verifying…", async () => {
        await verifyCode(state.lastLoginEmail, $("login-code").value.trim());
        resetSignIn();
        await ensureDisplayName();
        await onSignedIn();
      });
    });
    $("login-discord").addEventListener("click", () => {
      location.assign(discordSignInUrl(returnTo));
    });
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
          await signInWithPassword($("login-username").value.trim().toLowerCase(), $("login-password").value);
          $("login-password").value = "";
          await ensureDisplayName();
          await onSignedIn();
        });
      });
    }
  };

  window.PocketPassOAuth = {
    API_URL,
    API_KEY,
    configured: Boolean(secureApiUrl && API_KEY),
    $,
    el,
    clear,
    state,
    handlers,
    ApiError,
    authFetch,
    authed,
    rpc,
    sendCode,
    verifyCode,
    signInWithPassword,
    refreshSession,
    signOut,
    bootstrapSession,
    consumeFragment,
    discordSignInUrl,
    ensureDisplayName,
    accountLabel,
    notify,
    explain,
    fmtDate,
    run,
    runForm,
    resetSignIn,
    wireSignIn,
  };
})();
