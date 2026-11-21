(function () {
    'use strict';
    const ESPERA_AGRUPAR_MS = 700;
    const MODOS = {
        LIMITADA: { texto: 'Cantidad limitada', corto: 'Contada' },
        MANUAL: { texto: 'Disponibilidad manual', corto: 'Manual' },
        CONTINUA: { texto: 'Disponibilidad continua', corto: 'Continua' }
    };
    const ESTADOS = {
        DISPONIBLE: { texto: 'Disponible', clase: 'ok' },
        POCAS: { texto: 'Por agotarse', clase: 'pocas' },
        AGOTADO: { texto: 'Agotado', clase: 'agotado' },
        PAUSADO: { texto: 'Pausado', clase: 'pausado' }
    };
    const MOTIVOS_PAUSA = ['Se acabó un insumo', 'Problema en cocina', 'Mucha demanda', 'Falta personal'];
    const MOTIVOS_CANTIDAD = ['Se preparó más', 'Conteo físico', 'Merma o desperdicio', 'Corrección'];

    function el(tag, clase, texto) {
        const n = document.createElement(tag);
        if (clase) n.className = clase;
        if (texto !== undefined && texto !== null) n.textContent = String(texto);
        return n;
    }

    function ico(nombre) {
        return window.Icono ? Icono.crear(nombre) : document.createTextNode('');
    }

    function boton(clase, texto, icono, etiqueta) {
        const b = el('button', `dsp-btn ${clase || ''} flat`.trim());
        b.type = 'button';
        if (icono) b.append(ico(icono));
        if (texto) b.append(el('span', null, texto));
        if (etiqueta) b.setAttribute('aria-label', etiqueta);
        return b;
    }

    async function api(url, opciones) {
        const r = await secureFetch(url, Object.assign({ headers: { 'Content-Type': 'application/json', Accept: 'application/json' } }, opciones || {}));
        if (!r.ok) throw await apiError(r);
        return r.json();
    }

    function hora(iso) {
        const f = new Date(iso);
        return Number.isNaN(f.getTime()) ? '' : f.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' });
    }

    function montar(contenedor, opciones) {
        const op = Object.assign({}, opciones || {});
        let productos = [];
        let categorias = new Map();
        let filtro = 'todos';
        let busqueda = '';
        let vista = 'productos';
        let recarga = 0;
        const pendientes = new Map();

        const raiz = el('section', 'dsp');
        raiz.setAttribute('aria-label', 'Disponibilidad de productos');
        const barra = el('div', 'dsp-barra');
        const buscar = el('input', 'field dsp-buscar');
        buscar.type = 'search';
        buscar.placeholder = 'Buscar producto';
        buscar.setAttribute('aria-label', 'Buscar producto');
        const filtros = el('div', 'dsp-filtros');
        filtros.setAttribute('role', 'group');
        filtros.setAttribute('aria-label', 'Filtrar');
        [['todos', 'Todos'], ['pocas', 'Por agotarse'], ['parados', 'Agotados y pausados']].forEach(([clave, texto]) => {
            const b = el('button', 'dsp-filtro flat', texto);
            b.type = 'button';
            b.dataset.filtro = clave;
            b.setAttribute('aria-pressed', String(clave === filtro));
            filtros.append(b);
        });
        const historialBtn = boton('dsp-historial-btn', 'Historial de hoy', 'history');
        historialBtn.setAttribute('aria-pressed', 'false');
        barra.append(buscar, filtros, historialBtn);
        const resumen = el('p', 'dsp-resumen');
        resumen.setAttribute('aria-live', 'polite');
        const lista = el('div', 'dsp-lista');
        const historial = el('div', 'dsp-historial');
        historial.hidden = true;
        raiz.append(barra, resumen, lista, historial);
        contenedor.replaceChildren(raiz);

        filtros.addEventListener('click', e => {
            const b = e.target.closest('[data-filtro]');
            if (!b) return;
            filtro = b.dataset.filtro;
            filtros.querySelectorAll('[data-filtro]').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
            pintar();
        });
        buscar.addEventListener('input', () => { busqueda = buscar.value.trim().toLowerCase(); pintar(); });
        historialBtn.addEventListener('click', () => {
            vista = vista === 'productos' ? 'historial' : 'productos';
            historialBtn.setAttribute('aria-pressed', String(vista === 'historial'));
            lista.hidden = vista !== 'productos';
            filtros.hidden = vista !== 'productos';
            buscar.hidden = vista !== 'productos';
            historial.hidden = vista !== 'historial';
            if (vista === 'historial') cargarHistorial();
        });

        function visible(p) {
            if (busqueda && !p.nombre.toLowerCase().includes(busqueda)) return false;
            if (filtro === 'pocas') return p.estado === 'POCAS';
            if (filtro === 'parados') return p.estado === 'AGOTADO' || p.estado === 'PAUSADO';
            return true;
        }

        function pintar() {
            const agotados = productos.filter(p => p.estado === 'AGOTADO' || p.estado === 'PAUSADO').length;
            const pocas = productos.filter(p => p.estado === 'POCAS').length;
            resumen.textContent = productos.length
                ? `${productos.length} en el menú de hoy · ${pocas} por agotarse · ${agotados} agotados o pausados`
                : '';
            const grupos = new Map();
            productos.filter(visible).forEach(p => {
                const clave = p.categoria || '';
                if (!grupos.has(clave)) grupos.set(clave, []);
                grupos.get(clave).push(p);
            });
            if (!productos.length) {
                lista.replaceChildren(vacio('No hay productos en el menú de hoy', 'Publica el menú del día para controlar su disponibilidad.'));
                return;
            }
            if (!grupos.size) {
                lista.replaceChildren(vacio('Nada coincide', 'Prueba con otro filtro o búsqueda.'));
                return;
            }
            const orden = [...grupos.keys()].sort((a, b) => ordenCategoria(a) - ordenCategoria(b));
            lista.replaceChildren(...orden.map(clave => {
                const sec = el('section', 'dsp-grupo');
                const cat = categorias.get(clave);
                const h = el('h3', 'dsp-grupo-titulo');
                if (cat && cat.icono) h.append(el('span', 'dsp-grupo-icono', cat.icono));
                h.append(el('span', null, cat ? cat.nombre : 'Sin categoría'));
                sec.append(h, ...grupos.get(clave).map(fila));
                return sec;
            }));
        }

        function ordenCategoria(codigo) {
            const c = categorias.get(codigo);
            return c && Number.isFinite(Number(c.orden)) ? Number(c.orden) : 9999;
        }

        function vacio(titulo, texto) {
            const caja = el('div', 'dsp-vacio');
            const i = el('span', 'dsp-vacio-ico');
            i.append(ico('clipboard'));
            caja.append(i, el('p', 'dsp-vacio-titulo', titulo), el('p', 'dsp-vacio-texto', texto));
            return caja;
        }

        function fila(p) {
            const est = ESTADOS[p.estado] || ESTADOS.DISPONIBLE;
            const card = el('article', `dsp-item dsp-${est.clase}`);
            card.dataset.id = p.id;
            const cab = el('div', 'dsp-item-cab');
            const nombre = el('div', 'dsp-item-nombre');
            nombre.append(el('strong', null, p.nombre));
            const etiquetas = el('div', 'dsp-item-etiquetas');
            etiquetas.append(el('span', `dsp-estado dsp-estado-${est.clase}`, est.texto));
            const modo = el('select', 'dsp-modo');
            modo.setAttribute('aria-label', `Modalidad de ${p.nombre}`);
            Object.entries(MODOS).forEach(([valor, m]) => {
                const o = el('option', null, m.corto);
                o.value = valor;
                o.selected = valor === p.tipo;
                modo.append(o);
            });
            modo.addEventListener('change', () => cambiarModo(p, modo));
            etiquetas.append(modo);
            nombre.append(etiquetas);
            cab.append(nombre);

            const acciones = el('div', 'dsp-item-acciones');
            if (p.tipo === 'LIMITADA') {
                acciones.append(contador(p, null, p.stock));
            } else if (p.tipo === 'MANUAL') {
                const hay = boton(`dsp-toggle ${p.pausado ? '' : 'activo'}`, 'Hay', 'check', `${p.nombre}: hay`);
                const no = boton(`dsp-toggle dsp-toggle-no ${p.pausado ? 'activo' : ''}`, 'Agotado', 'x', `${p.nombre}: agotado`);
                hay.setAttribute('aria-pressed', String(!p.pausado));
                no.setAttribute('aria-pressed', String(!!p.pausado));
                hay.addEventListener('click', () => { if (p.pausado) pausar(p, false, null); });
                no.addEventListener('click', () => { if (!p.pausado) pausar(p, true, null); });
                const grupo = el('div', 'dsp-segmento');
                grupo.setAttribute('role', 'group');
                grupo.append(hay, no);
                acciones.append(grupo);
            } else {
                acciones.append(el('span', 'dsp-continua', 'Siempre disponible'));
            }
            if (p.tipo !== 'MANUAL') {
                const pausa = p.pausado
                    ? boton('dsp-reactivar', 'Reactivar', 'refresh', `Reactivar ${p.nombre}`)
                    : boton('dsp-pausar', 'Pausar', 'lock', `Pausar ${p.nombre}`);
                pausa.addEventListener('click', () => pausar(p, !p.pausado));
                acciones.append(pausa);
            }
            card.append(cab, acciones);

            const conStock = (p.presentaciones || []).filter(pr => pr.stock !== null && pr.stock !== undefined);
            if (conStock.length) {
                const sub = el('div', 'dsp-tamanos');
                conStock.forEach(pr => {
                    const f = el('div', 'dsp-tamano');
                    f.append(el('span', 'dsp-tamano-nombre', pr.nombre), contador(p, pr, pr.stock));
                    sub.append(f);
                });
                card.append(sub);
            }
            return card;
        }

        function contador(p, pres, valor) {
            const caja = el('div', 'dsp-contador');
            const nombre = pres ? `${p.nombre} – ${pres.nombre}` : p.nombre;
            caja.setAttribute('role', 'group');
            caja.setAttribute('aria-label', `Cantidad de ${nombre}`);
            const menos = boton('dsp-paso', null, 'minus', `Uno menos de ${nombre}`);
            const num = el('button', 'dsp-num flat', valor);
            num.type = 'button';
            num.setAttribute('aria-label', `${valor} unidades de ${nombre}. Toca para fijar la cantidad exacta`);
            const mas = boton('dsp-paso', null, 'plus', `Uno más de ${nombre}`);
            const cinco = boton('dsp-paso dsp-cinco', '+5', null, `Cinco más de ${nombre}`);
            menos.disabled = valor <= 0;
            menos.addEventListener('click', () => sumar(p, pres, -1, num, menos));
            mas.addEventListener('click', () => sumar(p, pres, 1, num, menos));
            cinco.addEventListener('click', () => sumar(p, pres, 5, num, menos));
            num.addEventListener('click', () => fijar(p, pres, Number(num.textContent)));
            caja.append(menos, num, mas, cinco);
            return caja;
        }

        function sumar(p, pres, delta, num, menos) {
            const actual = Number(num.textContent);
            const nuevo = Math.max(0, actual + delta);
            if (nuevo === actual) return;
            num.textContent = String(nuevo);
            menos.disabled = nuevo <= 0;
            num.closest('.dsp-item')?.classList.add('dsp-guardando');
            const clave = pres ? `pr-${pres.id}` : `p-${p.id}`;
            const previo = pendientes.get(clave) || { delta: 0, base: actual };
            previo.delta += nuevo - actual;
            clearTimeout(previo.timer);
            previo.timer = setTimeout(() => enviarDelta(p, pres, clave), ESPERA_AGRUPAR_MS);
            pendientes.set(clave, previo);
        }

        async function enviarDelta(p, pres, clave) {
            const pendiente = pendientes.get(clave);
            pendientes.delete(clave);
            if (!pendiente || !pendiente.delta) return;
            const url = pres ? `/api/disponibilidad/presentaciones/${pres.id}/cantidad` : `/api/disponibilidad/platos/${p.id}/cantidad`;
            try {
                reemplazar(await api(url, { method: 'PUT', body: JSON.stringify({ delta: pendiente.delta, motivo: 'Ajuste rápido' }) }));
            } catch (e) {
                showNotice(e.message);
                cargar();
            }
        }

        async function fijar(p, pres, actual) {
            const nombre = pres ? `${p.nombre} – ${pres.nombre}` : p.nombre;
            const valor = await pedirCantidad(`¿Cuántas unidades de ${nombre} hay?`, actual);
            if (valor === null) return;
            const motivo = await pedirMotivo({ titulo: 'Motivo del cambio', texto: `${nombre}: ${actual} → ${valor}`, sugerencias: MOTIVOS_CANTIDAD, aceptar: 'Guardar' });
            if (motivo === null) return;
            const url = pres ? `/api/disponibilidad/presentaciones/${pres.id}/cantidad` : `/api/disponibilidad/platos/${p.id}/cantidad`;
            try {
                reemplazar(await api(url, { method: 'PUT', body: JSON.stringify({ cantidad: valor, motivo }) }));
                showNotice(`${nombre}: ${valor} disponibles`, 'ok');
            } catch (e) {
                showNotice(e.message);
            }
        }

        function pedirCantidad(titulo, actual) {
            return new Promise(resolve => {
                const d = el('dialog', 'dialog-ui dsp-dialogo');
                d.setAttribute('aria-labelledby', 'dsp-cantidad-titulo');
                const h = el('h2', null, titulo);
                h.id = 'dsp-cantidad-titulo';
                const campo = el('input', 'field dsp-cantidad-campo');
                campo.type = 'number';
                campo.inputMode = 'numeric';
                campo.min = '0';
                campo.max = '9999';
                campo.value = String(actual ?? 0);
                campo.setAttribute('aria-label', 'Cantidad');
                const err = el('p', 'dialog-motivo-error', 'Escribe un número entre 0 y 9999.');
                err.hidden = true;
                const acc = el('div', 'modal-ui-acciones');
                const no = el('button', 'button-secondary', 'Cancelar');
                no.type = 'button';
                const si = el('button', 'button-primary', 'Siguiente');
                si.type = 'button';
                acc.append(no, si);
                d.append(h, campo, err, acc);
                const fin = v => { d.close(); d.remove(); resolve(v); };
                const ok = () => {
                    const n = Number(campo.value);
                    if (campo.value === '' || !Number.isInteger(n) || n < 0 || n > 9999) { err.hidden = false; campo.focus(); return; }
                    fin(n);
                };
                no.addEventListener('click', () => fin(null));
                si.addEventListener('click', ok);
                campo.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); ok(); } });
                d.addEventListener('cancel', e => { e.preventDefault(); fin(null); });
                document.body.append(d);
                d.showModal();
                campo.select();
            });
        }

        async function pausar(p, pausado, motivoFijo) {
            let motivo = motivoFijo;
            if (motivo === undefined) {
                motivo = await pedirMotivo(pausado
                    ? { titulo: `¿Pausar ${p.nombre}?`, texto: 'Nadie podrá pedirlo hasta que lo reactives. Se verá «Agotado» en la carta.', sugerencias: MOTIVOS_PAUSA, aceptar: 'Pausar' }
                    : { titulo: `¿Reactivar ${p.nombre}?`, texto: 'Volverá a aparecer disponible para meseros y clientes.', sugerencias: ['Ya hay insumos', 'Se resolvió el problema'], aceptar: 'Reactivar' });
                if (motivo === null) return;
            }
            try {
                reemplazar(await api(`/api/disponibilidad/platos/${p.id}/pausa`, { method: 'PUT', body: JSON.stringify({ pausado, motivo }) }));
            } catch (e) {
                showNotice(e.message);
            }
        }

        async function cambiarModo(p, select) {
            const tipo = select.value;
            let cantidad = null;
            if (tipo === 'LIMITADA') {
                cantidad = await pedirCantidad(`¿Cuántas unidades de ${p.nombre} hay?`, p.stock ?? 10);
                if (cantidad === null) { select.value = p.tipo; return; }
            }
            const motivo = await pedirMotivo({
                titulo: `Cambiar a «${MODOS[tipo].texto}»`,
                texto: tipo === 'LIMITADA' ? 'Se descontará una unidad con cada pedido confirmado y se agotará al llegar a cero.'
                    : tipo === 'MANUAL' ? 'Sin conteo: tú marcas cuándo hay y cuándo se agotó.'
                    : 'Siempre disponible mientras esté en el menú de hoy.',
                aceptar: 'Cambiar'
            });
            if (motivo === null) { select.value = p.tipo; return; }
            try {
                reemplazar(await api(`/api/disponibilidad/platos/${p.id}/modalidad`, { method: 'PUT', body: JSON.stringify({ tipo, cantidad, motivo }) }));
            } catch (e) {
                select.value = p.tipo;
                showNotice(e.message);
            }
        }

        function reemplazar(nuevo) {
            const i = productos.findIndex(x => x.id === nuevo.id);
            if (i >= 0) productos[i] = nuevo;
            pintar();
        }

        async function cargarHistorial() {
            historial.replaceChildren(el('p', 'dsp-cargando', 'Cargando…'));
            try {
                const movs = await api('/api/disponibilidad/movimientos');
                if (!movs.length) {
                    historial.replaceChildren(vacio('Sin cambios hoy', 'Aquí verás quién cambió cantidades, pausó o reactivó productos.'));
                    return;
                }
                const ul = el('ol', 'dsp-movs');
                const TIPOS = { AJUSTE: 'Cantidad', PAUSA: 'Pausado', REACTIVACION: 'Reactivado', MODALIDAD: 'Modalidad', ANULACION: 'Pedido anulado' };
                movs.forEach(m => {
                    const li = el('li', `dsp-mov dsp-mov-${m.tipo.toLowerCase()}`);
                    const cab = el('div', 'dsp-mov-cab');
                    cab.append(el('strong', null, m.producto), el('time', 'dsp-mov-hora', hora(m.fecha)));
                    const det = el('div', 'dsp-mov-det');
                    det.append(el('span', 'dsp-mov-tipo', TIPOS[m.tipo] || m.tipo));
                    if (m.cantidadAntes !== m.cantidadDespues)
                        det.append(el('span', 'dsp-mov-cant', `${m.cantidadAntes ?? '—'} → ${m.cantidadDespues ?? '—'}`));
                    det.append(el('span', 'dsp-mov-quien', m.usuario || '—'));
                    li.append(cab, det);
                    if (m.motivo) li.append(el('p', 'dsp-mov-motivo', m.motivo));
                    ul.append(li);
                });
                historial.replaceChildren(ul);
            } catch (e) {
                historial.replaceChildren(vacio('No se pudo cargar el historial', e.message));
            }
        }

        async function cargar() {
            try {
                const [lista, cats] = await Promise.all([api('/api/disponibilidad'), categorias.size ? null : api('/api/categorias').catch(() => [])]);
                if (cats) categorias = new Map(cats.map(c => [c.codigo, c]));
                productos = lista;
                pintar();
                if (vista === 'historial') cargarHistorial();
            } catch (e) {
                lista.replaceChildren(vacio('No se pudo cargar la disponibilidad', e.message));
            }
        }

        function programarRecarga() {
            if (pendientes.size) return;
            clearTimeout(recarga);
            recarga = setTimeout(cargar, 400);
        }

        function conectar() {
            if (!window.SockJS || !window.Stomp || op.sinTiempoReal) return;
            const stomp = Stomp.over(new SockJS('/ws'));
            stomp.debug = null;
            stomp.connect({}, () => {
                stomp.subscribe('/topic/stock', programarRecarga);
                stomp.subscribe('/topic/menu', programarRecarga);
            }, () => setTimeout(conectar, 5000));
        }

        cargar();
        conectar();
        return { recargar: cargar };
    }

    window.Disponibilidad = { montar };
})();
