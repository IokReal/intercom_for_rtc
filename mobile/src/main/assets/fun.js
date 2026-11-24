(function () {
    'use strict';
    if (window.__rtKeyHooked) return;
    window.__rtKeyHooked = true;

    let tokenSent = false;

    function handleToken(url, headerName, headerValue) {
        if (tokenSent || !url || !headerName || !headerValue) return;

        const urlStr = String(url);
        if (urlStr.includes('key.rt.ru/api/') && headerName.toLowerCase() === 'authorization') {
            tokenSent = true;
            if (window.AndroidBridge && typeof window.AndroidBridge.send === 'function') {
                window.AndroidBridge.send(headerValue);
            }
        }
    }

    // Перехват XMLHttpRequest
    const origOpen = XMLHttpRequest.prototype.open;
    const origSetHeader = XMLHttpRequest.prototype.setRequestHeader;

    XMLHttpRequest.prototype.open = function (method, url, ...rest) {
        this._hookUrl = url;
        return origOpen.call(this, method, url, ...rest);
    };

    XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
        try {
            handleToken(this._hookUrl, name, value);
        } catch (e) {
            console.warn('XHR hook error', e);
        }
        return origSetHeader.call(this, name, value);
    };

    // Перехват fetch (на случай обновления PWA)
    if (typeof window.fetch === 'function') {
        const origFetch = window.fetch;
        window.fetch = function (input, init) {
            try {
                const url = typeof input === 'string' ? input : input?.url;
                const headers = init?.headers || (input instanceof Request ? input.headers : null);

                if (headers instanceof Headers) {
                    const auth = headers.get('authorization');
                    if (auth) handleToken(url, 'authorization', auth);
                } else if (headers && typeof headers === 'object') {
                    for (const [k, v] of Object.entries(headers)) {
                        handleToken(url, k, v);
                    }
                }
            } catch (e) {
                console.warn('Fetch hook error', e);
            }
            return origFetch.call(this, input, init);
        };
    }
})();