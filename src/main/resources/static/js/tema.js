(function () {
    'use strict';
    const CLAVE = 'tema';
    const consulta = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

    function preferencia() {
        try {
            const valor = localStorage.getItem(CLAVE);
            return valor === 'claro' || valor === 'oscuro' ? valor : 'sistema';
        } catch (e) {
            return 'sistema';
        }
    }

    function modoEfectivo(pref) {
        if (pref === 'claro' || pref === 'oscuro') return pref;
        return consulta && consulta.matches ? 'oscuro' : 'claro';
    }

    function aplicar() {
        const pref = preferencia();
        const modo = modoEfectivo(pref);
        document.documentElement.setAttribute('data-theme', modo === 'oscuro' ? 'dark' : 'light');
        document.querySelectorAll('[data-tema]').forEach(boton => {
            boton.setAttribute('aria-pressed', String(boton.dataset.tema === pref));
        });
        document.querySelectorAll('[data-tema-alternar]').forEach(boton => {
            const siguiente = modo === 'oscuro' ? 'claro' : 'oscuro';
            boton.textContent = siguiente === 'oscuro' ? 'Modo oscuro' : 'Modo claro';
            boton.setAttribute('aria-label', `Cambiar a modo ${siguiente}`);
        });
        document.dispatchEvent(new CustomEvent('tema:cambio', { detail: { modo, preferencia: pref } }));
    }

    function establecer(pref) {
        try {
            if (pref === 'sistema') localStorage.removeItem(CLAVE);
            else localStorage.setItem(CLAVE, pref);
        } catch (e) {
            document.documentElement.setAttribute('data-theme', modoEfectivo(pref) === 'oscuro' ? 'dark' : 'light');
            return;
        }
        aplicar();
    }

    window.Tema = { preferencia, establecer, modo: () => modoEfectivo(preferencia()) };

    const hojaTema = document.querySelector('link[href*="css/theme.css"]');
    const SPRITE = (hojaTema ? hojaTema.getAttribute('href') : '/css/theme.css').replace(/css\/theme\.css.*$/, 'img/iconos.svg');
    const SVG = 'http://www.w3.org/2000/svg';
    const EMOJI_SISTEMA = {
        '⚠️': 'alert', '📡': 'refresh', '🍽️': 'utensils', '👥': 'users', '🗂️': 'grid', '🛒': 'bag',
        '🔍': 'search', '📉': 'chart', '💳': 'card', '🪑': 'store', '🕒': 'clock', '📆': 'clock',
        '🏪': 'building', '❔': 'info', '📝': 'pencil', '🔒': 'lock', '🧾': 'receipt', '🔔': 'bell'
    };
    function icono(nombre, clase) {
        const svg = document.createElementNS(SVG, 'svg');
        svg.setAttribute('class', clase ? `ico ${clase}` : 'ico');
        svg.setAttribute('aria-hidden', 'true');
        svg.setAttribute('focusable', 'false');
        const uso = document.createElementNS(SVG, 'use');
        uso.setAttribute('href', `${SPRITE}#i-${nombre}`);
        svg.append(uso);
        return svg;
    }
    window.Icono = { crear: icono, desdeEmoji: emoji => EMOJI_SISTEMA[emoji] || null, sprite: SPRITE };

    document.addEventListener('click', e => {
        const opcion = e.target.closest('[data-tema]');
        if (opcion) {
            establecer(opcion.dataset.tema);
            return;
        }
        if (e.target.closest('[data-tema-alternar]')) {
            establecer(modoEfectivo(preferencia()) === 'oscuro' ? 'claro' : 'oscuro');
        }
    });
    if (consulta) {
        const alCambiar = () => { if (preferencia() === 'sistema') aplicar(); };
        if (consulta.addEventListener) consulta.addEventListener('change', alCambiar);
        else if (consulta.addListener) consulta.addListener(alCambiar);
    }
    window.addEventListener('storage', e => { if (e.key === CLAVE) aplicar(); });
    document.addEventListener('DOMContentLoaded', aplicar);
})();
