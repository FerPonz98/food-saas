(function () {
    'use strict';
    const API = '/api/superadmin';
    const NOMBRES = { colorPrimario: 'Primario', colorSecundario: 'Secundario', colorAcento: 'Acento' };

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    async function pedir(url, opciones) {
        const o = Object.assign({ headers: { Accept: 'application/json' } }, opciones || {});
        if (o.json !== undefined) {
            o.headers['Content-Type'] = 'application/json';
            o.body = JSON.stringify(o.json);
            delete o.json;
        }
        const r = await secureFetch(url, o);
        if (!r.ok) throw await apiError(r);
        return r.json();
    }

    function selectorColores(prefijo, valores, alCambiar) {
        const fila = crear('div', 'mp-colores-fila');
        Object.entries(NOMBRES).forEach(([clave, etiqueta]) => {
            const caja = crear('label', 'mp-color');
            const input = crear('input');
            input.type = 'color';
            input.name = clave;
            input.value = (valores[clave] || '').toLowerCase();
            input.id = `${prefijo}-${clave}`;
            const textos = crear('span', 'mp-color-textos');
            const codigo = crear('code', null, String(valores[clave] || '').toUpperCase());
            textos.append(crear('span', 'mp-color-nombre', etiqueta), codigo);
            input.addEventListener('input', () => { codigo.textContent = input.value.toUpperCase(); alCambiar(); });
            caja.append(input, textos);
            fila.append(caja);
        });
        return fila;
    }

    function leerColores(contenedor) {
        const v = {};
        contenedor.querySelectorAll('input[type="color"]').forEach(i => { v[i.name] = i.value.toUpperCase(); });
        return v;
    }

    function pintarMuestra(muestra, colores, nombre) {
        muestra.style.setProperty('--muestra-primario', colores.colorPrimario);
        muestra.style.setProperty('--muestra-secundario', colores.colorSecundario);
        muestra.style.setProperty('--muestra-acento', colores.colorAcento);
        const n = muestra.querySelector('[data-muestra-nombre]');
        if (n && nombre !== undefined) n.textContent = nombre;
    }

    async function cargarMarca() {
        const m = await pedir(`${API}/marca`);
        const form = document.getElementById('mp-form');
        form.elements.nombre.value = m.nombre || '';
        form.elements.eslogan.value = m.eslogan || '';
        const cont = form.querySelector('[data-colores="mp"]');
        const muestra = document.getElementById('mp-muestra');
        const refrescar = () => pintarMuestra(muestra, leerColores(cont), form.elements.nombre.value);
        cont.replaceChildren(selectorColores('mp', m, refrescar));
        form.elements.nombre.oninput = refrescar;
        refrescar();
        document.getElementById('mp-logo').src = m.logoUrl;
        muestra.querySelector('.mp-muestra-logo').style.backgroundImage = `url("${m.logoUrl}")`;
        document.getElementById('mp-quitar').hidden = !m.logoPropio;
    }

    async function guardarMarca(e) {
        e.preventDefault();
        const form = e.currentTarget;
        const colores = leerColores(form);
        const ok = await confirmAction('El panel de superadministración se verá con estos colores y nombre. Los negocios existentes no cambian.', {
            titulo: '¿Guardar la marca de la plataforma?',
            resumen: [
                { etiqueta: 'Nombre', valor: form.elements.nombre.value.trim() },
                { etiqueta: 'Colores', valor: `${colores.colorPrimario} · ${colores.colorSecundario} · ${colores.colorAcento}` }
            ],
            aceptar: 'Guardar'
        });
        if (!ok) return;
        try {
            await pedir(`${API}/marca`, { method: 'PUT', json: { nombre: form.elements.nombre.value, eslogan: form.elements.eslogan.value, ...colores } });
            showNotice('Marca guardada. Actualizando el panel…', 'ok');
            setTimeout(() => location.reload(), 700);
        } catch (err) {
            showNotice(err.message);
        }
    }

    async function subirLogo(e) {
        const archivo = e.target.files && e.target.files[0];
        e.target.value = '';
        if (!archivo) return;
        const datos = new FormData();
        datos.append('archivo', archivo);
        try {
            await pedir(`${API}/marca/logo`, { method: 'PUT', body: datos });
            showNotice('Logo de la plataforma actualizado', 'ok');
            setTimeout(() => location.reload(), 600);
        } catch (err) {
            showNotice(err.message);
        }
    }

    async function quitarLogo() {
        const ok = await confirmAction('Se usará el logo genérico en el panel de superadministración.', { titulo: '¿Quitar el logo de la plataforma?', aceptar: 'Quitar', peligro: true });
        if (!ok) return;
        try {
            await pedir(`${API}/marca/logo`, { method: 'DELETE' });
            setTimeout(() => location.reload(), 300);
        } catch (err) {
            showNotice(err.message);
        }
    }

    function tarjetaNegocio(n) {
        const card = crear('article', 'mn-negocio');
        const cab = crear('div', 'mn-cab');
        const img = crear('img', 'mn-logo');
        img.alt = '';
        if (n.logoUrl) img.src = n.logoUrl;
        const textos = crear('div', 'mn-textos');
        textos.append(crear('strong', null, n.nombreVisible || n.nombre), crear('code', 'mn-slug', `/n/${n.slug}`));
        cab.append(img, textos);
        const muestra = crear('div', 'mp-muestra mn-muestra');
        muestra.setAttribute('aria-hidden', 'true');
        muestra.innerHTML = '<div class="mp-muestra-barra"><span data-muestra-nombre></span></div><div class="mp-muestra-cuerpo"><span class="mp-muestra-chip">Secundario</span><span class="mp-muestra-boton">Acento</span></div>';
        if (!n.colorPrimario) {
            card.append(cab, crear('p', 'admin-help', 'Este negocio aún no tiene configuración.'));
            return card;
        }
        const colores = crear('div');
        const refrescar = () => pintarMuestra(muestra, leerColores(colores), n.nombreVisible || n.nombre);
        colores.append(selectorColores(`mn-${n.id}`, n, refrescar));
        const guardar = crear('button', 'button-primary mn-guardar', 'Guardar colores');
        guardar.type = 'button';
        guardar.addEventListener('click', async () => {
            const v = leerColores(colores);
            const ok = await confirmAction('El negocio y su carta QR se verán con estos colores de inmediato.', {
                titulo: `¿Cambiar los colores de ${n.nombreVisible || n.nombre}?`,
                resumen: [{ etiqueta: 'Colores', valor: `${v.colorPrimario} · ${v.colorSecundario} · ${v.colorAcento}` }],
                aceptar: 'Cambiar colores'
            });
            if (!ok) return;
            guardar.disabled = true;
            try {
                await pedir(`${API}/negocios/${n.id}/colores`, { method: 'PUT', json: v });
                showNotice(`Colores de ${n.nombreVisible || n.nombre} guardados`, 'ok');
            } catch (err) {
                showNotice(err.message);
            } finally {
                guardar.disabled = false;
            }
        });
        card.append(cab, muestra, colores, guardar);
        refrescar();
        return card;
    }

    async function cargarNegocios() {
        const cont = document.getElementById('mn-lista');
        Admin.cargando(cont, 3);
        try {
            const lista = await pedir(`${API}/apariencia/negocios`);
            if (!lista.length) { Admin.vacio(cont, { icono: '🏪', titulo: 'Todavía no hay negocios' }); return; }
            cont.replaceChildren(...lista.map(tarjetaNegocio));
            Admin.listo(cont);
        } catch (err) {
            Admin.vacio(cont, { icono: '⚠️', titulo: 'No se pudieron cargar los negocios', texto: err.message });
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById('mp-form').addEventListener('submit', guardarMarca);
        document.getElementById('mp-archivo').addEventListener('change', subirLogo);
        document.getElementById('mp-quitar').addEventListener('click', quitarLogo);
        cargarMarca().catch(err => showNotice(err.message));
        cargarNegocios();
    });
})();
