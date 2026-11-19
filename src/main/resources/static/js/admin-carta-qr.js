(function () {
    'use strict';
    const N = window.NEGOCIO || {};
    const totalMesas = Number(document.body.dataset.mesas || 0);
    const MESA = N.puntoAtencionSingular || 'Mesa';
    const ROL = (N.etiquetaRolMesero || 'mesero').toLowerCase();
    const MAX_VISTA = 200;

    const form = document.getElementById('form-wifi');
    const estado = document.getElementById('wifi-estado');
    const btnGuardar = document.getElementById('wifi-guardar');
    const desde = document.getElementById('qr-desde');
    const hasta = document.getElementById('qr-hasta');
    const conWifi = document.getElementById('qr-con-wifi');
    const hojas = document.getElementById('qr-hojas');
    const resumen = document.getElementById('qr-resumen');
    let wifi = { nombre: '', clave: '' };
    let version = Date.now();

    function nodo(tag, clase, texto) {
        const n = document.createElement(tag);
        if (clase) n.className = clase;
        if (texto !== undefined) n.textContent = texto;
        return n;
    }

    function error(campo, mensaje) {
        const input = form.elements[campo === 'wifiNombre' ? 'nombre' : 'clave'];
        document.getElementById(`error-${campo}`).textContent = mensaje || '';
        if (mensaje) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
        return !mensaje;
    }

    function validar() {
        const nombre = form.elements.nombre.value.trim();
        const clave = form.elements.clave.value;
        const okNombre = error('wifiNombre', !nombre && clave ? 'Escribe el nombre de la red.' : '');
        const okClave = error('wifiClave', clave && clave.length < 8 ? 'Las claves de Wi-Fi tienen al menos 8 caracteres.' : '');
        if (!okNombre) form.elements.nombre.focus(); else if (!okClave) form.elements.clave.focus();
        return okNombre && okClave;
    }

    function pintarEstadoWifi() {
        const cambiado = form.elements.nombre.value.trim() !== wifi.nombre || form.elements.clave.value !== wifi.clave;
        estado.textContent = cambiado ? 'Tienes cambios sin guardar.' : (wifi.nombre ? 'Guardado.' : 'Sin Wi-Fi para clientes.');
        estado.classList.toggle('pendiente', cambiado);
    }

    async function cargarWifi() {
        try {
            const r = await secureFetch('/api/admin/configuracion/wifi');
            if (!r.ok) throw await apiError(r);
            wifi = await r.json();
            form.elements.nombre.value = wifi.nombre;
            form.elements.clave.value = wifi.clave;
        } catch (e) {
            showNotice(e.message || 'No se pudo cargar el Wi-Fi');
        }
        pintarEstadoWifi();
        pintarCarteles();
    }

    form.addEventListener('input', e => {
        error(e.target.name === 'nombre' ? 'wifiNombre' : 'wifiClave', '');
        pintarEstadoWifi();
    });

    form.addEventListener('submit', async e => {
        e.preventDefault();
        if (!validar()) return;
        btnGuardar.disabled = true;
        btnGuardar.textContent = 'Guardando…';
        try {
            const r = await secureFetch('/api/admin/configuracion/wifi', {
                method: 'PUT', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ nombre: form.elements.nombre.value.trim(), clave: form.elements.clave.value })
            });
            if (!r.ok) throw await apiError(r);
            wifi = await r.json();
            form.elements.nombre.value = wifi.nombre;
            form.elements.clave.value = wifi.clave;
            version = Date.now();
            showNotice(wifi.nombre ? 'Wi-Fi guardado. Si ya imprimiste carteles con otro Wi-Fi, imprímelos de nuevo.' : 'Wi-Fi quitado de la carta', 'ok');
            pintarCarteles();
        } catch (err) {
            showNotice(err.message || 'No se pudo guardar el Wi-Fi');
        } finally {
            btnGuardar.disabled = false;
            btnGuardar.textContent = 'Guardar Wi-Fi';
            pintarEstadoWifi();
        }
    });

    function rango() {
        if (!totalMesas) return [];
        const clamp = v => Math.min(totalMesas, Math.max(1, parseInt(v, 10) || 1));
        let a = clamp(desde.value), b = clamp(hasta.value);
        if (a > b) [a, b] = [b, a];
        const lista = [];
        for (let i = a; i <= b && lista.length < MAX_VISTA; i++) lista.push(i);
        return lista;
    }

    function qr(src, alt, titulo, detalle) {
        const fig = nodo('figure', 'qr-codigo');
        const img = nodo('img');
        img.src = src;
        img.alt = alt;
        img.width = 160;
        img.height = 160;
        img.decoding = 'async';
        const cap = nodo('figcaption');
        cap.append(nodo('strong', '', titulo));
        if (detalle) cap.append(nodo('span', '', detalle));
        fig.append(img, cap);
        return fig;
    }

    function cartel(numero) {
        const art = nodo('article', 'qr-cartel');
        const cab = nodo('header', 'qr-cartel-cab');
        if (N.logoUrl) {
            const logo = nodo('img', 'qr-cartel-logo');
            logo.src = N.logoUrl;
            logo.alt = '';
            cab.append(logo);
        }
        cab.append(nodo('span', 'qr-cartel-nombre', N.nombreNegocio || ''));
        const codigos = nodo('div', 'qr-cartel-codigos');
        codigos.append(qr(numero ? `/admin/qr/mesa/${numero}.svg?v=${version}` : `/admin/qr/carta.svg?v=${version}`,
            'Código QR de la carta', 'Mira la carta', numero ? `y llama al ${ROL}` : 'con fotos'));
        if (conWifi.checked && wifi.nombre) {
            codigos.append(qr(`/admin/qr/wifi.svg?v=${version}`, 'Código QR del Wi-Fi', 'Wi-Fi gratis', wifi.nombre));
        }
        art.append(cab, codigos);
        art.append(nodo('p', 'qr-cartel-pie', numero ? `${MESA} ${numero}` : 'Escanea con la cámara'));
        return art;
    }

    function pintarCarteles() {
        const mesas = rango();
        const lista = totalMesas ? mesas.map(cartel) : [cartel(null)];
        hojas.replaceChildren(...lista);
        conWifi.disabled = !wifi.nombre;
        conWifi.closest('label').classList.toggle('qr-check-off', !wifi.nombre);
        resumen.textContent = totalMesas
            ? `${mesas.length} ${mesas.length === 1 ? 'cartel' : 'carteles'}${!wifi.nombre ? ' · sin Wi-Fi (configúralo arriba para agregarlo)' : ''}.`
            : '1 cartel con el QR de la carta.';
    }

    [desde, hasta].forEach(c => c && c.addEventListener('change', pintarCarteles));
    conWifi.addEventListener('change', pintarCarteles);
    document.getElementById('qr-imprimir').addEventListener('click', async () => {
        const imgs = [...hojas.querySelectorAll('img')];
        await Promise.all(imgs.map(i => i.complete ? null : new Promise(ok => { i.onload = i.onerror = ok; })));
        const raiz = document.documentElement;
        const temaPrevio = raiz.getAttribute('data-theme');
        raiz.setAttribute('data-theme', 'light');
        window.addEventListener('afterprint', () => raiz.setAttribute('data-theme', temaPrevio || 'light'), { once: true });
        window.print();
    });

    pintarCarteles();
    cargarWifi();
})();
