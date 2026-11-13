// Zapper landing page: nav, release label, copy button and the interactive demo. No dependencies.
(function () {
  "use strict";
  const $ = (sel, root = document) => root.querySelector(sel);

  // Nav: a border once scrolled, and the menu on small screens.
  const nav = $(".nav");
  const menu = $("#nav-menu");
  if (nav && menu) {
    const onScroll = () => nav.classList.toggle("is-scrolled", window.scrollY > 8);
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
    const menuButton = $(".nav .menu");
    const setMenu = (open) => {
      menu.classList.toggle("is-open", open);
      menuButton.setAttribute("aria-expanded", String(open));
    };
    menuButton.addEventListener("click", () => setMenu(!menu.classList.contains("is-open")));
    menu.addEventListener("click", (e) => { if (e.target.closest("a")) setMenu(false); });
    document.addEventListener("keydown", (e) => { if (e.key === "Escape") setMenu(false); });
  }

  // Release label: the latest release's tag, keeping the static label if GitHub does not answer.
  fetch("https://api.github.com/repos/ThroughTheWind/zapper/releases/latest", { headers: { Accept: "application/vnd.github+json" } })
    .then((r) => (r.ok ? r.json() : null))
    .then((release) => {
      if (release && typeof release.tag_name === "string") {
        document.querySelectorAll("[data-version]").forEach((el) => { el.textContent = release.tag_name; });
      }
    })
    .catch(() => {});

  // Copy the adapter sample.
  const copy = $("#copy");
  if (copy) copy.addEventListener("click", () => {
    if (!navigator.clipboard) return;
    navigator.clipboard.writeText($("#code-sample").textContent).then(() => {
      const label = $("span", copy);
      label.textContent = "Copied";
      setTimeout(() => { label.textContent = "Copy"; }, 1500);
    });
  });

  // Demo: the phone's keys drive a mock TV.
  const tv = $("#tv");
  if (!tv) return;
  const demo = $(".demo");
  const tiles = [...tv.querySelectorAll(".tile")];
  const rows = 2;
  const columns = tiles.length / rows;
  let row = 0, col = 0, volume = 12, channel = 1, muted = false, off = false, app = null, playing = false;
  let osdTimer, toastTimer;

  const paint = () => tiles.forEach((t) => t.classList.toggle("on", +t.dataset.r === row && +t.dataset.c === col && !app && !off));
  const flash = (el, timerName) => {
    el.classList.add("show");
    clearTimeout(timerName === "osd" ? osdTimer : toastTimer);
    const t = setTimeout(() => el.classList.remove("show"), 1200);
    if (timerName === "osd") osdTimer = t; else toastTimer = t;
  };
  const toast = (text) => { const el = $("#toast"); el.textContent = text; flash(el, "toast"); };
  const osd = () => {
    $("#osdn").textContent = muted ? "Muted" : volume;
    $("#osdbar").style.width = (muted ? 0 : volume * 2) + "%";
    const icon = $("#osd use");
    if (icon) icon.setAttribute("href", muted ? "icons.svg#volume_off" : "icons.svg#volume_up");
    flash($("#osd"), "osd");
  };
  const openTile = (tile) => {
    app = tile.textContent;
    const screen = $("#tvapp");
    screen.textContent = app;
    screen.style.background = tile.style.background;
    screen.classList.add("show");
  };
  const closeApp = () => { app = null; $("#tvapp").classList.remove("show"); };

  const act = (a) => {
    if (a === "power") {
      off = !off;
      tv.classList.toggle("is-off", off);
      if (off) closeApp();
      paint();
      return;
    }
    if (off) return;
    switch (a) {
      case "up": if (!app) row = Math.max(0, row - 1); break;
      case "down": if (!app) row = Math.min(rows - 1, row + 1); break;
      case "left": if (!app) col = Math.max(0, col - 1); break;
      case "right": if (!app) col = Math.min(columns - 1, col + 1); break;
      case "ok": if (!app) openTile(tiles.find((t) => +t.dataset.r === row && +t.dataset.c === col)); break;
      case "back": case "home": closeApp(); break;
      case "source": closeApp(); row = 1; col = 0; break;
      case "volup": volume = Math.min(50, volume + 1); muted = false; osd(); break;
      case "voldown": volume = Math.max(0, volume - 1); osd(); break;
      case "mute": muted = !muted; osd(); break;
      case "chup": channel += 1; toast("Channel " + channel); break;
      case "chdown": channel = Math.max(1, channel - 1); toast("Channel " + channel); break;
      case "play": playing = !playing; toast(playing ? "Play" : "Pause"); break;
      case "rew": toast("Rewind"); break;
      case "ff": toast("Fast-forward"); break;
      case "menu": toast("Menu"); break;
      case "settings": toast("Settings"); break;
      case "guide": toast("Guide"); break;
      case "info": toast("Info"); break;
    }
    paint();
  };

  const repeats = ["up", "down", "left", "right", "volup", "voldown", "chup", "chdown", "rew", "ff"];
  demo.querySelectorAll("[data-act]").forEach((button) => {
    let timer;
    const stop = () => { button.classList.remove("is-pressed"); clearTimeout(timer); };
    button.addEventListener("pointerdown", (e) => {
      e.preventDefault();
      button.classList.add("is-pressed");
      act(button.dataset.act);
      if (repeats.includes(button.dataset.act)) {
        timer = setTimeout(function again() { act(button.dataset.act); timer = setTimeout(again, 110); }, 400);
      }
    });
    ["pointerup", "pointerleave", "pointercancel"].forEach((ev) => button.addEventListener(ev, stop));
    // Keyboard and assistive technology activate buttons with a click that has no pointerdown.
    button.addEventListener("click", (e) => { if (e.detail === 0) act(button.dataset.act); });
  });

  // Keys only while focus is in the demo, so the page still scrolls with the arrow keys.
  const keys = { ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right", Enter: "ok", Escape: "back", Backspace: "back", "+": "volup", "=": "volup", "-": "voldown", m: "mute" };
  tv.addEventListener("keydown", (e) => {
    const a = keys[e.key];
    if (a) { e.preventDefault(); act(a); }
  });
  paint();
})();
