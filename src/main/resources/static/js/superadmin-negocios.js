(function () {
    'use strict';
    const API = '/api/superadmin';
    const ESTADOS = { ACTIVO: 'badge badge-ok', PRUEBA: 'badge badge-info', SUSPENDIDO: 'badge badge-warn', CANCELADO: 'badge badge-off' };
    const fmt = new Intl.NumberFormat('es', { maximumFractionDigits: 0 });
    let planes = [];
    let modulos = [];
    let negocios = [];
    let actual = null;

    const $ = id => document.getElementById(id);

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    async function pedir(url, opciones) {
        const r = opciones ? await secureFetch(url, opciones) : await fetch(url, { headers: { Accept: 'application/json' } });
        if (!r.ok) throw await apiError(r);
        return r.status === 204 ? null : r.json();
    }

    function json(metodo, cuerpo) {
        return { method: metodo, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(cuerpo) };
    }

    function abrir(d) { if (typeof d.showModal === 'function') d.showModal(); else d.setAttribute('open', ''); }
    function cerrar(d) { if (typeof d.close === 'function') d.close(); else d.removeAttribute('open'); }

    function fecha(iso) {
        if (!iso) return '—';
        const d = new Date(iso.length <= 10 ? `${iso}T00:00:00` : iso);
        return isNaN(d) ? iso : d.toLocaleDateString('es', { day: 'numeric', month: 'short', year: 'numeric' });
    }

    function datosForm(form) {
        const datos = {};
        new FormData(form).forEach((v, k) => { datos[k] = typeof v === 'string' && v.trim() === '' ? null : v; });
        return datos;
    }

    function planDe(codigo) { return planes.find(p => p.codigo === codigo); }

    function pintarKpis() {
        const activos = negocios.filter(n => n.estado === 'ACTIVO');
        const mrr = activos.reduce((s, n) => {
            const p = planDe(n.planCodigo);
            if (!p) return s;
            return s + (n.ciclo === 'ANUAL' ? Number(p.precioAnual) / 12 : Number(p.precioMensual));
        }, 0);
        const datos = [
            ['Negocios', negocios.length, 'Registrados en la plataforma'],
            ['Activos', activos.length, 'Con suscripción vigente'],
            ['En prueba', negocios.filter(n => n.estado === 'PRUEBA').length, 'Periodo de prueba'],
            ['Suspendidos', negocios.filter(n => n.estado === 'SUSPENDIDO').length, 'Sin acceso al sistema'],
            ['Ingreso mensual estimado', `Bs ${fmt.format(mrr)}`, 'Suma de suscripciones activas']
        ];
        $('sa-kpis').replaceChildren(...datos.map(([t, v, h]) => {
            const tile = crear('div', 'admin-stat');
            tile.append(crear('span', 'admin-stat-label', t), crear('strong', 'admin-stat-valor', v), crear('span', 'admin-stat-hint', h));
            return tile;
        }));
    }

    function filtrados() {
        const texto = $('sa-buscar').value.trim().toLowerCase();
        const estado = $('sa-filtro-estado').value;
        const plan = $('sa-filtro-plan').value;
        return negocios.filter(n => (!estado || n.estado === estado) && (!plan || n.planCodigo === plan)
            && (!texto || [n.nombre, n.slug, n.contactoNombre, n.contactoEmail, n.contactoTelefono]
                .some(v => v && v.toLowerCase().includes(texto))));
    }

    function pintarLista() {
        const lista = filtrados();
        const cont = $('sa-lista');
        if (!lista.length) {
            Admin.vacio(cont, { icono: '🏪', titulo: negocios.length ? 'Ningún negocio coincide' : 'Todavía no hay negocios', texto: 'Crea uno con «Nuevo negocio».' });
            return;
        }
        Admin.listo(cont);
        cont.replaceChildren(...lista.map(n => {
            const tarjeta = crear('article', 'sa-negocio');
            const cab = crear('div', 'sa-negocio-cab');
            const titulo = crear('div', 'sa-negocio-titulo');
            titulo.append(crear('h3', '', n.nombre), crear('span', 'sa-slug', `/n/${n.slug}`));
            const etiquetas = crear('div', 'sa-etiquetas');
            etiquetas.append(crear('span', 'badge badge-info', n.planNombre), crear('span', ESTADOS[n.estado] || 'badge', n.estadoEtiqueta));
            cab.append(titulo, etiquetas);
            const datos = crear('dl', 'sa-datos');
            [['Vence', n.venceEn ? fecha(n.venceEn) : 'Sin fecha'],
             ['Mesas', n.uso.mesas], ['Productos', n.uso.productos], ['Usuarios', n.uso.usuarios],
             ['Pedidos (30 días)', n.uso.pedidos30Dias], ['Último acceso', n.uso.ultimoAcceso ? fecha(n.uso.ultimoAcceso) : '—']]
                .forEach(([t, v]) => { const d = crear('div'); d.append(crear('dt', '', t), crear('dd', '', v)); datos.append(d); });
            tarjeta.append(cab, datos);
            if (n.avisos.length) {
                const avisos = crear('ul', 'sa-avisos');
                n.avisos.forEach(a => avisos.append(crear('li', '', a)));
                tarjeta.append(avisos);
            }
            const pie = crear('div', 'sa-negocio-pie');
            const gestionar = crear('button', 'button-secondary', 'Gestionar');
            gestionar.type = 'button';
            gestionar.addEventListener('click', () => abrirNegocio(n.id));
            pie.append(gestionar);
            tarjeta.append(pie);
            return tarjeta;
        }));
    }

    async function cargar() {
        Admin.cargando($('sa-lista'), 3);
        try {
            [planes, modulos, negocios] = await Promise.all([pedir(`${API}/planes`), pedir(`${API}/modulos`), pedir(`${API}/negocios`)]);
            const opciones = planes.map(p => { const o = crear('option', '', `${p.nombre}${p.activo ? '' : ' (no disponible)'}`); o.value = p.codigo; return o; });
            $('sa-filtro-plan').replaceChildren(crear('option', '', 'Todos los planes'), ...opciones.map(o => o.cloneNode(true)));
            $('sa-filtro-plan').firstElementChild.value = '';
            $('n-plan').replaceChildren(...planes.filter(p => p.activo).map(p => { const o = crear('option', '', p.nombre); o.value = p.codigo; return o; }));
            $('n-plan').value = planes.some(p => p.codigo === 'PRO') ? 'PRO' : $('n-plan').value;
            $('s-plan').replaceChildren(...opciones);
            pintarKpis();
            pintarLista();
        } catch (e) {
            Admin.vacio($('sa-lista'), { icono: '⚠️', titulo: 'No se pudo cargar la lista', texto: e.message });
        }
    }

    function mostrarAcceso(acceso) {
        $('acc-url').textContent = `${location.origin}/n/${acceso.negocio.slug}/login`;
        $('acc-usuario').textContent = acceso.username;
        $('acc-clave').textContent = acceso.passwordTemporal;
        abrir($('sa-dlg-acceso'));
    }

    function sugerirSlug(nombre) {
        return nombre.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);
    }

    function pestana(nombre) {
        document.querySelectorAll('#sa-dlg-negocio [data-pestana]').forEach(b => b.setAttribute('aria-selected', String(b.dataset.pestana === nombre)));
        document.querySelectorAll('#sa-dlg-negocio [data-panel]').forEach(p => { p.hidden = p.dataset.panel !== nombre; });
        if (nombre === 'registro') cargarRegistro();
    }

    function pintarNegocio() {
        const n = actual;
        $('sa-negocio-titulo').textContent = n.nombre;
        const uso = $('d-uso');
        uso.replaceChildren();
        const plan = planDe(n.planCodigo) || {};
        [['Mesas', n.uso.mesas, plan.mesasIncluidas], ['Productos', n.uso.productos, plan.productosIncluidos], ['Usuarios activos', n.uso.usuarios, plan.usuariosIncluidos]]
            .forEach(([t, v, max]) => {
                const d = crear('div', `sa-uso-item${max != null && v > max ? ' sa-uso-excedido' : ''}`);
                d.append(crear('span', 'admin-stat-label', t), crear('strong', '', max == null ? `${v}` : `${v} / ${max}`));
                uso.append(d);
            });
        $('s-plan').value = n.planCodigo;
        $('s-estado').value = n.estado;
        $('s-ciclo').value = n.ciclo;
        $('s-inicio').value = n.inicioSuscripcion || '';
        $('s-vence').value = n.venceEn || '';
        $('e-nombre').value = n.nombre;
        $('e-slug').value = n.slug;
        $('e-contacto').value = n.contactoNombre || '';
        $('e-telefono').value = n.contactoTelefono || '';
        $('e-email').value = n.contactoEmail || '';
        $('e-notas').value = n.notas || '';
        const planMods = new Set(plan.modulos || []);
        const grupos = new Map();
        modulos.forEach(m => { if (!grupos.has(m.grupo)) grupos.set(m.grupo, []); grupos.get(m.grupo).push(m); });
        const cont = $('d-modulos');
        cont.replaceChildren();
        grupos.forEach((lista, grupo) => {
            cont.append(crear('h3', 'sa-modulos-grupo', grupo));
            lista.forEach(m => {
                const fila = crear('div', 'sa-modulo');
                const texto = crear('div', 'sa-modulo-texto');
                const efectivo = n.modulos.includes(m.codigo);
                texto.append(crear('strong', '', m.etiqueta), crear('span', 'admin-help', `${m.descripcion} · ${planMods.has(m.codigo) ? 'Incluido en el plan' : 'No incluido en el plan'}`));
                const sel = crear('select', 'field');
                sel.name = m.codigo;
                sel.setAttribute('aria-label', `Ajuste de ${m.etiqueta}`);
                [['PLAN', `Según el plan (${planMods.has(m.codigo) ? 'sí' : 'no'})`], ['ACTIVAR', 'Activar'], ['DESACTIVAR', 'Desactivar']]
                    .forEach(([v, t]) => { const o = crear('option', '', t); o.value = v; sel.append(o); });
                const ajuste = n.ajustesModulos[m.codigo];
                sel.value = ajuste === true ? 'ACTIVAR' : ajuste === false ? 'DESACTIVAR' : 'PLAN';
                fila.append(crear('span', efectivo ? 'sa-modulo-estado sa-activo' : 'sa-modulo-estado', efectivo ? '✓' : '—'), texto, sel);
                cont.append(fila);
            });
        });
    }

    async function abrirNegocio(id) {
        try {
            actual = await pedir(`${API}/negocios/${id}`);
        } catch (e) {
            showNotice(e.message);
            return;
        }
        pintarNegocio();
        pestana('suscripcion');
        abrir($('sa-dlg-negocio'));
    }

    async function cargarRegistro() {
        const lista = $('d-registro');
        Admin.cargando(lista, 3);
        try {
            const registros = await pedir(`${API}/registro?negocio=${actual.id}`);
            if (!registros.length) {
                Admin.vacio(lista, { icono: '🕒', titulo: 'Sin cambios registrados', texto: 'Aquí verás los cambios de plan, estado y módulos.' });
                return;
            }
            Admin.listo(lista);
            lista.replaceChildren(...registros.map(r => {
                const li = crear('li', 'sa-registro-item');
                li.append(crear('strong', '', r.accion.replaceAll('_', ' ').toLowerCase()), crear('span', 'admin-help', `${Admin.fechaHora(r.fecha)} · ${r.usuario || '—'}`), crear('span', '', r.detalle || ''));
                return li;
            }));
        } catch (e) {
            Admin.vacio(lista, { icono: '⚠️', titulo: 'No se pudo cargar el historial', texto: e.message });
        }
    }

    function reemplazar(n) {
        actual = n;
        const i = negocios.findIndex(x => x.id === n.id);
        if (i >= 0) negocios[i] = n; else negocios.push(n);
        pintarKpis();
        pintarLista();
        pintarNegocio();
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.sa-dialogo').forEach(d => {
            d.querySelectorAll('[data-cerrar]').forEach(b => b.addEventListener('click', () => cerrar(d)));
            d.addEventListener('click', e => { if (e.target === d) cerrar(d); });
        });
        document.querySelectorAll('#sa-dlg-negocio [data-pestana]').forEach(b => b.addEventListener('click', () => pestana(b.dataset.pestana)));
        ['sa-buscar', 'sa-filtro-estado', 'sa-filtro-plan'].forEach(id => $(id).addEventListener('input', pintarLista));

        let slugTocado = false;
        $('n-slug').addEventListener('input', () => { slugTocado = true; });
        $('n-nombre').addEventListener('input', () => {
            if (slugTocado) return;
            const s = sugerirSlug($('n-nombre').value);
            $('n-slug').value = s;
            if (!$('n-admin').dataset.tocado) $('n-admin').value = s ? `admin.${s}`.slice(0, 40) : '';
        });
        $('n-admin').addEventListener('input', () => { $('n-admin').dataset.tocado = '1'; });
        $('sa-nuevo').addEventListener('click', () => {
            $('sa-form-nuevo').reset();
            slugTocado = false;
            delete $('n-admin').dataset.tocado;
            $('n-error').textContent = '';
            if (planes.some(p => p.codigo === 'PRO')) $('n-plan').value = 'PRO';
            abrir($('sa-dlg-nuevo'));
        });
        $('sa-form-nuevo').addEventListener('submit', async e => {
            e.preventDefault();
            const datos = datosForm(e.target);
            $('n-error').textContent = '';
            const boton = e.target.querySelector('[type=submit]');
            boton.disabled = true;
            try {
                const acceso = await pedir(`${API}/negocios`, json('POST', datos));
                cerrar($('sa-dlg-nuevo'));
                negocios.push(acceso.negocio);
                pintarKpis();
                pintarLista();
                mostrarAcceso(acceso);
            } catch (err) {
                $('n-error').textContent = err.message;
            } finally {
                boton.disabled = false;
            }
        });

        $('sa-form-suscripcion').addEventListener('submit', async e => {
            e.preventDefault();
            const datos = datosForm(e.target);
            const plan = planDe(datos.planCodigo);
            const ok = await confirmAction(null, {
                titulo: '¿Guardar la suscripción?',
                resumen: [
                    { etiqueta: 'Negocio', valor: actual.nombre },
                    { etiqueta: 'Plan', valor: plan ? plan.nombre : datos.planCodigo },
                    { etiqueta: 'Estado', valor: $('s-estado').selectedOptions[0].textContent },
                    { etiqueta: 'Vence', valor: datos.venceEn ? fecha(datos.venceEn) : 'Sin fecha' }
                ],
                aceptar: 'Guardar',
                peligro: datos.estado === 'SUSPENDIDO' || datos.estado === 'CANCELADO'
            });
            if (!ok) return;
            try {
                reemplazar(await pedir(`${API}/negocios/${actual.id}/suscripcion`, json('PUT', datos)));
                showNotice('Suscripción actualizada', 'ok');
            } catch (err) {
                showNotice(err.message);
            }
        });

        $('sa-form-modulos').addEventListener('submit', async e => {
            e.preventDefault();
            const ajustes = {};
            e.target.querySelectorAll('select').forEach(s => { if (s.value !== 'PLAN') ajustes[s.name] = s.value; });
            try {
                reemplazar(await pedir(`${API}/negocios/${actual.id}/modulos`, json('PUT', ajustes)));
                showNotice('Módulos actualizados', 'ok');
            } catch (err) {
                showNotice(err.message);
            }
        });

        $('sa-form-datos').addEventListener('submit', async e => {
            e.preventDefault();
            try {
                reemplazar(await pedir(`${API}/negocios/${actual.id}`, json('PUT', datosForm(e.target))));
                showNotice('Datos guardados', 'ok');
            } catch (err) {
                showNotice(err.message);
            }
        });

        $('s-acceso').addEventListener('click', async () => {
            const ok = await confirmAction(null, {
                titulo: '¿Generar una contraseña temporal?',
                resumen: [{ etiqueta: 'Negocio', valor: actual.nombre }],
                items: [{ nombre: 'El primer administrador del negocio recibirá una contraseña nueva', detalle: '' }, { nombre: 'Sus sesiones abiertas se cerrarán', detalle: '' }],
                aceptar: 'Generar',
                peligro: true
            });
            if (!ok) return;
            try {
                mostrarAcceso(await pedir(`${API}/negocios/${actual.id}/restablecer-acceso`, { method: 'POST' }));
            } catch (err) {
                showNotice(err.message);
            }
        });

        $('acc-copiar').addEventListener('click', async () => {
            const texto = `Ingreso: ${$('acc-url').textContent}\nUsuario: ${$('acc-usuario').textContent}\nContraseña temporal: ${$('acc-clave').textContent}`;
            try {
                await navigator.clipboard.writeText(texto);
                showNotice('Datos copiados', 'ok');
            } catch (err) {
                showNotice('No se pudo copiar. Selecciona el texto manualmente.');
            }
        });

        cargar();
    });
})();
