(function () {
    'use strict';
    const N = window.NEGOCIO || {};
    const MONEDA = N.simboloMoneda || 'Bs.';
    const METODOS = [['EFECTIVO', '💵 Efectivo'], ['QR', '📱 QR'], ['TARJETA', '💳 Tarjeta']];
    const MODOS = [['PRODUCTOS', 'Por productos'], ['PARTES', 'Partes iguales'], ['MONTO', 'Otro monto']];
    let dialogo = null;
    let cuenta = null;
    let modo = 'PRODUCTOS';
    let elegidas = new Map();
    let partes = 2;
    let alTerminar = null;

    function nodo(tag, clase, texto) {
        const n = document.createElement(tag);
        if (clase) n.className = clase;
        if (texto !== undefined && texto !== null) n.textContent = String(texto);
        return n;
    }

    function dinero(v) {
        return `${MONEDA} ${Number(v || 0).toFixed(2)}`;
    }

    function redondear(v) {
        return Math.round(v * 100) / 100;
    }

    function montar() {
        dialogo = nodo('dialog', 'dvc-dialogo');
        dialogo.setAttribute('aria-labelledby', 'dvc-titulo');
        dialogo.addEventListener('click', e => { if (e.target === dialogo) cerrar(); });
        dialogo.addEventListener('close', () => { if (alTerminar) alTerminar(); });
        document.body.append(dialogo);
    }

    function cerrar() {
        if (dialogo && dialogo.open) dialogo.close();
    }

    function montoActual() {
        if (!cuenta) return 0;
        if (modo === 'PRODUCTOS') {
            let suma = 0;
            cuenta.lineas.forEach(l => { suma += (elegidas.get(l.detalleId) || 0) * l.precioUnitario; });
            return Math.min(redondear(suma), cuenta.saldo);
        }
        if (modo === 'PARTES') {
            const parte = redondear(cuenta.total / Math.max(2, partes));
            return cuenta.saldo - parte < partes * 0.01 ? cuenta.saldo : parte;
        }
        const campo = dialogo.querySelector('#dvc-monto');
        const valor = campo ? Number(String(campo.value).replace(',', '.')) : 0;
        return Number.isFinite(valor) && valor > 0 ? Math.min(redondear(valor), cuenta.saldo) : 0;
    }

    function actualizarBoton() {
        const boton = dialogo.querySelector('#dvc-cobrar');
        if (!boton) return;
        const monto = montoActual();
        boton.disabled = !(monto > 0);
        boton.textContent = monto > 0 ? `Cobrar esta parte · ${dinero(monto)}` : 'Cobrar esta parte';
    }

    function seccionProductos() {
        const caja = nodo('div', 'dvc-productos');
        const pendientes = cuenta.lineas.filter(l => l.cantidad - l.cantidadPagada > 0);
        if (!pendientes.length) {
            caja.append(nodo('p', 'dvc-ayuda', 'Todos los productos ya están asignados a algún pago. Cobra el saldo con «Otro monto».'));
            return caja;
        }
        pendientes.forEach(l => {
            const libres = l.cantidad - l.cantidadPagada;
            const fila = nodo('div', 'dvc-linea');
            const info = nodo('div', 'dvc-linea-info');
            info.append(nodo('strong', '', l.nombre), nodo('span', 'dvc-ayuda', `${dinero(l.precioUnitario)} c/u · quedan ${libres} de ${l.cantidad}`));
            const control = nodo('div', 'dvc-stepper');
            control.setAttribute('role', 'group');
            control.setAttribute('aria-label', `Cantidad de ${l.nombre} para esta persona`);
            const menos = nodo('button', 'button-secondary', '−');
            menos.type = 'button';
            menos.setAttribute('aria-label', 'Uno menos');
            const valor = nodo('output', 'dvc-valor', elegidas.get(l.detalleId) || 0);
            const mas = nodo('button', 'button-secondary', '+');
            mas.type = 'button';
            mas.setAttribute('aria-label', 'Uno más');
            const pintar = () => {
                const v = elegidas.get(l.detalleId) || 0;
                valor.textContent = String(v);
                menos.disabled = v <= 0;
                mas.disabled = v >= libres;
                actualizarBoton();
            };
            menos.addEventListener('click', () => { elegidas.set(l.detalleId, Math.max(0, (elegidas.get(l.detalleId) || 0) - 1)); pintar(); });
            mas.addEventListener('click', () => { elegidas.set(l.detalleId, Math.min(libres, (elegidas.get(l.detalleId) || 0) + 1)); pintar(); });
            control.append(menos, valor, mas);
            fila.append(info, control);
            caja.append(fila);
            pintar();
        });
        return caja;
    }

    function seccionPartes() {
        const caja = nodo('div', 'dvc-partes');
        const etiqueta = nodo('label', 'dvc-etiqueta', '¿Entre cuántas personas?');
        etiqueta.htmlFor = 'dvc-partes';
        const campo = nodo('input', 'field');
        campo.id = 'dvc-partes';
        campo.type = 'number';
        campo.inputMode = 'numeric';
        campo.min = '2';
        campo.max = '50';
        campo.value = String(partes);
        const ayuda = nodo('p', 'dvc-ayuda');
        const pintar = () => {
            const n = Math.max(2, Math.min(50, parseInt(campo.value, 10) || 2));
            partes = n;
            ayuda.textContent = `Cada persona paga ${dinero(cuenta.total / n)}. La última parte ajusta los centavos.`;
            actualizarBoton();
        };
        campo.addEventListener('input', pintar);
        caja.append(etiqueta, campo, ayuda);
        pintar();
        return caja;
    }

    function seccionMonto() {
        const caja = nodo('div', 'dvc-monto');
        const etiqueta = nodo('label', 'dvc-etiqueta', `Monto (saldo ${dinero(cuenta.saldo)})`);
        etiqueta.htmlFor = 'dvc-monto';
        const campo = nodo('input', 'field');
        campo.id = 'dvc-monto';
        campo.type = 'text';
        campo.inputMode = 'decimal';
        campo.value = cuenta.saldo.toFixed(2);
        campo.addEventListener('input', actualizarBoton);
        caja.append(etiqueta, campo);
        return caja;
    }

    function render() {
        dialogo.replaceChildren();
        const cab = nodo('div', 'dvc-cab');
        const h = nodo('h2', '', `Dividir cuenta · ${cuenta.etiqueta || ''}`);
        h.id = 'dvc-titulo';
        const x = nodo('button', 'dvc-cerrar flat', '✕');
        x.type = 'button';
        x.setAttribute('aria-label', 'Cerrar');
        x.addEventListener('click', cerrar);
        cab.append(h, x);

        const totales = nodo('dl', 'dvc-totales');
        [['Total', cuenta.total], ['Pagado', cuenta.abonado], ['Falta', cuenta.saldo]].forEach(([t, v]) => {
            const d = nodo('div');
            d.append(nodo('dt', '', t), nodo('dd', '', dinero(v)));
            totales.append(d);
        });

        const cuerpo = nodo('div', 'dvc-cuerpo');
        cuerpo.append(totales);

        if (cuenta.pagos.length) {
            const hist = nodo('ul', 'dvc-pagos');
            cuenta.pagos.forEach(p => {
                const li = nodo('li');
                li.append(nodo('strong', '', `${dinero(p.monto)} · ${p.metodoPago}`),
                    nodo('span', 'dvc-ayuda', [p.persona, p.detalle].filter(Boolean).join(' · ')));
                hist.append(li);
            });
            cuerpo.append(nodo('h3', 'dvc-subtitulo', 'Pagos registrados'), hist);
        }

        if (!cuenta.pagado && cuenta.saldo > 0) {
            const segmento = nodo('div', 'dvc-segmento');
            segmento.setAttribute('role', 'group');
            segmento.setAttribute('aria-label', 'Cómo dividir');
            MODOS.forEach(([valor, texto]) => {
                const b = nodo('button', 'flat', texto);
                b.type = 'button';
                b.setAttribute('aria-pressed', String(modo === valor));
                b.addEventListener('click', () => { modo = valor; render(); });
                segmento.append(b);
            });
            cuerpo.append(nodo('h3', 'dvc-subtitulo', 'Nuevo pago'), segmento);
            cuerpo.append(modo === 'PRODUCTOS' ? seccionProductos() : modo === 'PARTES' ? seccionPartes() : seccionMonto());

            const persona = nodo('input', 'field');
            persona.id = 'dvc-persona';
            persona.maxLength = 60;
            persona.placeholder = 'Ej.: Ana';
            const etPersona = nodo('label', 'dvc-etiqueta', '¿Quién paga? (opcional)');
            etPersona.htmlFor = 'dvc-persona';
            const metodo = nodo('select', 'field');
            metodo.id = 'dvc-metodo';
            METODOS.forEach(([v, t]) => { const o = nodo('option', '', t); o.value = v; metodo.append(o); });
            const etMetodo = nodo('label', 'dvc-etiqueta', 'Método de pago');
            etMetodo.htmlFor = 'dvc-metodo';
            const fila = nodo('div', 'dvc-fila');
            const c1 = nodo('div');
            c1.append(etPersona, persona);
            const c2 = nodo('div');
            c2.append(etMetodo, metodo);
            fila.append(c1, c2);
            cuerpo.append(fila);
        }

        const pie = nodo('div', 'dvc-pie');
        if (!cuenta.pagado && cuenta.saldo > 0) {
            const cobrar = nodo('button', 'button-primary', 'Cobrar esta parte');
            cobrar.id = 'dvc-cobrar';
            cobrar.type = 'button';
            cobrar.addEventListener('click', () => pagar(cobrar));
            pie.append(cobrar);
        } else {
            pie.append(nodo('p', 'dvc-listo', '✅ La cuenta está pagada por completo.'));
        }
        dialogo.append(cab, cuerpo, pie);
        actualizarBoton();
    }

    async function cargar(id) {
        const r = await fetch(`/api/pedidos/${id}/cuenta`, { headers: { Accept: 'application/json' } });
        if (!r.ok) throw await apiError(r);
        cuenta = await r.json();
    }

    async function pagar(boton) {
        const monto = montoActual();
        if (!(monto > 0)) return;
        const cuerpo = { modo, metodoPago: dialogo.querySelector('#dvc-metodo').value, persona: dialogo.querySelector('#dvc-persona').value.trim() || null };
        if (modo === 'PRODUCTOS') cuerpo.items = [...elegidas.entries()].filter(([, c]) => c > 0).map(([detalleId, cantidad]) => ({ detalleId, cantidad }));
        if (modo === 'PARTES') cuerpo.partes = partes;
        if (modo === 'MONTO') cuerpo.monto = monto;
        const ok = await confirmAction(null, {
            titulo: '¿Registrar este pago?',
            resumen: [
                { etiqueta: 'Cuenta', valor: cuenta.etiqueta || `#${cuenta.pedidoId}` },
                { etiqueta: 'Método', valor: cuerpo.metodoPago },
                ...(cuerpo.persona ? [{ etiqueta: 'Paga', valor: cuerpo.persona }] : [])
            ],
            total: dinero(monto),
            aceptar: 'Cobrar'
        });
        if (!ok) return;
        boton.disabled = true;
        try {
            const r = await secureFetch(`/api/pedidos/${cuenta.pedidoId}/pagos`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(cuerpo)
            });
            if (!r.ok) throw await apiError(r);
            cuenta = await r.json();
            elegidas = new Map();
            showNotice(cuenta.pagado ? 'Cuenta pagada por completo' : `Pago registrado. Falta ${dinero(cuenta.saldo)}`, 'ok');
            render();
        } catch (e) {
            boton.disabled = false;
            showNotice(e.message || 'No se pudo registrar el pago');
        }
    }

    window.DivisionCuenta = {
        async abrir(pedidoId, despues) {
            if (!dialogo) montar();
            alTerminar = despues || null;
            modo = 'PRODUCTOS';
            elegidas = new Map();
            partes = 2;
            try {
                await cargar(pedidoId);
            } catch (e) {
                showNotice(e.message || 'No se pudo abrir la cuenta');
                return;
            }
            render();
            if (typeof dialogo.showModal === 'function') dialogo.showModal(); else dialogo.setAttribute('open', '');
        }
    };
})();
