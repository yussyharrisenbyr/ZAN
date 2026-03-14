(() => {
  const currentScript = document.currentScript;
  const shellView = currentScript?.dataset?.shellView?.trim();

  if (!shellView) return;

  function applyEmbeddedShellClass() {
    document.documentElement.classList.add('embedded-shell');
    if (document.body) {
      document.body.classList.add('embedded-shell');
      return;
    }
    document.addEventListener('DOMContentLoaded', () => {
      if (document.body) {
        document.body.classList.add('embedded-shell');
      }
    }, { once: true });
  }

  function buildShellUrl(view, searchParams, hash) {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.delete('embed');
    nextParams.set('shell', view);
    const query = nextParams.toString();
    return `/${query ? `?${query}` : ''}${hash || ''}`;
  }

  try {
    const searchParams = new URLSearchParams(window.location.search);
    const isEmbedded = searchParams.get('embed') === '1' && window.parent && window.parent !== window;

    window.SiteShellEmbed = {
      isEmbedded,
      view: shellView
    };

    if (!isEmbedded) {
      window.location.replace(buildShellUrl(shellView, searchParams, window.location.hash));
      return;
    }

    applyEmbeddedShellClass();
  } catch (error) {
    // ignore
  }
})();
