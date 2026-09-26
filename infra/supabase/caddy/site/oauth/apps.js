(() => {
  "use strict";

  const O = window.PocketPassOAuth;
  if (!O) return;
  const { $, el, clear, state, rpc, notify, run, fmtDate } = O;

  const returnTo = `${location.origin}/oauth/apps`;

  const views = ["loading", "error", "signin", "apps"];
  const showView = (name) => {
    for (const view of views) $(`view-${view}`).hidden = view !== name;
    const sessionLine = $("session-line");
    sessionLine.hidden = !(name === "apps" && state.session);
    if (!sessionLine.hidden) $("session-name").textContent = O.accountLabel();
  };
  const showError = (title, copy) => {
    $("error-title").textContent = title;
    $("error-copy").textContent = copy;
    showView("error");
  };
  const showSignIn = () => {
    O.resetSignIn();
    showView("signin");
  };

  const httpsOnly = (value) => {
    try {
      const url = new URL(String(value || "").trim());
      return url.protocol === "https:" ? url.href : null;
    } catch (_) {
      return null;
    }
  };
  const scopeLabel = (key) => {
    const [resource, action] = String(key).split(":");
    if (!action) return String(key);
    return `${resource.charAt(0).toUpperCase()}${resource.slice(1)} · ${action}`;
  };

  const revoke = (app) => run(async () => {
    const name = app.name || "This app";
    if (!window.confirm(`Disconnect ${name}? It loses access to your account immediately and must ask again to reconnect.`)) return;
    await rpc("api_revoke_app", { p_client_id: app.client_id });
    notify(`${name} disconnected.`);
    await loadApps();
  });

  const appRow = (app) => {
    const website = httpsOnly(app.website);
    const scopes = Array.isArray(app.scopes) ? app.scopes : [];
    return el("li", { class: "app-row" }, [
      el("div", { class: "app-row-main" }, [
        el("div", { class: "app-row-name", text: app.name || "Unnamed app" }),
        website ? el("a", { class: "app-site", href: website, rel: "noopener noreferrer", target: "_blank", text: website }) : null,
        el("div", { class: "chips" }, scopes.length
          ? scopes.map((scope) => el("span", { class: "chip chip-perm", text: scopeLabel(scope) }))
          : [el("span", { class: "chip", text: "Sign-in only" })]),
        el("div", { class: "muted", text: `Connected ${fmtDate(app.granted_at)}` }),
      ]),
      el("button", { type: "button", class: "btn btn-red btn-small", text: "Revoke", onclick: () => revoke(app) }),
    ]);
  };

  const loadApps = async () => {
    const result = await rpc("api_connected_apps");
    const items = Array.isArray(result && result.items) ? result.items : [];
    const list = $("apps-list");
    clear(list);
    if (!items.length) {
      list.append(el("li", { class: "empty", text: "No apps are connected to your PocketPass account." }));
    } else {
      for (const app of items) list.append(appRow(app));
    }
    showView("apps");
  };

  const boot = async () => {
    if (!O.configured) {
      showError("Not configured", "This page did not receive its API configuration. Please try again later.");
      return;
    }
    O.handlers.onSignedOut = showSignIn;
    O.wireSignIn({ returnTo, onSignedIn: loadApps });
    $("sign-out").addEventListener("click", () => run(O.signOut));
    $("apps-refresh").addEventListener("click", () => run(loadApps));

    showView("loading");
    const fragment = O.consumeFragment();
    if (fragment && fragment.error) {
      showSignIn();
      notify(fragment.error, "error");
      return;
    }
    if (!fragment) await O.bootstrapSession();
    if (!state.session) {
      showSignIn();
      return;
    }
    await O.ensureDisplayName();
    if (!state.session) {
      showSignIn();
      return;
    }
    try {
      await loadApps();
    } catch (error) {
      if (state.session) showError("Could not load your apps", O.explain(error));
      else showSignIn();
    }
  };

  boot();
})();
