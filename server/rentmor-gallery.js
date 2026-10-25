/* RentMor product photos: progressive enhancement, swipe, arrows and optional autoplay. */
(function () {
  "use strict";
  function startGalleries() {
    var reducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    Array.prototype.forEach.call(document.querySelectorAll("[data-rm-gallery]"), function (gallery) {
      if (gallery.dataset.rmReady === "1") return;
      gallery.dataset.rmReady = "1";
      var slides = Array.prototype.slice.call(gallery.querySelectorAll("[data-rm-slide]"));
      if (!slides.length) return;
      var dots = Array.prototype.slice.call(gallery.querySelectorAll("[data-rm-dot]"));
      var thumbs = Array.prototype.slice.call(gallery.parentNode.querySelectorAll("[data-rm-thumb]"));
      var current = gallery.querySelector("[data-rm-current]");
      var prev = gallery.querySelector("[data-rm-prev]");
      var next = gallery.querySelector("[data-rm-next]");
      var zoom = gallery.querySelector("[data-rm-zoom]");
      var index = 0, timer = null, hovering = false, touching = false, visible = true, xStart = 0, moved = false;
      var link = gallery.querySelector("a.rm-gallery-stage");
      function hydrateSlide(slide) {
        var img = slide.querySelector("img[data-rm-src]");
        if (!img) return;
        // Set sizes and srcset before src, so the browser selects a small WebP.
        ["sizes", "srcset"].forEach(function (attribute) {
          var value = img.getAttribute("data-rm-" + attribute);
          if (value) img.setAttribute(attribute, value);
          img.removeAttribute("data-rm-" + attribute);
        });
        img.setAttribute("src", img.getAttribute("data-rm-src"));
        img.removeAttribute("data-rm-src");
      }
      function show(n) {
        index = (n + slides.length) % slides.length;
        slides.forEach(function (slide, i) {
          var active = i === index;
          if (active) hydrateSlide(slide);
          slide.classList.toggle("rm-active", active);
          slide.setAttribute("aria-hidden", active ? "false" : "true");
        });
        dots.forEach(function (dot, i) {
          dot.classList.toggle("rm-active", i === index);
          dot.setAttribute("aria-current", i === index ? "true" : "false");
        });
        thumbs.forEach(function (thumb, i) {
          thumb.classList.toggle("rm-active", i === index);
          thumb.setAttribute("aria-pressed", i === index ? "true" : "false");
        });
        if (current) current.textContent = String(index + 1);
      }
      function stop() { if (timer !== null) { window.clearInterval(timer); timer = null; } }
      function play() {
        stop();
        if (slides.length < 2 || reducedMotion || gallery.dataset.rmAutoplay !== "true" ||
            hovering || touching || !visible || document.hidden) return;
        timer = window.setInterval(function () { show(index + 1); }, gallery.classList.contains("rm-detail-gallery") ? 5800 : 4300);
      }
      function go(n) { show(n); play(); }
      if (prev) prev.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); go(index - 1); });
      if (next) next.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); go(index + 1); });
      dots.forEach(function (dot, i) {
        dot.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); go(i); });
      });
      thumbs.forEach(function (thumb, i) {
        thumb.addEventListener("click", function (e) { e.preventDefault(); go(i); });
      });
      gallery.addEventListener("mouseenter", function () { hovering = true; stop(); });
      gallery.addEventListener("mouseleave", function () { hovering = false; play(); });
      gallery.addEventListener("touchstart", function (e) {
        xStart = e.changedTouches[0].clientX; touching = true; moved = false; stop();
      }, {passive:true});
      gallery.addEventListener("touchend", function (e) {
        var diff = e.changedTouches[0].clientX - xStart;
        touching = false;
        if (Math.abs(diff) >= 38 && slides.length > 1) {
          moved = true;
          show(index + (diff < 0 ? 1 : -1));
          window.setTimeout(function () { moved = false; }, 400);
        }
        play();
      }, {passive:true});
      gallery.addEventListener("touchcancel", function () { touching = false; play(); }, {passive:true});
      if (link) link.addEventListener("click", function (e) { if (moved) { e.preventDefault(); moved = false; } });
      if (zoom) zoom.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); stop(); openLightbox(slides, index, function (n) { show(n); }); });
      if ("IntersectionObserver" in window) {
        var observer = new IntersectionObserver(function (entries) {
          visible = !!entries[0].isIntersecting;
          play();
        }, {threshold:.1});
        observer.observe(gallery);
      }
      document.addEventListener("visibilitychange", play);
      gallery.addEventListener("keydown", function (e) {
        if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
          e.preventDefault(); go(index + (e.key === "ArrowRight" ? 1 : -1));
        }
      });
      show(0); play();
    });
  }
  function openLightbox(slides, index, onChange) {
    var modal = document.createElement("div");
    modal.className = "rm-gallery-lightbox";
    modal.setAttribute("role", "dialog");
    modal.setAttribute("aria-modal", "true");
    modal.setAttribute("aria-label", "Powiększona galeria sprzętu");
    var image = document.createElement("img");
    var caption = document.createElement("div"); caption.className = "rm-gallery-lightbox-caption";
    var close = document.createElement("button"); close.className = "rm-gallery-lightbox-close"; close.type = "button"; close.textContent = "×"; close.setAttribute("aria-label", "Zamknij");
    var prev = document.createElement("button"); prev.className = "rm-gallery-lightbox-prev"; prev.type = "button"; prev.textContent = "‹"; prev.setAttribute("aria-label", "Poprzednie zdjęcie");
    var next = document.createElement("button"); next.className = "rm-gallery-lightbox-next"; next.type = "button"; next.textContent = "›"; next.setAttribute("aria-label", "Następne zdjęcie");
    modal.appendChild(image); modal.appendChild(close);
    if (slides.length > 1) { modal.appendChild(prev); modal.appendChild(next); }
    modal.appendChild(caption);
    var lastFocus = document.activeElement;
    var lastOverflow = document.body.style.overflow;
    function render(n) {
      index = (n + slides.length) % slides.length;
      var slideImage = slides[index].querySelector("img");
      image.src = slideImage.src; // Open the original full-resolution photo; srcset is only for previews.
      image.alt = slideImage.alt;
      caption.textContent = (index + 1) + " / " + slides.length;
      onChange(index);
    }
    function dismiss() {
      document.removeEventListener("keydown", keydown);
      document.body.style.overflow = lastOverflow;
      modal.remove();
      if (lastFocus && lastFocus.focus) lastFocus.focus();
    }
    function keydown(e) {
      if (e.key === "Escape") dismiss();
      else if (e.key === "ArrowLeft") render(index - 1);
      else if (e.key === "ArrowRight") render(index + 1);
    }
    modal.addEventListener("click", function (e) { if (e.target === modal) dismiss(); });
    close.addEventListener("click", dismiss);
    prev.addEventListener("click", function () { render(index - 1); });
    next.addEventListener("click", function () { render(index + 1); });
    document.addEventListener("keydown", keydown);
    document.body.appendChild(modal); document.body.style.overflow = "hidden";
    render(index); close.focus();
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", startGalleries);
  else startGalleries();
}());
