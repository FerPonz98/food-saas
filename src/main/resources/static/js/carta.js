(function () {
    'use strict';
    const N = window.NEGOCIO || {};
    const datos = document.body.dataset;
    const mesa = datos.mesa ? Number(datos.mesa) : null;
    const slug = datos.slug || '';
    const API = `/api/publico/${encodeURIComponent(slug)}`;
    const CON_LLAMADOS = datos.llamados === 'true';
    const CON_PEDIDOS = datos.pedidos === 'true';
    const contenido = document.getElementById('carta');
    const avisoEl = document.getElementById('carta-aviso');
    const estadoEl = document.getElementById('carta-estado');
    const ROL = (N.etiquetaRolMesero || 'mesero').toLowerCase();
    const MONEDA = N.simboloMoneda || 'Bs.';
    const PAGOS = { EFECTIVO: 'efectivo', TARJETA: 'tarjeta', QR: 'pago QR' };
    const CLAVE_PEDIDOS = `carta-pedidos:${slug}:${mesa}`;
    const CLAVE_CARRITO = `carta-carrito:${slug}:${mesa}`;
    const VIGENCIA_PEDIDO_MS = 3 * 60 * 60 * 1000;
    let vista = null;
    let cargando = null;
    let ultimaCarga = 0;
    let platosPorId = new Map();
    let llamados = [];
    let pedidos = [];
    let carrito = [];
    let sondeo = 0;
    let avisoTimer = 0;

    async function pedir(url, opciones) {
        const r = await fetch(url, Object.assign({ credentials: 'same-origin', headers: { Accept: 'application/json' } }, opciones || {}));
        if (!r.ok) {
            let mensaje = 'No se pudo completar. Intenta de nuevo.';
            try {
                const d = await r.json();
                if (d && d.mensaje) mensaje = d.mensaje;
            } catch (e) {
                mensaje = r.status === 404 ? 'No encontramos lo que buscabas.' : mensaje;
            }
            const error = new Error(mensaje);
            error.status = r.status;
            throw error;
        }
        return r.json();
    }

    function leer(clave, defecto) {
        try {
            const valor = JSON.parse(localStorage.getItem(clave));
            return valor == null ? defecto : valor;
        } catch (e) {
            return defecto;
        }
    }

    function guardar(clave, valor) {
        try { localStorage.setItem(clave, JSON.stringify(valor)); } catch (e) { return; }
    }

    function nodo(tag, clase, texto) {
        const n = document.createElement(tag);
        if (clase) n.className = clase;
        if (texto !== undefined) n.textContent = texto;
        return n;
    }

    function dinero(v) {
        return `${MONEDA} ${Number(v || 0).toFixed(2)}`;
    }

    function avisar(texto, tipo) {
        clearTimeout(avisoTimer);
        avisoEl.textContent = texto;
        avisoEl.className = `carta-aviso carta-aviso-${tipo || 'ok'}`;
        avisoEl.hidden = false;
        avisoTimer = setTimeout(() => { avisoEl.hidden = true; }, 5000);
    }

    function mostrarError() {
        const caja = nodo('div', 'ctl-estado-pagina');
        caja.setAttribute('role', 'alert');
        const icono = nodo('span', 'ctl-estado-pagina-icono', '!');
        icono.setAttribute('aria-hidden', 'true');
        const boton = nodo('button', 'button-primary', 'Reintentar');
        boton.type = 'button';
        boton.addEventListener('click', () => cargar(true));
        caja.append(icono, nodo('h2', '', 'No pudimos cargar la carta'),
            nodo('p', '', navigator.onLine === false ? 'Parece que no hay conexión a internet.' : 'Intenta de nuevo en unos segundos.'), boton);
        contenido.replaceChildren(caja);
    }

    function mostrarVacia() {
        const caja = nodo('div', 'ctl-estado-pagina carta-vacia');
        const icono = nodo('span', 'ctl-estado-pagina-icono', '🍽️');
        icono.setAttribute('aria-hidden', 'true');
        caja.append(icono, nodo('h2', '', 'La carta de hoy todavía no está lista'),
            nodo('p', '', `Pregunta al ${ROL} qué hay hoy.`));
        contenido.replaceChildren(caja);
    }

    function cargar(conEsqueleto) {
        if (cargando) return cargando;
        if (conEsqueleto && !vista && window.Catalogo) Catalogo.esqueleto(contenido);
        cargando = pedir(`${API}/carta`)
            .then(d => {
                ultimaCarga = Date.now();
                platosPorId = new Map(d.platos.map(p => [String(p.id), p]));
                if (!d.platos.length) {
                    if (vista) { vista.destruir(); vista = null; }
                    mostrarVacia();
                    return;
                }
                if (vista) vista.actualizar(d);
                else {
                    contenido.replaceChildren();
                    const opciones = { platos: d.platos, categorias: d.categorias, modo: 'pagina' };
                    if (CON_PEDIDOS) opciones.alElegir = plato => { if (window.CartaPedidos) window.CartaPedidos.abrirAgregar(plato); };
                    vista = Catalogo.montar(contenido, opciones);
                }
            })
            .catch(() => { if (!vista) mostrarError(); })
            .finally(() => { cargando = null; });
        return cargando;
    }

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState !== 'visible') return;
        if (Date.now() - ultimaCarga > 30000) cargar(false);
        if (mesa) revisarEstado();
    });
    window.addEventListener('online', () => cargar(!vista));
    setInterval(() => { if (document.visibilityState === 'visible') cargar(false); }, 90000);
    cargar(true);

    if (!mesa) return;

    function abrir(id) {
        const d = document.getElementById(id);
        if (!d) return null;
        if (typeof d.showModal === 'function') d.showModal(); else d.setAttribute('open', '');
        return d;
    }
    function cerrar(d) {
        if (!d) return;
        if (typeof d.close === 'function') d.close(); else d.removeAttribute('open');
    }
    document.querySelectorAll('.carta-dialogo').forEach(d => {
        d.querySelectorAll('[data-cerrar]').forEach(b => b.addEventListener('click', () => cerrar(d)));
        d.addEventListener('click', e => { if (e.target === d) cerrar(d); });
    });

    function hace(fecha) {
        const min = Math.max(0, Math.round((Date.now() - new Date(fecha).getTime()) / 60000));
        return min < 1 ? 'recién' : `hace ${min} min`;
    }

    function filaEstado(texto, detalle, clase, alCerrar) {
        const fila = nodo('div', `carta-estado-fila ${clase || ''}`);
        fila.append(nodo('span', 'carta-estado-texto', texto), nodo('span', 'carta-estado-hora', detalle));
        if (alCerrar) {
            const x = nodo('button', 'carta-estado-cerrar flat', '✕');
            x.type = 'button';
            x.setAttribute('aria-label', 'Quitar este aviso');
            x.addEventListener('click', alCerrar);
            fila.append(x);
        }
        return fila;
    }

    function pintarEstado() {
        const filas = [];
        pedidos.forEach(p => {
            if (!p.estado) return;
            if (p.estado === 'PENDIENTE')
                filas.push(filaEstado(`📝 Tu pedido (${dinero(p.total)})`, `Esperando que el ${ROL} lo confirme · ${hace(p.creadoEn)}`, 'carta-estado-espera'));
            else if (p.estado === 'APROBADA')
                filas.push(filaEstado('✅ Pedido confirmado', 'Ya está en la cocina', 'carta-estado-ok', () => olvidarPedido(p.token)));
            else
                filas.push(filaEstado('❌ No se pudo tomar tu pedido', p.motivoRechazo || `Consulta al ${ROL}`, 'carta-estado-error', () => olvidarPedido(p.token)));
        });
        llamados.forEach(l => {
            const texto = l.motivo === 'CUENTA'
                ? `🧾 Pediste la cuenta${l.metodoPago ? ` (${PAGOS[l.metodoPago] || l.metodoPago})` : ''}`
                : `🔔 Llamaste al ${ROL}`;
            filas.push(filaEstado(texto, l.confirmado ? `Ya viene · ${hace(l.desde)}` : `Avisamos al ${ROL} · ${hace(l.desde)}`,
                l.confirmado ? 'carta-estado-ok' : 'carta-estado-espera'));
        });
        estadoEl.replaceChildren(...filas);
        estadoEl.hidden = !filas.length;
    }

    function hayPendientes() {
        return llamados.length > 0 || pedidos.some(p => p.estado === 'PENDIENTE');
    }

    function programarSondeo() {
        clearTimeout(sondeo);
        if (hayPendientes()) sondeo = setTimeout(revisarEstado, 8000);
    }

    async function revisarEstado() {
        if (CON_LLAMADOS) {
            try {
                llamados = await pedir(`${API}/mesa/${mesa}/llamados`);
            } catch (e) {
                llamados = llamados || [];
            }
        }
        if (CON_PEDIDOS) {
            const guardados = leer(CLAVE_PEDIDOS, []).filter(p => Date.now() - p.creado < VIGENCIA_PEDIDO_MS);
            const actualizados = [];
            for (const g of guardados) {
                try {
                    actualizados.push(Object.assign({ creado: g.creado }, await pedir(`${API}/mesa/${mesa}/pedidos/${encodeURIComponent(g.token)}`)));
                } catch (e) {
                    if (e.status !== 404) actualizados.push(Object.assign({ creado: g.creado }, pedidos.find(p => p.token === g.token) || g));
                }
            }
            pedidos = actualizados;
            guardar(CLAVE_PEDIDOS, pedidos.map(p => ({ token: p.token, creado: p.creado })));
        }
        pintarEstado();
        programarSondeo();
    }

    function olvidarPedido(token) {
        pedidos = pedidos.filter(p => p.token !== token);
        guardar(CLAVE_PEDIDOS, pedidos.map(p => ({ token: p.token, creado: p.creado })));
        pintarEstado();
    }

    if (CON_LLAMADOS) {
        const enviar = async (motivo, metodoPago, boton) => {
            if (boton) boton.disabled = true;
            try {
                const r = await pedir(`${API}/mesa/${mesa}/llamados`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: JSON.stringify({ motivo, metodoPago })
                });
                llamados = llamados.filter(p => p.motivo !== r.motivo).concat([r]);
                pintarEstado();
                programarSondeo();
                if (navigator.vibrate) navigator.vibrate(40);
                avisar(motivo === 'CUENTA' ? `Listo, le avisamos al ${ROL} que quieres la cuenta.` : `Listo, le avisamos al ${ROL}.`);
            } catch (e) {
                avisar(e.message, 'error');
            } finally {
                if (boton) boton.disabled = false;
            }
        };
        const dlgLlamar = document.getElementById('dlg-llamar');
        document.getElementById('btn-llamar').addEventListener('click', () => abrir('dlg-llamar'));
        document.getElementById('confirmar-llamar').addEventListener('click', e => {
            cerrar(dlgLlamar);
            enviar('MESERO', null, e.currentTarget);
        });
        const dlgCuenta = document.getElementById('dlg-cuenta');
        document.getElementById('btn-cuenta').addEventListener('click', () => abrir('dlg-cuenta'));
        dlgCuenta.querySelectorAll('[data-pago]').forEach(b => b.addEventListener('click', () => {
            cerrar(dlgCuenta);
            enviar('CUENTA', b.dataset.pago, document.getElementById('btn-cuenta'));
        }));
    }

    if (CON_PEDIDOS) {
        const dlgAgregar = document.getElementById('dlg-agregar');
        const dlgCarrito = document.getElementById('dlg-carrito');
        const btnCarrito = document.getElementById('btn-carrito');
        const opcionesEl = document.getElementById('agregar-opciones');
        const tamanosEl = document.getElementById('agregar-tamanos');
        const cantidadEl = document.getElementById('agregar-cantidad');
        const notaEl = document.getElementById('agregar-nota');
        let elegido = null;
        let cantidad = 1;
        carrito = leer(CLAVE_CARRITO, []);

        const tamanosMesa = plato => (plato.presentaciones || []).filter(p => p.modalidad !== 'LLEVAR' && p.disponible !== false);

        const pintarCarrito = () => {
            const unidades = carrito.reduce((s, i) => s + i.cantidad, 0);
            document.getElementById('carrito-cantidad').textContent = String(unidades);
            btnCarrito.hidden = unidades === 0;
            const lista = document.getElementById('carrito-lista');
            lista.replaceChildren(...carrito.map((i, idx) => {
                const li = nodo('li', 'carta-carrito-item');
                const info = nodo('div', 'carta-carrito-info');
                info.append(nodo('strong', '', `${i.cantidad} × ${i.nombre}`));
                if (i.notas) info.append(nodo('span', 'carta-nota', i.notas));
                const quitar = nodo('button', 'button-secondary carta-carrito-quitar', 'Quitar');
                quitar.type = 'button';
                quitar.setAttribute('aria-label', `Quitar ${i.nombre}`);
                quitar.addEventListener('click', () => {
                    carrito.splice(idx, 1);
                    guardar(CLAVE_CARRITO, carrito);
                    pintarCarrito();
                    if (!carrito.length) cerrar(dlgCarrito);
                });
                li.append(info, nodo('span', 'carta-carrito-precio', dinero(i.precio * i.cantidad)), quitar);
                return li;
            }));
            document.getElementById('carrito-total').textContent = dinero(carrito.reduce((s, i) => s + i.precio * i.cantidad, 0));
        };

        const pintarCantidad = () => {
            cantidadEl.textContent = String(cantidad);
            document.getElementById('agregar-menos').disabled = cantidad <= 1;
        };

        function abrirAgregar(plato) {
            elegido = platosPorId.get(String(plato.id)) || plato;
            cantidad = 1;
            notaEl.value = '';
            document.getElementById('dlg-agregar-titulo').textContent = elegido.nombre;
            const tamanos = tamanosMesa(elegido);
            tamanosEl.hidden = !tamanos.length;
            opcionesEl.replaceChildren(...tamanos.map((t, i) => {
                const label = nodo('label', 'carta-tamano');
                const radio = nodo('input');
                radio.type = 'radio';
                radio.name = 'agregar-tamano';
                radio.value = String(t.id);
                radio.checked = i === 0;
                label.append(radio, nodo('span', 'carta-tamano-nombre', t.nombre), nodo('span', 'carta-tamano-precio', dinero(t.precio)));
                return label;
            }));
            pintarCantidad();
            abrir('dlg-agregar');
        }
        window.CartaPedidos = { abrirAgregar };

        document.getElementById('agregar-menos').addEventListener('click', () => { cantidad = Math.max(1, cantidad - 1); pintarCantidad(); });
        document.getElementById('agregar-mas').addEventListener('click', () => { cantidad = Math.min(99, cantidad + 1); pintarCantidad(); });
        document.getElementById('agregar-confirmar').addEventListener('click', () => {
            if (!elegido) return;
            const tamanos = tamanosMesa(elegido);
            let presentacion = null;
            if (tamanos.length) {
                const marcado = opcionesEl.querySelector('input:checked');
                presentacion = tamanos.find(t => String(t.id) === (marcado && marcado.value)) || tamanos[0];
            }
            const notas = notaEl.value.trim();
            const item = {
                platoId: elegido.id,
                presentacionId: presentacion ? presentacion.id : null,
                nombre: presentacion ? `${elegido.nombre} – ${presentacion.nombre}` : elegido.nombre,
                precio: presentacion ? presentacion.precio : elegido.precio,
                cantidad,
                notas
            };
            const igual = carrito.find(i => i.platoId === item.platoId && i.presentacionId === item.presentacionId && i.notas === item.notas);
            if (igual) igual.cantidad = Math.min(99, igual.cantidad + item.cantidad); else carrito.push(item);
            guardar(CLAVE_CARRITO, carrito);
            pintarCarrito();
            cerrar(dlgAgregar);
            avisar(`Agregaste ${item.cantidad} × ${item.nombre}. Revisa «Mi pedido» para enviarlo.`);
        });

        btnCarrito.addEventListener('click', () => { pintarCarrito(); abrir('dlg-carrito'); });
        document.getElementById('carrito-enviar').addEventListener('click', async e => {
            const boton = e.currentTarget;
            if (!carrito.length) return;
            boton.disabled = true;
            try {
                const r = await pedir(`${API}/mesa/${mesa}/pedidos`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: JSON.stringify({
                        nombreCliente: document.getElementById('carrito-nombre').value.trim() || null,
                        nota: document.getElementById('carrito-nota').value.trim() || null,
                        items: carrito.map(i => ({ platoId: i.platoId, presentacionId: i.presentacionId, cantidad: i.cantidad, notas: i.notas || null }))
                    })
                });
                pedidos.push(Object.assign({ creado: Date.now() }, r));
                guardar(CLAVE_PEDIDOS, pedidos.map(p => ({ token: p.token, creado: p.creado })));
                carrito = [];
                guardar(CLAVE_CARRITO, carrito);
                document.getElementById('carrito-nota').value = '';
                pintarCarrito();
                cerrar(dlgCarrito);
                pintarEstado();
                programarSondeo();
                if (navigator.vibrate) navigator.vibrate(40);
                avisar(`Listo, enviamos tu pedido. El ${ROL} lo confirmará en un momento.`);
            } catch (err) {
                avisar(err.message, 'error');
                if (err.status === 400) cargar(false);
            } finally {
                boton.disabled = false;
            }
        });
        pintarCarrito();
    }

    const btnWifi = document.getElementById('btn-wifi');
    if (btnWifi) {
        let wifi = null;
        btnWifi.addEventListener('click', async () => {
            abrir('dlg-wifi');
            if (wifi) return;
            try {
                wifi = await pedir(`${API}/mesa/${mesa}/wifi`);
                document.getElementById('wifi-nombre').textContent = wifi.nombre;
                document.getElementById('wifi-clave').textContent = wifi.clave || '';
                document.getElementById('wifi-clave-fila').hidden = !wifi.clave;
            } catch (e) {
                document.getElementById('wifi-nombre').textContent = 'No disponible ahora';
                document.getElementById('wifi-clave-fila').hidden = true;
            }
        });
        document.getElementById('wifi-copiar').addEventListener('click', async e => {
            const boton = e.currentTarget;
            const clave = wifi && wifi.clave;
            if (!clave) return;
            let copiado = false;
            try {
                await navigator.clipboard.writeText(clave);
                copiado = true;
            } catch (err) {
                const rango = document.createRange();
                rango.selectNodeContents(document.getElementById('wifi-clave'));
                const sel = window.getSelection();
                sel.removeAllRanges();
                sel.addRange(rango);
            }
            boton.textContent = copiado ? '¡Copiada!' : 'Seleccionada';
            setTimeout(() => { boton.textContent = 'Copiar'; }, 2000);
        });
    }

    revisarEstado();
})();
