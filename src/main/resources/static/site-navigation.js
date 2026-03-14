(() => {
  const prefetched = new Set();
  const prerendered = new Set();
  const LOGIN_REQUEST_KEY = 'site:pendingLoginRequest:v1';
  const LOGIN_RETURN_URL_KEY = 'site:pendingLoginReturnUrl:v1';
  const speculationRules = {
    prerender: []
  };
  let speculationScript = null;

  function supportsSpeculationRules() {
    return typeof HTMLScriptElement !== 'undefined'
      && typeof HTMLScriptElement.supports === 'function'
      && HTMLScriptElement.supports('speculationrules');
  }

  function toAbsoluteUrl(url) {
    try {
      return new URL(url, window.location.href).toString();
    } catch (error) {
      return String(url || '');
    }
  }

  function toRelativeUrl(url) {
    const absolute = toAbsoluteUrl(url);
    if (!absolute) return '/';
    const parsed = new URL(absolute);
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  }

  function isSameOrigin(url) {
    if (!url) return false;
    try {
      return new URL(url, window.location.href).origin === window.location.origin;
    } catch (error) {
      return false;
    }
  }

  function updateSpeculationRules() {
    if (!supportsSpeculationRules()) return;
    if (!speculationScript) {
      speculationScript = document.createElement('script');
      speculationScript.type = 'speculationrules';
      document.head.appendChild(speculationScript);
    }
    const payload = {};
    if (speculationRules.prerender.length) {
      payload.prerender = [{ source: 'list', urls: speculationRules.prerender }];
    }
    speculationScript.textContent = JSON.stringify(payload);
  }

  function prerenderPage(url) {
    const absolute = toAbsoluteUrl(url);
    if (!absolute || prerendered.has(absolute) || !isSameOrigin(absolute)) return;
    prerendered.add(absolute);
    speculationRules.prerender.push(absolute);
    updateSpeculationRules();
  }

  function prefetchPage(url) {
    const absolute = toAbsoluteUrl(url);
    if (!absolute || prefetched.has(absolute)) return;
    prefetched.add(absolute);
    const link = document.createElement('link');
    link.rel = 'prefetch';
    link.as = 'document';
    link.href = absolute;
    document.head.appendChild(link);
  }

  function warmRoutes(urls, options = {}) {
    (urls || []).forEach(url => {
      if (!url) return;
      prefetchPage(url);
      if (options.prerender !== false) {
        prerenderPage(url);
      }
    });
  }

  function installPrefetchers(entries) {
    (entries || []).forEach(entry => {
      if (!entry || !entry.selector || typeof entry.getUrl !== 'function') return;
      document.querySelectorAll(entry.selector).forEach(element => {
        const trigger = () => {
          const nextUrl = entry.getUrl(element);
          if (!nextUrl) return;
          prefetchPage(nextUrl);
          if (entry.prerender !== false) {
            prerenderPage(nextUrl);
          }
        };
        ['mouseenter', 'focusin', 'touchstart'].forEach(eventName => {
          element.addEventListener(eventName, trigger, { once: true, passive: true });
        });
      });
    });
  }

  function navigate(url, options = {}) {
    const nextUrl = toRelativeUrl(url);
    if (!nextUrl || nextUrl === `${window.location.pathname}${window.location.search}${window.location.hash}`) return;
    if (typeof options.beforeNavigate === 'function') {
      options.beforeNavigate();
    }
    if (options.replace) {
      window.location.replace(nextUrl);
      return;
    }
    window.location.href = nextUrl;
  }

  function buildDiscoveryUrl(keyword = '') {
    const normalizedKeyword = String(keyword || '').trim();
    return normalizedKeyword
      ? `/?q=${encodeURIComponent(normalizedKeyword)}`
      : '/';
  }

  function navigateDiscovery(keyword = '', options = {}) {
    const nextUrl = buildDiscoveryUrl(keyword);
    const currentUrl = toRelativeUrl(window.location.href);
    if (nextUrl === currentUrl) return;

    if (window.parent && window.parent !== window) {
      try {
        const parentOrigin = window.parent.location?.origin;
        if (parentOrigin === window.location.origin && typeof window.parent.SiteShell?.navigate === 'function') {
          window.parent.SiteShell.navigate(nextUrl, options);
          return;
        }
      } catch (error) {
        // ignore cross-frame access errors and continue with current window
      }
    }

    if (typeof window.SiteShell?.navigate === 'function') {
      window.SiteShell.navigate(nextUrl, options);
      return;
    }

    navigate(nextUrl, options);
  }

  function persistLoginRequest(returnTo = '') {
    try {
      sessionStorage.setItem(LOGIN_REQUEST_KEY, '1');
      const normalizedReturnTo = toRelativeUrl(returnTo || window.location.href);
      if (normalizedReturnTo) {
        sessionStorage.setItem(LOGIN_RETURN_URL_KEY, normalizedReturnTo);
      } else {
        sessionStorage.removeItem(LOGIN_RETURN_URL_KEY);
      }
    } catch (error) {
      // ignore storage errors and fall back to direct navigation only
    }
  }

  function clearPendingLoginRequest() {
    try {
      sessionStorage.removeItem(LOGIN_REQUEST_KEY);
      sessionStorage.removeItem(LOGIN_RETURN_URL_KEY);
    } catch (error) {
      // ignore storage errors
    }
  }

  function consumePendingLoginRequest() {
    try {
      const open = sessionStorage.getItem(LOGIN_REQUEST_KEY) === '1';
      const returnTo = sessionStorage.getItem(LOGIN_RETURN_URL_KEY) || '';
      clearPendingLoginRequest();
      return {
        open,
        returnTo: returnTo ? toRelativeUrl(returnTo) : ''
      };
    } catch (error) {
      return { open: false, returnTo: '' };
    }
  }

  function requestLogin(options = {}) {
    const returnTo = toRelativeUrl(options.returnTo || window.location.href);
    if (window.parent && window.parent !== window) {
      try {
        const parentOrigin = window.parent.location?.origin;
        if (parentOrigin === window.location.origin && typeof window.parent.SiteNavigation?.requestLogin === 'function') {
          window.parent.SiteNavigation.requestLogin({ returnTo });
          return;
        }
      } catch (error) {
        // ignore cross-frame access errors and continue with current window
      }
    }

    persistLoginRequest(returnTo);

    if (typeof window.navigateShell === 'function') {
      window.navigateShell('/', { replace: false });
    }
    if (typeof window.openLoginModal === 'function') {
      window.openLoginModal();
      return;
    }

    navigate('/');
  }

  function onIdle(callback, timeout = 200) {
    if (typeof callback !== 'function') return;
    if (typeof window.requestIdleCallback === 'function') {
      window.requestIdleCallback(() => callback(), { timeout });
      return;
    }
    window.setTimeout(callback, timeout);
  }

  window.SiteNavigation = {
    navigate,
    navigateDiscovery,
    buildDiscoveryUrl,
    requestLogin,
    consumePendingLoginRequest,
    clearPendingLoginRequest,
    prefetchPage,
    warmRoutes,
    installPrefetchers,
    onIdle,
    toRelativeUrl
  };
})();

