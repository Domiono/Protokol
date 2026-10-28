/* RentMor online session heartbeat. No visitor identifiers sent by JavaScript. */
(function () {
  "use strict";
  function heartbeat() {
    if (document.visibilityState === "hidden") return;
    fetch("/online.php", {
      method: "POST",
      mode: "same-origin",
      credentials: "same-origin",
      cache: "no-store",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "ping=1"
    }).catch(function () {});
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", heartbeat, { once: true });
  } else {
    heartbeat();
  }
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") heartbeat();
  });
  window.setInterval(heartbeat, 30000);
})();

/* Contact events are independent of the legacy cumulative counter. */
(function () {
  "use strict";
  document.addEventListener("click", function (event) {
    var button = event.target.closest && event.target.closest("a,button,[data-phone-action]");
    if (!button) return;
    var action = button.getAttribute("data-phone-action");
    if (!action && /^tel:/i.test(button.getAttribute("href") || "")) action = "call";
    if (!action && /^pokaż numer$/i.test(button.textContent.trim())) action = "reveal";
    if (action !== "call" && action !== "reveal") return;
    var match = (button.getAttribute("onclick") || "").match(/trackCall\(['"]([^'"]+)['"]\)/);
    var id = button.getAttribute("data-product-id") || (match && match[1]);
    // Product buttons on the home catalogue retain their equipment identity.
    if (!id || id === "top_nav") {
      id = new URLSearchParams(location.search).get("id");
      // SEO product URLs do not have an id query parameter.
      if (!id && document.querySelector(".rm-detail-gallery")) {
        document.querySelectorAll("a[onclick]").forEach(function (link) {
          var productCall = (link.getAttribute("onclick") || "").match(/trackCall\(['"]([^'"]+)['"]\)/);
          if (productCall && productCall[1] !== "top_nav") id = productCall[1];
        });
      }
      id = id || "home";
    }
    var body = new URLSearchParams({ action: action, source_id: id });
    fetch("/phone_event.php", { method: "POST", credentials: "same-origin", mode: "same-origin", keepalive: true, body: body }).catch(function () {});
  }, true);
})();

/* Count only visible pages confirmed by the browser, once per issued page token. */
(function () {
  "use strict";
  var script = document.querySelector("script[data-view-token]");
  var token = script && script.getAttribute("data-view-token");
  if (!token || navigator.webdriver) return;
  var sent = false;
  function confirmView() {
    if (sent || document.visibilityState !== "visible") return;
    sent = true;
    fetch("/pageview.php", {method:"POST", mode:"same-origin", credentials:"same-origin", keepalive:true,
      body:new URLSearchParams({token:token})}).catch(function () {});
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", confirmView, {once:true});
  else confirmView();
  document.addEventListener("visibilitychange", confirmView);
})();
