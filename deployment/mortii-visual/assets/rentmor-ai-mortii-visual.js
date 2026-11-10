/* Mortii visual enhancement only. Keeps existing chat, booking and close interactions unchanged. */
(() => {
  'use strict';
  const avatarSrc = 'data:image/webp;base64,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';
  const launcherSrc = 'data:image/webp;base64,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';
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
    css.href = '/rentmor-ai-mortii-visual.css?v=3';
    document.head.append(css);
    enhance();
    const bindChat = () => {
      const chat = document.getElementById('mortii-chat');
      if (!chat) return false;
      const observer = new MutationObserver(() => enhance());
      observer.observe(chat, {childList: true, subtree: true});
      enhance();
      return true;
    };
    if (!bindChat()) {
      const waiting = new MutationObserver(() => {
        if (bindChat()) waiting.disconnect();
      });
      waiting.observe(document.body || document.documentElement, {childList: true, subtree: true});
    }
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, {once: true});
  } else init();
})();