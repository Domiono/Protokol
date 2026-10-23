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
