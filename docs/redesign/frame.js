/*
 * PadStudio 2 preview: the mock frames and the global switches.
 *
 * Frames. Every .mock-frame is the tablet's screen in landscape, 1164 × 777 dp, drawn with 1 dp = 1 CSS px.
 * frame.js scales each frame with a CSS transform so it fits the width of its parent element (aspect kept)
 * and gives back the layout space the transform frees through negative right and bottom margins, then
 * recomputes when the parent is resized. kit.css makes frames inline-block so those margins can't collapse
 * through the parent; keep it that way. The scale is written to the frame as --frame-scale (and data-fitted).
 *   data-max-scale="1"            largest scale; default 1, so a frame is never drawn above 1 dp = 1 px
 *   data-orientation="portrait"   777 × 1164 (kit.css). The design size is the frame's own CSS size.
 *   data-lock="theme fontscale"   switches this frame ignores; it keeps the data-* value written in the HTML
 *   data-fit="none"               leave the frame unscaled
 * Put each frame in its own block parent: that parent's content width is what the frame fits.
 * Write the default attributes into the HTML too (data-theme="dark" data-palette="Violet" data-fontscale="1.25"
 * data-bold="on"), so the frame is already right before this script runs.
 *
 * Switches, applied to every frame as data attributes:
 *   data-theme      dark | light
 *   data-palette    Violet | Ocean | Rose | Emerald | Graphite
 *   data-fontscale  1 | 1.25 | 2     (PadFontScale.apply from fontscale.js rewrites the type sizes)
 *   data-bold       on | off         (kit.css: [data-bold="on"] adds 300 to --w-*)
 * Defaults are the owner's tablet: dark, Violet, 1.25, Bold text on.
 * Precedence: the page URL, then what was saved, then the defaults. The URL form is
 *   page.html#theme=light&palette=Ocean&fontscale=1&bold=off      (or the same after "?")
 * fontscale also accepts percent (125) and the alias "text"; motion=reduce forces reduced motion.
 * A change made with the controls is saved in localStorage when the browser allows it.
 *
 * Controls. An element with data-mock-switches is filled with the switch controls (kit.css styles them).
 * An element with data-mock-themed gets data-theme and data-palette, for page chrome that follows the theme.
 *
 * Motion. data-motion="reduce" | "full" on <html>, on frames and on themed elements, from
 * prefers-reduced-motion (kept up to date). Colours cross-fade on a switch only when motion is full.
 *
 * API: window.PadFrame.state, .set({theme, palette, fontscale, bold}), .refresh(), .fit(frame), .SWITCHES.
 * Every change fires "padframe:change" on document with the new state as detail.
 */
(function () {
  "use strict";
  if (typeof window === "undefined" || typeof document === "undefined" || window.PadFrame) return;

  var SWITCHES = [
    { key: "theme", attr: "data-theme", label: "Theme", def: "dark",
      options: [["dark", "Dark"], ["light", "Light"]] },
    { key: "palette", attr: "data-palette", label: "Colours", def: "Violet",
      options: [["Violet", "Violet"], ["Ocean", "Ocean"], ["Rose", "Rose"], ["Emerald", "Emerald"], ["Graphite", "Graphite"]] },
    { key: "fontscale", attr: "data-fontscale", label: "Text size", def: "1.25",
      options: [["1", "100%"], ["1.25", "125%"], ["2", "200%"]] },
    { key: "bold", attr: "data-bold", label: "Bold text", def: "on",
      options: [["on", "On"], ["off", "Off"]] },
  ];
  var BY_KEY = {};
  SWITCHES.forEach(function (s) { BY_KEY[s.key] = s; });
  var ALIASES = { mode: "theme", colours: "palette", colors: "palette", scale: "fontscale", text: "fontscale", textsize: "fontscale" };
  // Seed colours from padstudio/design/tokens.json; swatches use them only until tokens.css is loaded.
  var SEEDS = { Violet: "#5A3FD6", Ocean: "#0F8B8D", Rose: "#C2185B", Emerald: "#138A55", Graphite: "#4A4A52" };
  var STORE_KEY = "padstudio2.switches";

  // ---- values --------------------------------------------------------------------------------------

  function norm(key, raw) {
    if (raw === null || raw === undefined) return null;
    var v = String(raw).trim();
    if (key === "theme") {
      v = v.toLowerCase();
      return v === "dark" || v === "light" ? v : null;
    }
    if (key === "palette") {
      var hit = null;
      BY_KEY.palette.options.forEach(function (o) { if (o[0].toLowerCase() === v.toLowerCase()) hit = o[0]; });
      return hit;
    }
    if (key === "fontscale") {
      var n = parseFloat(v.replace("%", ""));
      if (!isFinite(n)) return null;
      if (n > 3) n = n / 100; // 125 -> 1.25
      if (n < 0.5 || n > 3) return null;
      return String(Math.round(n * 10000) / 10000);
    }
    if (key === "bold") {
      v = v.toLowerCase();
      if (v === "on" || v === "1" || v === "true" || v === "yes") return "on";
      if (v === "off" || v === "0" || v === "false" || v === "no") return "off";
      return null;
    }
    return null;
  }

  function defaults() {
    var o = {};
    SWITCHES.forEach(function (s) { o[s.key] = s.def; });
    return o;
  }

  function parsePairs(text, into) {
    String(text || "").split(/[&;]/).forEach(function (pair) {
      var i = pair.indexOf("=");
      if (i < 1) return;
      var k, val;
      try {
        k = decodeURIComponent(pair.slice(0, i)).trim().toLowerCase();
        val = decodeURIComponent(pair.slice(i + 1).replace(/\+/g, " "));
      } catch (e) {
        return;
      }
      into[ALIASES[k] || k] = val;
    });
    return into;
  }

  var urlRaw = {};
  try {
    parsePairs(window.location.search.replace(/^\?/, ""), urlRaw);
    parsePairs(window.location.hash.replace(/^#/, ""), urlRaw);
  } catch (e) { /* no location */ }

  var hashHadSwitches = false;
  try {
    var hashPairs = parsePairs(window.location.hash.replace(/^#/, ""), {});
    hashHadSwitches = SWITCHES.some(function (s) { return s.key in hashPairs; });
  } catch (e) { /* ignore */ }

  function readStore() {
    var out = {};
    try {
      var raw = window.localStorage.getItem(STORE_KEY);
      if (!raw) return out;
      var saved = JSON.parse(raw);
      SWITCHES.forEach(function (s) {
        var v = saved && norm(s.key, saved[s.key]);
        if (v) out[s.key] = v;
      });
    } catch (e) { /* storage blocked or unreadable: defaults */ }
    return out;
  }

  function writeStore(st) {
    try { window.localStorage.setItem(STORE_KEY, JSON.stringify(st)); } catch (e) { /* not saved */ }
  }

  var state = defaults();
  var saved = readStore();
  SWITCHES.forEach(function (s) {
    if (saved[s.key]) state[s.key] = saved[s.key];
    var fromUrl = norm(s.key, urlRaw[s.key]);
    if (fromUrl) state[s.key] = fromUrl;
  });

  // ---- motion --------------------------------------------------------------------------------------

  var motionQuery = window.matchMedia ? window.matchMedia("(prefers-reduced-motion: reduce)") : null;
  var forcedMotion = String(urlRaw.motion || "").toLowerCase();
  function reducedMotion() {
    if (forcedMotion === "reduce") return true;
    if (forcedMotion === "full") return false;
    return !!(motionQuery && motionQuery.matches);
  }

  // ---- applying ------------------------------------------------------------------------------------

  function lockedSet(el) {
    var set = {};
    String(el.getAttribute("data-lock") || "").split(/[\s,]+/).forEach(function (k) {
      if (k) set[ALIASES[k] || k] = true;
    });
    return set;
  }

  var warnedNoFontScale = false;

  function applyFrame(frame, animate) {
    var locked = lockedSet(frame);
    SWITCHES.forEach(function (s) {
      if (!locked[s.key]) frame.setAttribute(s.attr, state[s.key]);
      else if (!norm(s.key, frame.getAttribute(s.attr))) frame.setAttribute(s.attr, s.def);
    });
    var scale = Number(locked.fontscale ? norm("fontscale", frame.getAttribute("data-fontscale")) : state.fontscale);
    if (window.PadFontScale && window.PadFontScale.apply) {
      window.PadFontScale.apply(frame, scale);
    } else if (!warnedNoFontScale) {
      warnedNoFontScale = true;
      if (window.console) console.warn("frame.js: fontscale.js is not loaded; text sizes stay at 1.0");
    }
    frame.setAttribute("data-motion", reducedMotion() ? "reduce" : "full");
    if (animate) crossFade(frame);
  }

  function applyThemed(el, animate) {
    var locked = lockedSet(el);
    if (!locked.theme) el.setAttribute("data-theme", state.theme);
    if (!locked.palette) el.setAttribute("data-palette", state.palette);
    el.setAttribute("data-motion", reducedMotion() ? "reduce" : "full");
    if (animate) crossFade(el);
  }

  var fadeTimers = typeof WeakMap === "function" ? new WeakMap() : null;
  function crossFade(el) {
    if (reducedMotion() || !fadeTimers) return;
    el.classList.add("is-switching");
    clearTimeout(fadeTimers.get(el));
    fadeTimers.set(el, setTimeout(function () { el.classList.remove("is-switching"); }, 320));
  }

  function frames() { return Array.prototype.slice.call(document.querySelectorAll(".mock-frame")); }
  function themed() { return Array.prototype.slice.call(document.querySelectorAll("[data-mock-themed], [data-mock-switches]")); }

  // ---- fitting -------------------------------------------------------------------------------------

  function fit(frame) {
    if (frame.getAttribute("data-fit") === "none") return;
    var parent = frame.parentElement;
    if (!parent) return;
    var cs = window.getComputedStyle(parent);
    var avail = parent.clientWidth - (parseFloat(cs.paddingLeft) || 0) - (parseFloat(cs.paddingRight) || 0);
    var w = frame.offsetWidth; // layout size: transforms don't change it
    var h = frame.offsetHeight;
    if (!(avail > 0 && w > 0 && h > 0)) return; // hidden for now; the resize observer calls again
    var max = parseFloat(frame.getAttribute("data-max-scale"));
    if (!(max > 0)) max = 1;
    var k = Math.floor(Math.min(max, avail / w) * 10000) / 10000;
    var stamp = k + "|" + w + "|" + h;
    if (frame.getAttribute("data-fitted") === stamp) return;
    frame.setAttribute("data-fitted", stamp);
    frame.style.setProperty("--frame-scale", String(k));
    if (k === 1) {
      frame.style.transform = "";
      frame.style.marginRight = "";
      frame.style.marginBottom = "";
    } else {
      frame.style.transform = "scale(" + k + ")";
      frame.style.marginRight = ((k - 1) * w).toFixed(3) + "px";
      frame.style.marginBottom = ((k - 1) * h).toFixed(3) + "px";
    }
  }

  var observer = typeof ResizeObserver === "function" ? new ResizeObserver(function (entries) {
    var seen = [];
    entries.forEach(function (entry) {
      var el = entry.target;
      var list = el.classList.contains("mock-frame") ? [el] : Array.prototype.filter.call(el.children, function (c) {
        return c.classList && c.classList.contains("mock-frame");
      });
      list.forEach(function (f) { if (seen.indexOf(f) < 0) seen.push(f); });
    });
    seen.forEach(fit);
  }) : null;

  var known = typeof WeakSet === "function" ? new WeakSet() : null;
  function adopt(frame) {
    if (known && known.has(frame)) return;
    if (known) known.add(frame);
    if (observer) {
      observer.observe(frame);
      if (frame.parentElement) observer.observe(frame.parentElement);
    }
  }

  // ---- controls ------------------------------------------------------------------------------------

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text) n.textContent = text;
    return n;
  }

  function buildControls(host) {
    if (host.getAttribute("data-mock-switches-built") === "1") return;
    host.setAttribute("data-mock-switches-built", "1");
    host.classList.add("mock-switches");
    if (!host.hasAttribute("role")) host.setAttribute("role", "toolbar");
    if (!host.hasAttribute("aria-label")) host.setAttribute("aria-label", "Preview settings");
    SWITCHES.forEach(function (s) {
      var group = el("div", "mock-switches__group");
      var labelId = "mock-sw-" + s.key + "-" + Math.random().toString(36).slice(2, 7);
      var label = el("span", "mock-switches__label", s.label);
      label.id = labelId;
      var seg = el("div", "seg seg--compact");
      seg.setAttribute("role", "group");
      seg.setAttribute("aria-labelledby", labelId);
      s.options.forEach(function (o) {
        var b = el("button", "seg__btn");
        b.type = "button";
        b.setAttribute("data-switch", s.key);
        b.setAttribute("data-value", o[0]);
        if (s.key === "palette") {
          var sw = el("span", "mock-switches__swatch");
          sw.setAttribute("data-palette", o[0]);
          sw.setAttribute("data-lock", "palette");
          sw.setAttribute("data-mock-themed", "");
          sw.setAttribute("aria-hidden", "true");
          sw.style.setProperty("--seed", SEEDS[o[0]]);
          b.appendChild(sw);
        }
        var name = el("span", "mock-switches__name", o[1]);
        b.appendChild(name);
        b.addEventListener("click", function () {
          var change = {};
          change[s.key] = o[0];
          set(change);
        });
        seg.appendChild(b);
      });
      group.appendChild(label);
      group.appendChild(seg);
      host.appendChild(group);
    });
    syncControls();
  }

  function syncControls() {
    Array.prototype.forEach.call(document.querySelectorAll("[data-mock-switches] [data-switch]"), function (b) {
      var on = state[b.getAttribute("data-switch")] === b.getAttribute("data-value");
      b.setAttribute("aria-pressed", on ? "true" : "false");
    });
  }

  // ---- state changes -------------------------------------------------------------------------------

  function syncHash() {
    if (!hashHadSwitches) return;
    try {
      var pairs = window.location.hash.replace(/^#/, "").split(/[&;]/).filter(function (p) {
        var k = p.split("=")[0].toLowerCase();
        return p && !BY_KEY[ALIASES[k] || k];
      });
      SWITCHES.forEach(function (s) { pairs.push(s.key + "=" + encodeURIComponent(state[s.key])); });
      window.history.replaceState(null, "", "#" + pairs.join("&"));
    } catch (e) { /* the URL keeps its old values */ }
  }

  function refresh(animate) {
    var mode = reducedMotion() ? "reduce" : "full";
    document.documentElement.setAttribute("data-motion", mode);
    // Controls first: their palette swatches are themed elements too.
    Array.prototype.forEach.call(document.querySelectorAll("[data-mock-switches]"), buildControls);
    themed().forEach(function (t) { applyThemed(t, animate); });
    frames().forEach(function (f) {
      adopt(f);
      applyFrame(f, animate);
      fit(f);
    });
    syncControls();
  }

  function set(partial, opts) {
    opts = opts || {};
    var changed = false;
    var themeChanged = false;
    SWITCHES.forEach(function (s) {
      if (!partial || !(s.key in partial)) return;
      var v = norm(s.key, partial[s.key]);
      if (v && v !== state[s.key]) {
        state[s.key] = v;
        changed = true;
        if (s.key === "theme" || s.key === "palette") themeChanged = true;
      }
    });
    if (!changed) return false;
    refresh(themeChanged);
    if (opts.save !== false) writeStore(state);
    syncHash();
    try {
      document.dispatchEvent(new CustomEvent("padframe:change", { detail: copy(state) }));
    } catch (e) { /* old browser */ }
    return true;
  }

  function copy(o) {
    var c = {};
    Object.keys(o).forEach(function (k) { c[k] = o[k]; });
    return c;
  }

  // ---- start ---------------------------------------------------------------------------------------

  function start() {
    refresh(false);
    if (motionQuery) {
      var onMotion = function () { refresh(false); };
      if (motionQuery.addEventListener) motionQuery.addEventListener("change", onMotion);
      else if (motionQuery.addListener) motionQuery.addListener(onMotion);
    }
    window.addEventListener("resize", function () { frames().forEach(fit); });
    // fontscale.js may load after this file, and webfonts can change nothing but heights; recheck once.
    window.addEventListener("load", function () { refresh(false); });
    if (typeof MutationObserver === "function") {
      new MutationObserver(function (records) {
        var found = false;
        records.forEach(function (r) {
          Array.prototype.forEach.call(r.addedNodes, function (n) {
            if (n.nodeType !== 1) return;
            if ((n.matches && n.matches(".mock-frame, [data-mock-themed], [data-mock-switches]")) ||
                (n.querySelector && n.querySelector(".mock-frame, [data-mock-themed], [data-mock-switches]"))) found = true;
          });
        });
        if (found) refresh(false);
      }).observe(document.documentElement, { childList: true, subtree: true });
    }
  }

  window.PadFrame = {
    get state() { return copy(state); },
    set: set,
    refresh: function () { refresh(false); },
    fit: fit,
    SWITCHES: SWITCHES,
    DESIGN_SIZE: { width: 1164, height: 777 },
  };

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
