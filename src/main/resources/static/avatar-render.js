(() => {
  const DEFAULT_BG = '#1e80ff';
  const IMAGE_BG = '#eef5ff';

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function normalizeUrl(url) {
    return typeof url === 'string' ? url.trim() : '';
  }

  function resolveText(data = {}) {
    const explicit = String(data.avatarText || '').trim();
    if (explicit) return explicit.slice(0, 1).toUpperCase();
    const username = String(data.username || data.authorName || data.fromUsername || '').trim();
    if (username) return username.slice(0, 1).toUpperCase();
    return 'U';
  }

  function renderInner(data = {}) {
    const avatarUrl = normalizeUrl(data.avatarUrl || data.authorAvatarUrl || data.fromUserAvatarUrl || data.userAvatar);
    const text = resolveText(data);
    const alt = escapeHtml(data.alt || `${data.username || data.authorName || data.fromUsername || '用户'}头像`);
    if (avatarUrl) {
      return {
        html: `<img src="${escapeHtml(avatarUrl)}" alt="${alt}" style="width:100%;height:100%;display:block;object-fit:cover;border-radius:inherit;">`,
        style: `background:${IMAGE_BG};overflow:hidden;`
      };
    }
    return {
      html: escapeHtml(text),
      style: `background:${DEFAULT_BG};color:#fff;`
    };
  }

  function renderHtml(className, data = {}, extraAttrs = '') {
    const rendered = renderInner(data);
    return `<div class="${className}" style="${rendered.style}" ${extraAttrs}>${rendered.html}</div>`;
  }

  function apply(element, data = {}) {
    if (!element) return;
    const rendered = renderInner(data);
    if (!element.dataset.baseInlineStyle) {
      element.dataset.baseInlineStyle = element.getAttribute('style') || '';
    }
    const baseInlineStyle = element.dataset.baseInlineStyle;
    element.setAttribute('style', `${rendered.style}${baseInlineStyle ? ';' + baseInlineStyle : ''}`);
    element.innerHTML = rendered.html;
  }

  window.SiteAvatar = {
    renderHtml,
    apply,
    normalizeUrl,
    resolveText
  };
})();

