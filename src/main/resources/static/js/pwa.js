(function () {
    'use strict';
    if (!('serviceWorker' in navigator)) return;
    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(function (e) {
            console.warn('[PWA] No se pudo registrar el service worker:', e);
        });
    });
})();
