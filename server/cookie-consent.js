/* RentMor consent UI: no third-party libraries and no analytics before opting in. */
(function () {
  "use strict";
  var COOKIE = "rm_stats_consent";
  var MAX_AGE = 60 * 60 * 24 * 180;
  function choice() {
    var chunks = document.cookie.split("; ");
    for (var i = 0; i < chunks.length; i++) {
      var p = chunks[i].split("=");
      if (p[0] === COOKIE) return p[1] === "allow" ? "allow" : (p[1] === "deny" ? "deny" : "");
    }
    return "";
  }
  function save(value) {
    document.cookie = COOKIE + "=" + value +
      "; Max-Age=" + MAX_AGE + "; Path=/; SameSite=Lax" +
      (location.protocol === "https:" ? "; Secure" : "");
    if (choice() !== value) return false;
    return true;
  }
  function build() {
    if (document.getElementById("rm-cookie-panel")) return;
    var style = document.createElement("style");
    style.textContent =
      "#rm-cookie-panel{position:fixed;inset:auto 14px 14px;max-width:550px;margin:auto;padding:19px 22px;background:#fff;color:#172b46;border:1px solid #cbd5e1;border-radius:16px;box-shadow:0 7px 32px rgba(0,0,0,.22);z-index:2147483644;font:15px/1.55 system-ui,Arial,sans-serif}" +
      "#rm-cookie-panel[hidden]{display:none!important}#rm-cookie-panel h2{font:800 19px/1.3 system-ui,Arial,sans-serif;margin:0 0 9px;color:#112640}" +
      "#rm-cookie-panel p{margin:0 0 13px;color:#334155}" +
      "#rm-cookie-actions{display:flex;gap:10px;flex-wrap:wrap}" +
      "#rm-cookie-actions button{flex:1 1 180px;min-height:44px;padding:10px 14px;border:2px solid #172b46;border-radius:11px;background:#fff;color:#172b46;font:700 14px system-ui,Arial,sans-serif;cursor:pointer}" +
      "#rm-cookie-actions button:last-child{background:#172b46;color:#fff}" +
      "#rm-cookie-panel a{color:#174c86;text-decoration:underline;text-underline-offset:2px}" +
      "#rm-cookie-panel button:focus-visible{outline:3px solid #dcae17;outline-offset:3px}" +
      "@media(max-width:600px){#rm-cookie-panel{inset:auto 9px 9px;padding:16px}}";
    document.head.appendChild(style);
    var panel = document.createElement("section");
    panel.id = "rm-cookie-panel";
    panel.hidden = true;
    panel.setAttribute("role", "region");
    panel.setAttribute("aria-label", "Zgody na statystyki serwisu");
    panel.innerHTML =
      '<h2>Twoja prywatność w RentMor.pl</h2>' +
      '<p>Używamy cookies niezbędnych do działania strony. Za Twoją zgodą zbieramy też statystyki oglądanych ofert i kliknięć kontaktu. Odmowa nie utrudnia wynajmu sprzętu. ' +
      '<a href="/polityka-prywatnosci.php">Polityka prywatności</a>.</p>' +
      '<div id="rm-cookie-actions"><button type="button" data-rm-consent="deny">Tylko niezbędne</button>' +
      '<button type="button" data-rm-consent="allow">Zgadzam się na statystyki</button></div>';
    panel.addEventListener("click", function (event) {
      var btn = event.target.closest && event.target.closest("[data-rm-consent]");
      if (!btn) return;
      var next = btn.getAttribute("data-rm-consent");
      if (next !== "allow" && next !== "deny") return;
      if (!save(next)) { panel.querySelector("p").textContent = "Nie można zapisać wyboru w przeglądarce. Statystyki pozostają wyłączone."; return; }
      panel.hidden = true;
      // Obtain a fresh server-generated statistics token, or stop loaded trackers after withdrawal.
      // Always return using GET: never resubmit a contact form POST.
      location.replace(location.pathname + location.search + location.hash);
    });
    document.body.appendChild(panel);
    if (!choice()) panel.hidden = false;
    document.addEventListener("click", function (event) {
      if (!event.target.closest) return;
      var link = event.target.closest("[data-rm-cookie-settings]");
      if (!link) return;
      event.preventDefault();
      panel.hidden = false;
      panel.querySelector("button").focus();
    });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", build, { once: true });
  else build();
})();
