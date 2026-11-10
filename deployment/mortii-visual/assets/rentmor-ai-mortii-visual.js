/* Mortii visual enhancement only. Keeps existing chat, booking and close interactions unchanged. */
(() => {
  'use strict';
  const imageRoot = '/assets/';
  const avatarSrc = imageRoot + 'rentmor-ai-mortii-avatar.webp';
  const launcherSrc = imageRoot + 'rentmor-ai-mortii-launcher.webp';
  const img = (src, className, width, height) => {
    const node = document.createElement('img');
    node.src = src;
    node.alt = '';
    node.setAttribute('aria-hidden', 'true');
    node.className = className;
    node.width = width;
    node.height = height;
    node.decoding = 'async';
    return node;
  };
  function enhance() {
    const launcher = document.getElementById('mortii-launcher');
    if (launcher && !launcher.classList.contains('rm-mortii-branded')) {
      const label = document.createElement('span');
      label.textContent = 'Zapytaj Mortii';
      label.className = 'rm-mortii-launcher-label';
      launcher.replaceChildren(img(launcherSrc, 'rm-mortii-launcher-img', 68, 68), label);
      launcher.classList.add('rm-mortii-branded');
      launcher.setAttribute('aria-label', 'Otwórz czat z Mortii');
    }
    const heading = document.querySelector('#mortii-chat .mortii-top > strong');
    if (heading && !heading.classList.contains('rm-mortii-heading')) {
      heading.prepend(img(avatarSrc, 'rm-mortii-heading-img', 40, 40));
      heading.classList.add('rm-mortii-heading');
    }
    const messages = document.querySelector('#mortii-chat .mortii-messages');
    if (!messages) return;
    for (const bubble of Array.from(messages.children)) {
      // Existing implementation renders assistant replies as .mortii-answer;
      // the initial greeting is the sole unclassified paragraph.
      const assistant = bubble.classList?.contains('mortii-answer') ||
        (bubble.tagName === 'P' && !bubble.className);
      if (!assistant || bubble.closest('.rm-mortii-answer-row')) continue;
      const row = document.createElement('div');
      row.className = 'rm-mortii-answer-row';
      bubble.before(row);
      row.append(img(avatarSrc, 'rm-mortii-reply-img', 35, 35), bubble);
    }
  }
  function init() {
    const css = document.createElement('link');
    css.rel = 'stylesheet';
    css.href = '/assets/rentmor-ai-mortii-visual.css?v=1';
    document.head.append(css);
    enhance();
    const chat = document.getElementById('mortii-chat');
    if (!chat) return;
    const observer = new MutationObserver(() => enhance());
    observer.observe(chat, {childList: true, subtree: true});
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, {once: true});
  } else init();
})();