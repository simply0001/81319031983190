(() => {
  "use strict";

  const O = window.PocketPassOAuth;
  if (!O) return;
  const { $, el, clear, state, ApiError, authed, rpc, notify, explain, run } = O;

  const ID_PATTERN = /^[A-Za-z0-9._~-]{8,128}$/;
  const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const CUSTOM_SCHEME = /^[a-z][a-z0-9+.-]*:$/;
  const BLOCKED_SCHEMES = ["javascript:", "data:", "blob:", "vbscript:", "file:", "about:", "http:"];
  const LOOPBACK_HOSTS = ["localhost", "127.0.0.1", "[::1]"];
  const EXPIRED_COPY = "This request has expired or was already used. Go back to the app and try again.";

  const authorizationId = new URLSearchParams(location.search).get("authorization_id") || "";
  const returnTo = `${location.origin}/oauth/consent?authorization_id=${encodeURIComponent(authorizationId)}`;

  const views = ["loading", "error", "signin", "continue", "consent"];
  const showView = (name) => {
    for (const view of views) $(`view-${view}`).hidden = view !== name;
    const sessionLine = $("session-line");
    sessionLine.hidden = !(name === "consent" && state.session);
    if (!sessionLine.hidden) $("session-name").textContent = O.accountLabel();
  };
  const showLoading = (copy) => {
    $("loading-copy").textContent = copy || "One moment…";
    showView("loading");
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
  const showContinue = () => {
    const session = state.session;
    const email = (session && session.user && session.user.email) || "";
    const name = (session && session.display_name) || email || "your PocketPass account";
    $("continue-name").textContent = name;
    $("continue-email").textContent = session && session.display_name && email ? email : "";
    showView("continue");
  };

  const parseUrl = (value) => {
    try {
      return new URL(String(value || "").trim());
    } catch (_) {
      return null;
    }
  };
  const safeRedirect = (value) => {
    const url = parseUrl(value);
    if (!url) return null;
    const protocol = url.protocol.toLowerCase();
    if (protocol === "http:" && LOOPBACK_HOSTS.includes(url.hostname.toLowerCase())) return String(value).trim();
    if (BLOCKED_SCHEMES.includes(protocol)) return null;
    if (protocol !== "https:" && !CUSTOM_SCHEME.test(protocol)) return null;
    return String(value).trim();
  };
  const httpsOnly = (value) => {
    const url = parseUrl(value);
    return url && url.protocol === "https:" ? url.href : null;
  };
  const returnHost = (value) => {
    const url = parseUrl(value);
    if (!url) return String(value || "the app");
    return url.hostname || url.href;
  };
  const scopeLabel = (key) => {
    const [resource, action] = String(key).split(":");
    if (!action) return String(key);
    return `${resource.charAt(0).toUpperCase()}${resource.slice(1)} · ${action}`;
  };
  const joinNatural = (items) => {
    if (items.length <= 1) return items.join("");
    return `${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`;
  };

  const explainAuthorization = (error) => {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 410) return EXPIRED_COPY;
      if (/oauth_authorization|authorization_(not_found|expired|already|used)/i.test(error.code || "")) return EXPIRED_COPY;
    }
    return explain(error);
  };

  const navigateTo = (redirectUrl) => {
    const target = safeRedirect(redirectUrl);
    if (!target) {
      showError("Cannot return to the app", "The app asked to send you to an address PocketPass does not allow. Nothing was shared.");
      return;
    }
    showLoading("Returning to the app…");
    location.assign(target);
  };

  const setActionsDisabled = (disabled) => {
    for (const button of document.querySelectorAll("#consent-card button")) button.disabled = disabled;
  };

  const decide = (action) => run(async () => {
    setActionsDisabled(true);
    let result;
    try {
      result = await authed(`/auth/v1/oauth/authorizations/${encodeURIComponent(authorizationId)}/consent`, {
        method: "POST",
        body: { action },
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) throw error;
      setActionsDisabled(false);
      showError("This request cannot be completed", explainAuthorization(error));
      return;
    }
    navigateTo(result.redirect_url);
  });

  const identityBlock = (client, info) => {
    const logoUrl = httpsOnly(client.logo_uri || (info && info.logo_url));
    const name = client.name || (info && info.name) || "Unknown app";
    const fallback = el("div", { class: "app-logo app-logo-fallback", text: name.charAt(0).toUpperCase() || "?" });
    let logo = fallback;
    if (logoUrl) {
      logo = el("img", { class: "app-logo", src: logoUrl, alt: "", width: "72", height: "72", referrerpolicy: "no-referrer" });
      logo.addEventListener("error", () => logo.replaceWith(fallback));
    }
    const website = httpsOnly(client.uri || (info && info.website));
    const description = String((info && info.description) || "").trim();
    return el("div", { class: "app-identity" }, [
      logo,
      el("h1", { class: "auth-title app-name", text: name }),
      description ? el("p", { class: "auth-copy app-description", text: description }) : null,
      website ? el("a", { class: "app-site", href: website, rel: "noopener noreferrer", target: "_blank", text: website }) : null,
    ]);
  };

  const renderConsent = async (details) => {
    const client = details.client || {};
    let info = null;
    let infoError = null;
    if (UUID_PATTERN.test(String(client.id || ""))) {
      try {
        info = await rpc("api_app_info", { p_client_id: client.id });
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) throw error;
        infoError = error;
      }
    } else {
      infoError = new ApiError(404, { code: "PT404", hint: "APP_NOT_FOUND", message: "Unknown app." });
    }

    const requested = String(details.scope || "").split(/\s+/).filter((scope) => scope && scope !== "openid");
    const oidcDescriptions = (info && info.oidc_scope_descriptions) || {};
    const known = requested.filter((scope) => Object.prototype.hasOwnProperty.call(oidcDescriptions, scope));
    const unknown = requested.filter((scope) => !known.includes(scope));
    const suspended = Boolean(infoError && infoError.code === "PT403" && infoError.hint === "APP_SUSPENDED")
      || Boolean(info && info.status && info.status !== "active");
    const allowable = Boolean(info) && !infoError && !suspended && unknown.length === 0;

    const card = $("consent-card");
    clear(card);
    card.append(identityBlock(client, info));
    card.append(el("p", { class: "notice", text: "This is a third-party app, not made by PocketPass." }));
    if (info && info.owner_display_name) card.append(el("p", { class: "auth-copy owner-line", text: `Made by ${info.owner_display_name}` }));

    if (infoError) {
      card.append(el("p", { class: "auth-copy is-error", text: suspended ? "This app has been suspended." : explain(infoError) }));
    } else if (suspended) {
      card.append(el("p", { class: "auth-copy is-error", text: "This app has been suspended." }));
    }

    if (info && Array.isArray(info.scopes) && info.scopes.length) {
      card.append(el("div", { class: "scope-panel" }, [
        el("div", { class: "scope-title", text: `${info.name || client.name || "This app"} will be able to:` }),
        el("ul", { class: "scope-list" }, info.scopes.map((scope) => el("li", { class: "scope-item" }, [
          el("span", { class: "scope-key", text: scopeLabel(scope.key) }),
          el("span", { class: "scope-desc", text: scope.description || "" }),
        ]))),
      ]));
    }
    if (known.length) {
      card.append(el("p", { class: "auth-copy oidc-line", text: `It will also see: ${joinNatural(known.map((scope) => String(oidcDescriptions[scope])))}.` }));
    }
    if (unknown.length) {
      card.append(el("p", { class: "auth-copy is-error", text: `This app asked for a permission PocketPass does not recognise (${unknown.join(", ")}). You cannot allow this request.` }));
    }
    card.append(el("p", { class: "auth-copy return-line", text: `You will be returned to ${returnHost(details.redirect_uri)}` }));

    card.append(el("div", { class: "consent-actions" }, [
      allowable ? el("button", { type: "button", class: "btn btn-green btn-block", text: "Allow", onclick: () => decide("approve") }) : null,
      el("button", { type: "button", class: "btn btn-grey btn-block", text: allowable ? "Deny" : "Go back to the app", onclick: () => decide("deny") }),
    ]));
    showView("consent");
  };

  const startAuthorization = () => run(async () => {
    showLoading("Checking the request…");
    let details;
    try {
      details = await authed(`/auth/v1/oauth/authorizations/${encodeURIComponent(authorizationId)}`, { method: "GET" });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) throw error;
      showError("This request cannot be completed", explainAuthorization(error));
      return;
    }
    if (details && details.redirect_url) {
      navigateTo(details.redirect_url);
      return;
    }
    await renderConsent(details || {});
  });

  const boot = async () => {
    if (!O.configured) {
      showError("Not configured", "This page did not receive its API configuration. Please try again later.");
      return;
    }
    if (!ID_PATTERN.test(authorizationId)) {
      showError("Invalid request", "This link is missing a valid authorization id. Go back to the app and start the connection again.");
      return;
    }
    O.handlers.onSignedOut = showSignIn;
    O.wireSignIn({ returnTo, onSignedIn: startAuthorization });
    $("continue-yes").addEventListener("click", startAuthorization);
    $("continue-switch").addEventListener("click", () => run(O.signOut));
    $("sign-out").addEventListener("click", () => run(O.signOut));

    showLoading();
    const fragment = O.consumeFragment();
    if (fragment && fragment.error) {
      showSignIn();
      notify(fragment.error, "error");
      return;
    }
    if (fragment && fragment.session) {
      await O.ensureDisplayName();
      await startAuthorization();
      return;
    }
    const session = await O.bootstrapSession();
    if (!session) {
      showSignIn();
      return;
    }
    await O.ensureDisplayName();
    if (!state.session) {
      showSignIn();
      return;
    }
    showContinue();
  };

  boot();
})();
