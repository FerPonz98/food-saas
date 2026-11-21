(function () {
    'use strict';
    const ESTADO_FACTURA = { PAGADA: ['Pagada', 'badge-ok'], ANULADA: ['Anulada', 'badge-off'], AL_DIA: ['Por pagar', 'badge-info'],
        VENCIDA: ['Vencida', 'badge-warn'], BLOQUEO: ['Vencida', 'badge-warn'] };

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    const bs = v => `Bs ${Number(v || 0).toLocaleString('es', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    const fecha = f => {
        if (!f) return '—';
        const d = new Date(`${String(f).slice(0, 10)}T00:00:00`);
        return isNaN(d) ? f : d.toLocaleDateString('es', { day: 'numeric', month: 'long', year: 'numeric' });
    };

    function lineas(lista) {
        const ul = crear('ul', 'pg-lineas');
        lista.forEach(l => {
            const li = crear('li');
            li.append(crear('span', '', l.concepto), crear('span', '', bs(l.subtotal)));
            ul.append(li);
        });
        return ul;
    }

    function pintar(e) {
        const cont = document.getElementById('plan-pagos');
        const partes = [];
        const resumen = crear('div', 'pg-resumen');
        if (e.pendiente) {
            const p = e.pendiente;
            const vencida = p.situacion !== 'AL_DIA';
            const caja = crear('div', `pg-pendiente${vencida ? ' pg-vencida' : ''}`);
            caja.append(crear('span', 'admin-stat-label', vencida ? 'Pago vencido' : 'Próximo pago'), crear('span', 'pg-monto', bs(p.total)));
            const detalle = vencida
                ? `Venció el ${fecha(p.venceEn)}.` + (p.bloqueoEl ? ` El sistema se bloqueará el ${fecha(p.bloqueoEl)} si no se registra el pago.` : '')
                : `Vence el ${fecha(p.venceEn)}${p.diasParaVencer != null ? ` (en ${p.diasParaVencer} ${p.diasParaVencer === 1 ? 'día' : 'días'})` : ''}.`;
            caja.append(crear('p', 'pg-detalle', `Factura ${p.numero} · ${detalle}`), lineas(p.lineas));
            resumen.append(caja);
        } else {
            const caja = crear('div', 'pg-pendiente pg-aldia');
            caja.append(crear('span', 'admin-stat-label', 'Estás al día'),
                crear('span', 'pg-monto', e.pagadoHasta ? `Hasta el ${fecha(e.pagadoHasta)}` : 'Sin pagos pendientes'));
            if (e.proximoCargo && e.proximoCargo.length) {
                caja.append(crear('p', 'pg-detalle', `Próximo cargo estimado: ${bs(e.proximoTotal)}`), lineas(e.proximoCargo));
            }
            resumen.append(caja);
        }
        partes.push(resumen);
        if (e.instruccionesPago) {
            partes.push(crear('h3', 'inicio-seccion-head', 'Cómo pagar'), crear('p', 'pg-instrucciones', e.instruccionesPago));
        }
        if (e.avisos && e.avisos.length) {
            const ul = crear('ul', 'pg-avisos');
            e.avisos.slice(0, 6).forEach(a => {
                const li = crear('li', `pg-aviso pg-aviso-${a.tipo.toLowerCase()}`);
                const t = crear('time', '', new Date(a.fecha).toLocaleDateString('es', { day: '2-digit', month: 'short' }));
                li.append(t, crear('span', '', a.mensaje));
                ul.append(li);
            });
            partes.push(crear('h3', 'inicio-seccion-head', 'Avisos de pago'), ul);
        }
        if (e.facturas && e.facturas.length) {
            const tabla = crear('table', 'admin-table');
            const thead = crear('thead');
            const trh = crear('tr');
            ['Factura', 'Periodo', 'Total', 'Estado'].forEach(t => trh.append(crear('th', t === 'Total' ? 'num' : '', t)));
            thead.append(trh);
            const tbody = crear('tbody');
            e.facturas.forEach(f => {
                const tr = crear('tr');
                const td = (et, cont2, cls) => { const c = crear('td', cls || ''); c.dataset.label = et; if (cont2 instanceof Node) c.append(cont2); else c.textContent = cont2; tr.append(c); };
                td('Factura', f.numero);
                td('Periodo', `${fecha(f.periodoDesde)} – ${fecha(f.periodoHasta)}`);
                td('Total', bs(f.total), 'num');
                const [txt, cls] = ESTADO_FACTURA[f.estado === 'PENDIENTE' ? f.situacion : f.estado] || [f.estado, 'badge-off'];
                td('Estado', crear('span', `badge ${cls}`, txt));
                tbody.append(tr);
            });
            tabla.append(thead, tbody);
            const envoltura = crear('div', 'admin-table-wrap');
            envoltura.append(tabla);
            partes.push(crear('h3', 'inicio-seccion-head', 'Historial'), envoltura);
        }
        cont.replaceChildren(...partes);
    }

    document.addEventListener('DOMContentLoaded', async () => {
        const cont = document.getElementById('plan-pagos');
        if (!cont) return;
        if (window.Admin) Admin.cargando(cont, 3);
        try {
            const r = await secureFetch('/api/admin/facturacion', { headers: { Accept: 'application/json' } });
            if (!r.ok) throw await apiError(r);
            pintar(await r.json());
            if (new URLSearchParams(location.search).get('seccion') === 'pagos') document.getElementById('pagos').scrollIntoView({ block: 'start' });
        } catch (e) {
            if (window.Admin) Admin.vacio(cont, { icono: '⚠️', titulo: 'No se pudo cargar la información de pagos', texto: e.message });
        }
    });
})();
