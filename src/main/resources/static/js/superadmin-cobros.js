(function () {
    'use strict';
    const API = '/api/superadmin';
    const SITUACION = {
        AL_DIA: ['Por cobrar', 'badge-info'],
        VENCIDA: ['Vencida', 'badge-warn'],
        BLOQUEO: ['Vencida · bloqueo', 'badge-warn cb-badge-bloqueo'],
        PAGADA: ['Pagada', 'badge-ok'],
        ANULADA: ['Anulada', 'badge-off']
    };
    let filtro = '';
    let negocios = [];

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    async function pedir(url, opciones) {
        const o = Object.assign({ headers: { 'Content-Type': 'application/json', Accept: 'application/json' } }, opciones || {});
        if (o.body && typeof o.body !== 'string') o.body = JSON.stringify(o.body);
        const r = await secureFetch(url, o);
        if (!r.ok) throw await apiError(r);
        return r.json();
    }

    const bs = v => `Bs ${Number(v || 0).toLocaleString('es', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    const fecha = f => {
        if (!f) return '—';
        const [a, m, d] = String(f).slice(0, 10).split('-');
        return `${d}/${m}/${a}`;
    };

    function kpi(etiqueta, valor, ayuda, destacado) {
        const c = crear('div', `admin-stat${destacado ? ' admin-stat-destacado' : ''}`);
        c.append(crear('span', 'admin-stat-label', etiqueta), crear('strong', 'admin-stat-valor', valor));
        if (ayuda) c.append(crear('span', 'admin-stat-hint', ayuda));
        return c;
    }

    async function cargarKpis() {
        const todas = await pedir(`${API}/facturas`);
        const pendientes = todas.filter(f => f.estado === 'PENDIENTE');
        const vencidas = pendientes.filter(f => f.situacion !== 'AL_DIA');
        const mes = new Date().toISOString().slice(0, 7);
        const cobradoMes = todas.filter(f => f.estado === 'PAGADA' && String(f.pagadaEn || '').startsWith(mes))
            .reduce((s, f) => s + Number(f.total), 0);
        const suspendidos = negocios.filter(n => n.estado === 'SUSPENDIDO').length;
        document.getElementById('cb-kpis').replaceChildren(
            kpi('Por cobrar', bs(pendientes.reduce((s, f) => s + Number(f.total), 0)), `${pendientes.length} facturas pendientes`, true),
            kpi('Vencidas', String(vencidas.length), bs(vencidas.reduce((s, f) => s + Number(f.total), 0))),
            kpi('Cobrado este mes', bs(cobradoMes), 'Pagos registrados'),
            kpi('Negocios suspendidos', String(suspendidos), 'Por falta de pago o manual'));
    }

    async function cargarFacturas() {
        const cont = document.getElementById('cb-facturas');
        Admin.cargando(cont, 4);
        try {
            const lista = await pedir(`${API}/facturas${filtro ? `?estado=${encodeURIComponent(filtro)}` : ''}`);
            if (!lista.length) {
                Admin.vacio(cont, { icono: '🧾', titulo: 'No hay facturas aquí', texto: 'Las facturas se emiten solas antes del vencimiento de cada negocio, o puedes emitir una ahora.' });
                return;
            }
            const tabla = crear('table', 'admin-table');
            const thead = crear('thead');
            const trh = crear('tr');
            ['Factura', 'Negocio', 'Periodo', 'Vence', 'Total', 'Estado', ''].forEach(t => trh.append(crear('th', t === 'Total' ? 'num' : '', t)));
            thead.append(trh);
            const tbody = crear('tbody');
            lista.forEach(f => {
                const tr = crear('tr');
                const celda = (etiqueta, contenido, clase) => {
                    const td = crear('td', clase || '');
                    td.dataset.label = etiqueta;
                    if (contenido instanceof Node) td.append(contenido); else td.textContent = contenido;
                    tr.append(td);
                };
                celda('Factura', crear('code', 'cb-numero', f.numero));
                celda('Negocio', f.negocioNombre || `#${f.negocioId}`);
                celda('Periodo', `${fecha(f.periodoDesde)} – ${fecha(f.periodoHasta)}`);
                const vence = crear('div', 'cb-vence');
                vence.append(crear('span', '', fecha(f.venceEn)));
                if (f.estado === 'PENDIENTE' && f.bloqueoEl) vence.append(crear('small', 'admin-help', `Bloqueo: ${fecha(f.bloqueoEl)}`));
                if (f.estado === 'PAGADA') vence.append(crear('small', 'admin-help', `Pagada ${fecha(f.pagadaEn)} · ${f.metodoPago || ''}`));
                celda('Vence', vence);
                celda('Total', bs(f.total), 'num');
                const [txt, cls] = SITUACION[f.situacion] || [f.estado, 'badge-off'];
                celda('Estado', crear('span', `badge ${cls}`, txt));
                const acciones = crear('div', 'cb-acciones');
                const ver = crear('button', 'button-ghost cb-btn', 'Detalle');
                ver.type = 'button';
                ver.addEventListener('click', () => detalle(f));
                acciones.append(ver);
                if (f.estado === 'PENDIENTE') {
                    const pagar = crear('button', 'button-primary cb-btn', 'Registrar pago');
                    pagar.type = 'button';
                    pagar.addEventListener('click', () => registrarPago(f));
                    const anular = crear('button', 'button-ghost cb-btn cb-anular', 'Anular');
                    anular.type = 'button';
                    anular.addEventListener('click', () => anularFactura(f));
                    acciones.append(pagar, anular);
                }
                celda('Acciones', acciones);
                tbody.append(tr);
            });
            tabla.append(thead, tbody);
            cont.replaceChildren(tabla);
            Admin.listo(cont);
        } catch (e) {
            Admin.vacio(cont, { icono: '⚠️', titulo: 'No se pudieron cargar las facturas', texto: e.message });
        }
    }

    function detalle(f) {
        confirmAction(null, {
            titulo: `Factura ${f.numero}`,
            resumen: [
                { etiqueta: 'Negocio', valor: f.negocioNombre },
                { etiqueta: 'Periodo', valor: `${fecha(f.periodoDesde)} – ${fecha(f.periodoHasta)}` },
                { etiqueta: 'Vence', valor: fecha(f.venceEn) },
                { etiqueta: 'Estado', valor: (SITUACION[f.situacion] || [f.estado])[0] },
                { etiqueta: 'Referencia', valor: f.referenciaPago },
                { etiqueta: 'Nota', valor: f.nota }
            ],
            items: f.lineas.map(l => ({ nombre: l.concepto, detalle: bs(l.subtotal) })),
            total: bs(f.total),
            aceptar: 'Cerrar',
            cancelar: 'Volver'
        });
    }

    async function registrarPago(f) {
        const metodo = await pedirMotivo({
            titulo: `Registrar pago de ${f.numero}`,
            texto: `${f.negocioNombre} · ${bs(f.total)}. El negocio quedará pagado hasta el ${fecha(f.periodoHasta)} y, si estaba suspendido por falta de pago, se reactiva.`,
            sugerencias: ['Transferencia', 'QR', 'Efectivo', 'Depósito'],
            obligatorio: true,
            placeholder: 'Método de pago',
            aceptar: 'Siguiente'
        });
        if (metodo === null) return;
        const referencia = await pedirMotivo({ titulo: 'Referencia del pago', texto: 'Número de comprobante o transacción (opcional).', placeholder: 'Ej.: 00012345', aceptar: 'Registrar pago' });
        if (referencia === null) return;
        try {
            await pedir(`${API}/facturas/${f.id}/pago`, { method: 'PUT', body: { metodo, referencia } });
            showNotice(`Pago de ${f.numero} registrado`, 'ok');
            await recargar();
        } catch (e) {
            showNotice(e.message);
        }
    }

    async function anularFactura(f) {
        const motivo = await pedirMotivo({
            titulo: `¿Anular ${f.numero}?`,
            texto: 'La factura deja de cobrarse y no genera más avisos. Podrás emitir una nueva para el mismo periodo.',
            sugerencias: ['Error en el monto', 'Cambio de plan', 'Cortesía acordada'],
            obligatorio: true,
            aceptar: 'Anular factura',
            peligro: true
        });
        if (motivo === null) return;
        try {
            await pedir(`${API}/facturas/${f.id}/anular`, { method: 'PUT', body: { motivo } });
            showNotice(`${f.numero} anulada`, 'ok');
            await recargar();
        } catch (e) {
            showNotice(e.message);
        }
    }

    async function emitir() {
        const conFecha = negocios.filter(n => n.venceEn && n.estado !== 'CANCELADO');
        if (!conFecha.length) {
            showNotice('Ningún negocio tiene fecha de vencimiento. Defínela en Negocios → Gestionar → Suscripción.');
            return;
        }
        const nombre = await pedirMotivo({
            titulo: 'Emitir factura',
            texto: 'Elige el negocio. Se factura el periodo que empieza en su fecha de vencimiento, con su plan y sus módulos extra.',
            sugerencias: conFecha.map(n => n.nombre),
            obligatorio: true,
            placeholder: 'Nombre del negocio',
            aceptar: 'Emitir'
        });
        if (nombre === null) return;
        const n = conFecha.find(x => x.nombre.toLowerCase() === nombre.toLowerCase());
        if (!n) { showNotice('No encontramos ese negocio'); return; }
        try {
            const f = await pedir(`${API}/negocios/${n.id}/facturas`, { method: 'POST' });
            showNotice(`Factura ${f.numero} emitida por ${bs(f.total)}`, 'ok');
            await recargar();
        } catch (e) {
            showNotice(e.message);
        }
    }

    async function ejecutar(boton) {
        boton.disabled = true;
        try {
            const r = await pedir(`${API}/cobros/ejecutar`, { method: 'POST' });
            showNotice(`Revisión hecha: ${r.facturasEmitidas} facturas emitidas, ${r.avisosEnviados} avisos, ${r.negociosSuspendidos} suspendidos`, 'ok');
            await recargar();
        } catch (e) {
            showNotice(e.message);
        } finally {
            boton.disabled = false;
        }
    }

    async function cargarReglas() {
        const p = await pedir(`${API}/cobros/parametros`);
        const form = document.getElementById('cb-reglas');
        form.elements.diasGeneracion.value = p.diasGeneracion;
        form.elements.diasRecordatorio.value = p.diasRecordatorio;
        form.elements.diasGracia.value = p.diasGracia;
        form.elements.suspensionAutomatica.checked = !!p.suspensionAutomatica;
        form.elements.cobranzaActiva.checked = !!p.cobranzaActiva;
        form.elements.instruccionesPago.value = p.instruccionesPago || '';
    }

    async function guardarReglas(e) {
        e.preventDefault();
        const form = e.currentTarget;
        const num = n => form.elements[n].value === '' ? null : Number(form.elements[n].value);
        try {
            await pedir(`${API}/cobros/parametros`, {
                method: 'PUT',
                body: {
                    diasGeneracion: num('diasGeneracion'), diasRecordatorio: num('diasRecordatorio'), diasGracia: num('diasGracia'),
                    suspensionAutomatica: form.elements.suspensionAutomatica.checked, instruccionesPago: form.elements.instruccionesPago.value,
                    cobranzaActiva: form.elements.cobranzaActiva.checked
                }
            });
            showNotice('Reglas de cobro guardadas', 'ok');
            await cargarKpis();
        } catch (err) {
            showNotice(err.message);
        }
    }

    async function cargarPrecios() {
        const cont = document.getElementById('cb-precios');
        const precios = await pedir(`${API}/cobros/precios`);
        const tabla = crear('table', 'admin-table cb-precios-tabla');
        const thead = crear('thead');
        const trh = crear('tr');
        ['Módulo', 'Mensual (Bs)', 'Anual (Bs)', 'Se vende aparte', ''].forEach(t => trh.append(crear('th', '', t)));
        thead.append(trh);
        const tbody = crear('tbody');
        precios.forEach(p => {
            const tr = crear('tr');
            const td = (etiqueta, el) => { const c = crear('td'); c.dataset.label = etiqueta; c.append(el); tr.append(c); return c; };
            td('Módulo', crear('strong', '', p.etiqueta));
            const entrada = (valor, etiqueta) => {
                const i = crear('input', 'field cb-precio');
                i.type = 'number'; i.min = '0'; i.step = '0.01'; i.inputMode = 'decimal';
                i.value = Number(valor).toFixed(2);
                i.setAttribute('aria-label', `${etiqueta} de ${p.etiqueta}`);
                return i;
            };
            const mensual = entrada(p.precioMensual, 'Precio mensual');
            const anual = entrada(p.precioAnual, 'Precio anual');
            td('Mensual', mensual);
            td('Anual', anual);
            const aparte = crear('input');
            aparte.type = 'checkbox';
            aparte.checked = !!p.seVendeAparte;
            aparte.setAttribute('aria-label', `${p.etiqueta} se vende aparte`);
            td('Aparte', aparte);
            const guardar = crear('button', 'button-secondary cb-btn', 'Guardar');
            guardar.type = 'button';
            guardar.addEventListener('click', async () => {
                guardar.disabled = true;
                try {
                    await pedir(`${API}/cobros/precios/${encodeURIComponent(p.modulo)}`, {
                        method: 'PUT', body: { precioMensual: Number(mensual.value), precioAnual: Number(anual.value), seVendeAparte: aparte.checked }
                    });
                    showNotice(`Precio de ${p.etiqueta} guardado`, 'ok');
                } catch (e) {
                    showNotice(e.message);
                } finally {
                    guardar.disabled = false;
                }
            });
            td('', guardar);
            tbody.append(tr);
        });
        tabla.append(thead, tbody);
        cont.replaceChildren(tabla);
    }

    async function recargar() {
        negocios = await pedir(`${API}/negocios`);
        await Promise.all([cargarKpis(), cargarFacturas()]);
    }

    document.addEventListener('DOMContentLoaded', async () => {
        document.getElementById('cb-filtros').addEventListener('click', e => {
            const b = e.target.closest('[data-estado]');
            if (!b) return;
            filtro = b.dataset.estado;
            document.querySelectorAll('#cb-filtros [data-estado]').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
            cargarFacturas();
        });
        document.getElementById('cb-emitir').addEventListener('click', emitir);
        document.getElementById('cb-ejecutar').addEventListener('click', e => ejecutar(e.currentTarget));
        document.getElementById('cb-reglas').addEventListener('submit', guardarReglas);
        try {
            await Promise.all([recargar(), cargarReglas(), cargarPrecios()]);
        } catch (e) {
            showNotice(e.message);
        }
    });
})();
