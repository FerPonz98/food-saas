(function () {
    'use strict';
    const N = window.NEGOCIO || {};
    const MODULOS = N.modulos || [];
    const CON_LLAMADOS = MODULOS.includes('LLAMADOS');
    const CON_PEDIDOS = MODULOS.includes('PEDIDOS_QR');
    const MESA = N.puntoAtencionSingular || 'Mesa';
    const ROL = (N.etiquetaRolMesero || 'mesero').toLowerCase();
    const MONEDA = N.simboloMoneda || 'Bs.';
    const PAGOS = { EFECTIVO: '💵 Efectivo', TARJETA: '💳 Tarjeta', QR: '📱 Pago QR' };
    let llamados = [];
    let solicitudes = [];
    let boton, contador, panel, listaEl, alertas;
    let audio = null;

    function nodo(tag, clase, texto) {
        const n = document.createElement(tag);
        if (clase) n.className = clase;
        if (texto !== undefined) n.textContent = texto;
        return n;
    }

    function boton_(texto, clase, accion) {
        const b = nodo('button', clase, texto);
        b.type = 'button';
        b.addEventListener('click', () => accion(b));
        return b;
    }

    function dinero(v) {
        return `${MONEDA} ${Number(v || 0).toFixed(2)}`;
    }

    function titulo(l) {
        return l.motivo === 'CUENTA' ? `${MESA} ${l.numeroMesa} pide la cuenta` : `${MESA} ${l.numeroMesa} llama al ${ROL}`;
    }

    function icono(l) { return l.motivo === 'CUENTA' ? '🧾' : '🔔'; }

    function hace(fecha) {
        const min = Math.max(0, Math.round((Date.now() - new Date(fecha).getTime()) / 60000));
        return min < 1 ? 'Recién' : `Hace ${min} min`;
    }

    function total() { return llamados.length + solicitudes.length; }

    function montar() {
        boton = nodo('button', 'llm-boton flat');
        boton.type = 'button';
        boton.hidden = true;
        boton.append(nodo('span', 'llm-boton-icono', '🔔'));
        contador = nodo('span', 'llm-boton-num', '0');
        boton.append(contador);
        boton.addEventListener('click', abrirPanel);

        panel = nodo('dialog', 'llm-panel');
        panel.setAttribute('aria-labelledby', 'llm-panel-titulo');
        const cab = nodo('div', 'llm-panel-cab');
        const h = nodo('h2', '', 'Avisos de las mesas');
        h.id = 'llm-panel-titulo';
        const cerrar = boton_('✕', 'llm-cerrar flat', () => panel.close());
        cerrar.setAttribute('aria-label', 'Cerrar avisos');
        cab.append(h, cerrar);
        listaEl = nodo('ul', 'llm-lista');
        panel.append(cab, listaEl);
        panel.addEventListener('click', e => { if (e.target === panel) panel.close(); });

        alertas = nodo('div', 'llm-alertas');
        alertas.setAttribute('aria-live', 'assertive');
        document.body.append(boton, panel, alertas);

        const desbloquear = () => {
            try {
                audio = audio || new (window.AudioContext || window.webkitAudioContext)();
                if (audio.state === 'suspended') audio.resume();
            } catch (e) { audio = null; }
        };
        document.addEventListener('pointerdown', desbloquear, { once: true, capture: true });
    }

    function accionesLlamado(l) {
        const caja = nodo('div', 'llm-acciones');
        if (!l.confirmadoEn) caja.append(boton_('🏃 Voy', 'button-secondary llm-atender', b => confirmar(l.id, b)));
        caja.append(boton_('✓ Atendido', 'button-primary llm-atender', b => atender(l.id, b)));
        return caja;
    }

    function metaLlamado(l) {
        const meta = [hace(l.creadoEn)];
        if (l.metodoPago) meta.unshift(PAGOS[l.metodoPago] || l.metodoPago);
        if (l.confirmadoEn) meta.push(`Va ${l.confirmadoPor || 'alguien'}`);
        return meta.join(' · ');
    }

    function itemLlamado(l) {
        const li = nodo('li', `llm-item llm-${l.motivo === 'CUENTA' ? 'cuenta' : 'mesero'}${l.confirmadoEn ? ' llm-confirmado' : ''}`);
        const info = nodo('div', 'llm-item-info');
        info.append(nodo('span', 'llm-item-titulo', `${icono(l)} ${titulo(l)}`), nodo('span', 'llm-item-meta', metaLlamado(l)));
        li.append(info, accionesLlamado(l));
        return li;
    }

    function itemSolicitud(s) {
        const li = nodo('li', 'llm-item llm-pedido');
        const info = nodo('div', 'llm-item-info');
        info.append(nodo('span', 'llm-item-titulo', `📝 ${MESA} ${s.numeroMesa} pide desde la carta${s.nombreCliente ? ` · ${s.nombreCliente}` : ''}`));
        const ul = nodo('ul', 'llm-pedido-items');
        s.items.forEach(i => {
            const fila = nodo('li', '', `${i.cantidad} × ${i.nombre}`);
            if (i.notas) fila.append(nodo('span', 'llm-pedido-nota', ` — ${i.notas}`));
            ul.append(fila);
        });
        info.append(ul);
        if (s.nota) info.append(nodo('span', 'llm-item-meta', `Nota: ${s.nota}`));
        const meta = [dinero(s.total), hace(s.creadoEn)];
        if (s.pedidoAbiertoId) meta.push(`Se suma al pedido abierto #${s.pedidoAbiertoId}`);
        info.append(nodo('span', 'llm-item-meta', meta.join(' · ')));

        const caja = nodo('div', 'llm-acciones');
        const motivo = nodo('input', 'field llm-motivo');
        motivo.type = 'text';
        motivo.maxLength = 200;
        motivo.placeholder = 'Motivo (opcional)';
        motivo.setAttribute('aria-label', 'Motivo del rechazo');
        motivo.hidden = true;
        const rechazar = boton_('Rechazar', 'button-secondary llm-atender', b => {
            if (motivo.hidden) {
                motivo.hidden = false;
                b.textContent = 'Confirmar rechazo';
                motivo.focus();
                return;
            }
            revisar(s.id, false, b, motivo.value);
        });
        caja.append(motivo, rechazar, boton_('✓ Aprobar', 'button-primary llm-atender', b => revisar(s.id, true, b)));
        li.append(info, caja);
        return li;
    }

    function render() {
        const n = total();
        contador.textContent = String(n);
        boton.hidden = n === 0;
        boton.setAttribute('aria-label', `${n} ${n === 1 ? 'aviso pendiente' : 'avisos pendientes'} de las mesas`);
        boton.classList.toggle('llm-boton-cuenta', llamados.some(l => l.motivo === 'CUENTA') && !solicitudes.length);
        boton.classList.toggle('llm-boton-pedido', solicitudes.length > 0);
        if (!n) {
            listaEl.replaceChildren(nodo('li', 'llm-vacio', 'No hay avisos pendientes.'));
            return;
        }
        listaEl.replaceChildren(...solicitudes.map(itemSolicitud), ...llamados.map(itemLlamado));
    }

    function abrirPanel() {
        render();
        if (typeof panel.showModal === 'function') panel.showModal(); else panel.setAttribute('open', '');
    }

    async function cargar() {
        try {
            if (CON_LLAMADOS) {
                const r = await fetch('/api/llamados', { headers: { Accept: 'application/json' } });
                if (r.ok) llamados = await r.json();
            }
            if (CON_PEDIDOS) {
                const r = await fetch('/api/solicitudes', { headers: { Accept: 'application/json' } });
                if (r.ok) solicitudes = await r.json();
            }
            render();
            [...alertas.children].forEach(a => {
                const vigente = a.dataset.tipo === 'pedido'
                    ? solicitudes.some(s => String(s.id) === a.dataset.id)
                    : llamados.some(l => String(l.id) === a.dataset.id);
                if (!vigente) a.remove();
            });
        } catch (e) {
            return;
        }
    }

    async function confirmar(id, btn) {
        btn.disabled = true;
        try {
            const r = await secureFetch(`/api/llamados/${id}/confirmar`, { method: 'PUT' });
            if (!r.ok) throw await apiError(r);
            actualizarLlamado(await r.json());
        } catch (e) {
            btn.disabled = false;
            showNotice(e.message || 'No se pudo confirmar el aviso');
        }
    }

    async function atender(id, btn) {
        if (btn) btn.disabled = true;
        try {
            const r = await secureFetch(`/api/llamados/${id}/atender`, { method: 'PUT' });
            if (!r.ok) throw await apiError(r);
            quitarLlamado(id);
        } catch (e) {
            if (btn) btn.disabled = false;
            showNotice(e.message || 'No se pudo marcar como atendido');
        }
    }

    async function revisar(id, aprobar, btn, motivo) {
        btn.disabled = true;
        try {
            const r = await secureFetch(`/api/solicitudes/${id}/${aprobar ? 'aprobar' : 'rechazar'}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(aprobar ? { agregarAlAbierto: true } : { motivo: motivo || null })
            });
            if (!r.ok) throw await apiError(r);
            quitarSolicitud(id);
            showNotice(aprobar ? 'Pedido enviado a cocina' : 'Pedido rechazado. El cliente verá el aviso en su carta.', 'ok');
        } catch (e) {
            btn.disabled = false;
            showNotice(e.message || 'No se pudo revisar el pedido');
        }
    }

    function actualizarLlamado(l) {
        const i = llamados.findIndex(x => String(x.id) === String(l.id));
        if (i >= 0) llamados[i] = Object.assign({}, llamados[i], l); else llamados.push(l);
        const a = alertas.querySelector(`[data-tipo="llamado"][data-id="${l.id}"]`);
        if (a && l.confirmadoEn) a.remove();
        render();
    }

    function quitarLlamado(id) {
        llamados = llamados.filter(l => String(l.id) !== String(id));
        const a = alertas.querySelector(`[data-tipo="llamado"][data-id="${id}"]`);
        if (a) a.remove();
        render();
        if (!total() && panel.open) panel.close();
    }

    function quitarSolicitud(id) {
        solicitudes = solicitudes.filter(s => String(s.id) !== String(id));
        const a = alertas.querySelector(`[data-tipo="pedido"][data-id="${id}"]`);
        if (a) a.remove();
        render();
        if (!total() && panel.open) panel.close();
    }

    function sonar(tonos) {
        if (navigator.vibrate) navigator.vibrate([200, 100, 200]);
        if (!audio) return;
        try {
            tonos.forEach((f, i) => {
                const osc = audio.createOscillator();
                const vol = audio.createGain();
                const t = audio.currentTime + i * 0.22;
                osc.frequency.value = f;
                vol.gain.setValueAtTime(0.0001, t);
                vol.gain.exponentialRampToValueAtTime(0.35, t + 0.02);
                vol.gain.exponentialRampToValueAtTime(0.0001, t + 0.2);
                osc.connect(vol).connect(audio.destination);
                osc.start(t);
                osc.stop(t + 0.21);
            });
        } catch (e) {
            audio = null;
        }
    }

    function alertar(tipo, id, clase, textoTitulo, textoMeta, acciones) {
        const previa = alertas.querySelector(`[data-tipo="${tipo}"][data-id="${id}"]`);
        if (previa) previa.remove();
        const a = nodo('div', `llm-alerta ${clase}`);
        a.dataset.id = String(id);
        a.dataset.tipo = tipo;
        a.setAttribute('role', 'alert');
        const info = nodo('div', 'llm-item-info');
        info.append(nodo('span', 'llm-item-titulo', textoTitulo));
        if (textoMeta) info.append(nodo('span', 'llm-item-meta', textoMeta));
        const x = boton_('✕', 'llm-cerrar flat', () => a.remove());
        x.setAttribute('aria-label', 'Ocultar este aviso (sigue pendiente)');
        a.append(info, acciones, x);
        alertas.prepend(a);
        while (alertas.children.length > 3) alertas.lastElementChild.remove();
    }

    function recibir(n) {
        if (!n || !boton) return;
        if (n.tipo === 'LLAMADO_MESA' && CON_LLAMADOS) {
            const l = { id: n.llamadoId, numeroMesa: n.numeroMesa, motivo: n.motivo, metodoPago: n.metodoPago,
                creadoEn: n.creadoEn, confirmadoEn: n.confirmadoEn, confirmadoPor: n.confirmadoPor };
            actualizarLlamado(l);
            alertar('llamado', l.id, `llm-${l.motivo === 'CUENTA' ? 'cuenta' : 'mesero'}`,
                `${icono(l)} ${titulo(l)}${n.recordatorio ? ' (otra vez)' : ''}`,
                l.metodoPago ? (PAGOS[l.metodoPago] || l.metodoPago) : '', accionesLlamado(l));
            sonar(l.motivo === 'CUENTA' ? [880, 1175, 880] : [988, 784]);
        } else if (n.tipo === 'LLAMADO_CONFIRMADO') {
            actualizarLlamado({ id: n.llamadoId, confirmadoEn: n.confirmadoEn, confirmadoPor: n.confirmadoPor,
                numeroMesa: n.numeroMesa, motivo: n.motivo, metodoPago: n.metodoPago, creadoEn: n.creadoEn });
        } else if (n.tipo === 'LLAMADO_ATENDIDO') {
            quitarLlamado(n.llamadoId);
        } else if (n.tipo === 'PEDIDO_QR' && CON_PEDIDOS) {
            cargar();
            const ver = boton_('Revisar', 'button-primary llm-atender', () => abrirPanel());
            alertar('pedido', n.solicitudId, 'llm-pedido', `📝 ${MESA} ${n.numeroMesa} envió un pedido desde la carta`,
                dinero(n.total), ver);
            sonar([784, 988, 1175]);
        } else if (n.tipo === 'PEDIDO_QR_REVISADO') {
            quitarSolicitud(n.solicitudId);
        }
    }

    window.Llamados = {
        init() {
            if (boton || (!CON_LLAMADOS && !CON_PEDIDOS)) return;
            montar();
            render();
            cargar();
            setInterval(cargar, 30000);
            document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') cargar(); });
        },
        recibir,
        recargar: () => { if (boton) cargar(); }
    };
})();
