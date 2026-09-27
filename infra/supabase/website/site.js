(function () {
  "use strict";

  var reducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  var menuButton = document.querySelector("[data-menu-button]");
  var mobileNav = document.querySelector("[data-mobile-nav]");
  if (menuButton && mobileNav) {
    var setMenuOpen = function (open) {
      mobileNav.classList.toggle("is-open", open);
      menuButton.setAttribute("aria-expanded", open ? "true" : "false");
    };
    menuButton.addEventListener("click", function () {
      setMenuOpen(!mobileNav.classList.contains("is-open"));
    });
    mobileNav.addEventListener("click", function (event) {
      if (event.target.closest("a")) {
        setMenuOpen(false);
      }
    });
    document.addEventListener("click", function (event) {
      if (!mobileNav.contains(event.target) && !menuButton.contains(event.target)) {
        setMenuOpen(false);
      }
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape") {
        setMenuOpen(false);
      }
    });
  }

  var tour = document.getElementById("tour");
  if (tour) {
    var stage = tour.querySelector(".stage");
    var devices = Array.prototype.slice.call(stage.querySelectorAll("[data-device-view]"));
    var deviceButtons = Array.prototype.slice.call(tour.querySelectorAll("[data-device-button]"));
    var thorOnly = Array.prototype.slice.call(tour.querySelectorAll("[data-thor-only]"));
    var tabs = Array.prototype.slice.call(tour.querySelectorAll("[data-tab-button]"));
    var slides = Array.prototype.slice.call(tour.querySelectorAll("[data-slide]"));
    var storySets = Array.prototype.slice.call(tour.querySelectorAll("[data-story-set]"));
    var order = tabs.map(function (tab) { return tab.getAttribute("data-tab-button"); });
    var current = order[0];
    var leaveTimer = 0;

    var select = function (name) {
      if (name === current) {
        return;
      }
      stage.setAttribute("data-direction", order.indexOf(name) < order.indexOf(current) ? "back" : "forward");
      current = name;
      tabs.forEach(function (tab) {
        var on = tab.getAttribute("data-tab-button") === name;
        tab.classList.toggle("is-active", on);
        tab.setAttribute("aria-selected", on ? "true" : "false");
        tab.tabIndex = on ? 0 : -1;
      });
      slides.forEach(function (slide) {
        var on = slide.getAttribute("data-slide") === name;
        if (!on && slide.classList.contains("is-active")) {
          slide.classList.add("is-leaving");
        }
        slide.classList.toggle("is-active", on);
      });
      window.clearTimeout(leaveTimer);
      leaveTimer = window.setTimeout(function () {
        slides.forEach(function (slide) { slide.classList.remove("is-leaving"); });
      }, 500);
      storySets.forEach(function (set) {
        set.hidden = set.getAttribute("data-story-set") !== name;
      });
      if (!reducedMotion) {
        devices.forEach(function (device) {
          if (device.hidden) { return; }
          device.classList.remove("is-switching");
          void device.offsetWidth;
          device.classList.add("is-switching");
        });
      }
    };

    devices.forEach(function (device) {
      device.addEventListener("animationend", function (event) {
        if (event.target === device) { device.classList.remove("is-switching"); }
      });
    });

    deviceButtons.forEach(function (button) {
      button.addEventListener("click", function () {
        var layout = button.getAttribute("data-device-button");
        tour.setAttribute("data-device", layout);
        if (layout === "phone" && current === "piip") { select("home"); }
        devices.forEach(function (device) {
          device.hidden = device.getAttribute("data-device-view") !== layout;
        });
        deviceButtons.forEach(function (choice) {
          choice.setAttribute("aria-pressed", choice === button ? "true" : "false");
        });
        thorOnly.forEach(function (item) { item.hidden = layout !== "thor"; });
      });
    });

    tabs.forEach(function (tab) {
      tab.addEventListener("click", function () {
        select(tab.getAttribute("data-tab-button"));
      });
      tab.addEventListener("keydown", function (event) {
        var step = event.key === "ArrowRight" || event.key === "ArrowDown" ? 1
          : event.key === "ArrowLeft" || event.key === "ArrowUp" ? -1 : 0;
        if (!step) {
          return;
        }
        event.preventDefault();
        var visibleTabs = tabs.filter(function (item) { return !item.hidden; });
        var index = visibleTabs.indexOf(tab);
        var next = visibleTabs[(index + step + visibleTabs.length) % visibleTabs.length];
        select(next.getAttribute("data-tab-button"));
        next.focus();
      });
    });
  }

  var RELEASES = "https://github.com/Hinoaaaaaf212/pocketpass-release/releases/latest";
  var APK_PREFIX = "https://github.com/Hinoaaaaaf212/pocketpass-release/";

  var card = document.getElementById("download");
  if (!card || typeof window.fetch !== "function") {
    return;
  }

  var version = document.getElementById("download-version");
  var size = document.getElementById("download-size");
  var date = document.getElementById("download-date");
  var sha = document.getElementById("download-sha");
  var meta = document.getElementById("download-meta");
  var buttons = document.querySelectorAll("[data-download-apk]");

  function formatBytes(bytes) {
    if (typeof bytes !== "number" || !isFinite(bytes) || bytes <= 0) {
      return null;
    }
    if (bytes >= 1e6) {
      return (bytes / 1e6).toFixed(1) + " MB";
    }
    return Math.round(bytes / 1e3) + " KB";
  }

  function formatDate(value) {
    if (typeof value !== "string") {
      return null;
    }
    var parsed = new Date(value);
    if (isNaN(parsed.getTime())) {
      return null;
    }
    return parsed.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
  }

  function setText(node, text) {
    if (node && text) {
      node.textContent = text;
    }
  }

  function setButtonLabel(button, text) {
    var icon = button.querySelector("svg");
    button.textContent = "";
    if (icon) {
      button.appendChild(icon);
    }
    button.appendChild(document.createTextNode(text));
  }

  fetch("/updates/latest.json", { headers: { Accept: "application/json" } })
    .then(function (response) {
      if (!response.ok) {
        throw new Error("feed " + response.status);
      }
      return response.json();
    })
    .then(function (manifest) {
      if (
        !manifest ||
        typeof manifest !== "object" ||
        manifest.schemaVersion !== 1 ||
        !Number.isInteger(manifest.versionCode) ||
        manifest.versionCode <= 0
      ) {
        card.dataset.state = "none";
        setText(meta, "No public release yet. Watch the releases page on GitHub.");
        return;
      }

      var label = typeof manifest.versionName === "string" && manifest.versionName
        ? manifest.versionName
        : String(manifest.versionCode);
      setText(version, label);
      setText(size, formatBytes(manifest.apkSizeBytes));
      setText(date, formatDate(manifest.publishedAt));
      if (typeof manifest.apkSha256 === "string" && /^[0-9a-f]{64}$/i.test(manifest.apkSha256)) {
        sha.textContent = manifest.apkSha256.slice(0, 16) + "…";
        sha.title = manifest.apkSha256;
      }

      var direct = typeof manifest.apkUrl === "string" && manifest.apkUrl.indexOf(APK_PREFIX) === 0;
      for (var i = 0; i < buttons.length; i += 1) {
        buttons[i].href = direct ? manifest.apkUrl : RELEASES;
        if (direct) {
          setButtonLabel(buttons[i], "Download PocketPass " + label);
        }
      }
      setText(meta, direct ? "" : "Get the APK from the releases page.");
      if (meta) meta.hidden = direct;
      card.dataset.state = direct ? "live" : "releases";
    })
    .catch(function () {
      card.dataset.state = "fallback";
    });
})();
