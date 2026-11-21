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
    const llamadosEl = document.getElementById('carta-llamados');
    const OCULTAR_APROBADO_MS = 60000;
    const vistosAprobados = new Map();
    let relojLlamados = 0;
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

    function avisar(texto, tipo, accion) {
        clearTimeout(avisoTimer);
        avisoEl.replaceChildren(nodo('span', 'carta-aviso-texto', texto));
        if (accion) {
            const b = nodo('button', 'carta-aviso-accion flat', accion.texto);
            b.type = 'button';
            b.addEventListener('click', () => { avisoEl.hidden = true; accion.alHacer(); });
            avisoEl.append(b);
        }
        avisoEl.className = `carta-aviso carta-aviso-${tipo || 'ok'}`;
        avisoEl.hidden = false;
        avisoTimer = setTimeout(() => { avisoEl.hidden = true; }, accion ? 6000 : 5000);
    }

    function icono(nombre) {
        return window.Icono ? Icono.crear(nombre) : document.createTextNode('');
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
        const ic = nodo('span', 'ctl-estado-pagina-icono carta-vacia-icono');
        ic.append(icono('utensils'));
        caja.append(ic, nodo('h2', '', 'La carta de hoy todavía no está lista'),
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
                    if (CON_PEDIDOS) {
                        opciones.alElegir = (plato, extra) => { if (window.CartaPedidos) window.CartaPedidos.abrirAgregar(plato, extra); };
                        opciones.enPedido = id => carrito.filter(i => String(i.platoId) === String(id)).reduce((t, i) => t + i.cantidad, 0);
                    }
                    vista = Catalogo.montar(contenido, opciones);
                }
            })
            .catch(() => { if (!vista) mostrarError(); })
            .finally(() => { cargando = null; });
        return cargando;
    }

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState !== 'visible') return;
        if (Date.now() - ultimaCarga > 15000) cargar(false);
        if (mesa) revisarEstado();
    });
    window.addEventListener('online', () => cargar(!vista));
    setInterval(() => { if (document.visibilityState === 'visible') cargar(false); }, 20000);
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

    function filaEstado(texto, detalle, clase, alCerrar, ico, pasos) {
        const fila = nodo('div', `carta-estado-fila ${clase || ''}`);
        const burbuja = nodo('span', 'carta-estado-ico');
        burbuja.append(icono(ico || 'info'));
        const cuerpo = nodo('div', 'carta-estado-cuerpo');
        cuerpo.append(nodo('span', 'carta-estado-texto', texto), nodo('span', 'carta-estado-hora', detalle));
        if (pasos) cuerpo.append(progreso(pasos));
        fila.append(burbuja, cuerpo);
        if (alCerrar) {
            const x = nodo('button', 'carta-estado-cerrar flat');
            x.append(icono('x'));
            x.type = 'button';
            x.setAttribute('aria-label', 'Quitar este aviso');
            x.addEventListener('click', alCerrar);
            fila.append(x);
        }
        return fila;
    }

    function progreso(hechos) {
        const lista = nodo('ol', 'carta-progreso');
        ['Enviado', `${ROL.charAt(0).toUpperCase()}${ROL.slice(1)} confirma`, 'En cocina'].forEach((t, i) => {
            const li = nodo('li', i < hechos ? 'hecho' : (i === hechos ? 'actual' : ''), t);
            if (i === hechos) li.setAttribute('aria-current', 'step');
            lista.append(li);
        });
        return lista;
    }

    function pintarEstado() {
        const filas = [];
        const ahora = Date.now();
        pedidos.forEach(p => {
            if (p.estado === 'APROBADA' && !vistosAprobados.has(p.token)) {
                vistosAprobados.set(p.token, ahora);
                setTimeout(() => olvidarPedido(p.token), OCULTAR_APROBADO_MS);
            }
        });
        pedidos.forEach(p => {
            if (!p.estado) return;
            if (p.estado === 'PENDIENTE')
                filas.push(filaEstado(`Tu pedido · ${dinero(p.total)}`, `Esperando que el ${ROL} lo confirme · ${hace(p.creadoEn)}`, 'carta-estado-espera', null, 'clock', 1));
            else if (p.estado === 'APROBADA')
                filas.push(filaEstado('Pedido confirmado', 'Ya lo están preparando', 'carta-estado-ok', () => olvidarPedido(p.token), 'chef', 3));
            else
                filas.push(filaEstado('No se pudo tomar tu pedido', p.motivoRechazo || `Consulta al ${ROL}`, 'carta-estado-error', () => olvidarPedido(p.token), 'alert'));
        });
        estadoEl.replaceChildren(...filas);
        estadoEl.hidden = !filas.length;
    }

    function reloj(segundos) {
        const total = Math.max(0, Math.floor(segundos));
        const h = Math.floor(total / 3600);
        const m = Math.floor((total % 3600) / 60);
        const ss = String(total % 60).padStart(2, '0');
        return h ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${String(m).padStart(2, '0')}:${ss}`;
    }

    function textoLlamado(l) {
        const cuenta = l.motivo === 'CUENTA';
        if (l.confirmado) return cuenta ? `El ${ROL} viene con tu cuenta` : `El ${ROL} está en camino`;
        if (cuenta) return `Pediste la cuenta${l.metodoPago ? ` · ${PAGOS[l.metodoPago] || l.metodoPago}` : ''}`;
        return `Esperando al ${ROL}`;
    }

    function actualizarRelojes() {
        llamadosEl.querySelectorAll('[data-desde]').forEach(t => {
            t.textContent = reloj((Date.now() - Number(t.dataset.desde)) / 1000);
        });
    }

    function pintarBotonesLlamado() {
        [['btn-llamar', 'MESERO', `${ROL.charAt(0).toUpperCase()}${ROL.slice(1)} avisado`, `Llamar ${ROL}`],
         ['btn-cuenta', 'CUENTA', 'Cuenta pedida', 'Pedir la cuenta']].forEach(([id, motivo, activo, normal]) => {
            const b = document.getElementById(id);
            if (!b) return;
            const hay = llamados.some(l => l.motivo === motivo);
            b.classList.toggle('carta-accion-activa', hay);
            b.setAttribute('aria-disabled', String(hay));
            const etiqueta = b.querySelector('[data-etiqueta]');
            if (etiqueta) etiqueta.textContent = hay ? activo : normal;
        });
    }

    function pintarLlamados() {
        const previas = new Map([...llamadosEl.querySelectorAll('[data-motivo]')].map(f => [f.dataset.motivo, f.dataset.confirmado]));
        const filas = llamados.map(l => {
            const fila = nodo('div', `carta-llamado${l.confirmado ? ' carta-llamado-ok' : ''}`);
            fila.dataset.motivo = l.motivo;
            fila.dataset.confirmado = String(!!l.confirmado);
            if (previas.has(l.motivo) && previas.get(l.motivo) !== String(!!l.confirmado)) fila.classList.add('carta-llamado-cambio');
            const ico = nodo('span', 'carta-llamado-ico');
            ico.append(icono(l.confirmado ? 'arrow-right' : (l.motivo === 'CUENTA' ? 'receipt' : 'bell')));
            const texto = nodo('span', 'carta-llamado-texto', textoLlamado(l));
            const tiempo = nodo('span', 'carta-llamado-reloj', reloj((Date.now() - l.base) / 1000));
            tiempo.dataset.desde = String(l.base);
            tiempo.setAttribute('aria-hidden', 'true');
            fila.append(ico, texto, tiempo);
            return fila;
        });
        llamadosEl.replaceChildren(...filas);
        llamadosEl.hidden = !filas.length;
        llamadosEl.setAttribute('role', 'status');
        llamadosEl.setAttribute('aria-live', 'polite');
        clearInterval(relojLlamados);
        if (filas.length) relojLlamados = setInterval(actualizarRelojes, 1000);
        pintarBotonesLlamado();
    }

    function conBase(lista) {
        const ahora = Date.now();
        return (lista || []).map(l => Object.assign({}, l, { base: ahora - (Number(l.esperaSegundos) || 0) * 1000 }));
    }

    function hayPendientes() {
        return llamados.length > 0 || pedidos.some(p => p.estado === 'PENDIENTE');
    }

    function programarSondeo() {
        clearTimeout(sondeo);
        if (hayPendientes()) sondeo = setTimeout(revisarEstado, llamados.length ? 5000 : 8000);
    }

    async function revisarEstado() {
        if (CON_LLAMADOS) {
            try {
                llamados = conBase(await pedir(`${API}/mesa/${mesa}/llamados`));
            } catch (e) {
                llamados = llamados || [];
            }
            pintarLlamados();
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
        const activo = motivo => llamados.find(l => l.motivo === motivo);
        const yaAvisado = motivo => {
            const l = activo(motivo);
            if (!l) return false;
            avisar(l.confirmado ? textoLlamado(l) : (motivo === 'CUENTA'
                ? `Ya pediste la cuenta. El ${ROL} vendrá en un momento.`
                : `Ya avisamos al ${ROL}. Te avisaremos cuando venga.`));
            return true;
        };
        const enviar = async (motivo, metodoPago, boton) => {
            if (activo(motivo)) { yaAvisado(motivo); return; }
            if (boton) boton.disabled = true;
            try {
                const r = await pedir(`${API}/mesa/${mesa}/llamados`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: JSON.stringify({ motivo, metodoPago })
                });
                llamados = llamados.filter(p => p.motivo !== r.motivo).concat(conBase([r]));
                pintarLlamados();
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
        document.getElementById('btn-llamar').addEventListener('click', () => { if (!yaAvisado('MESERO')) abrir('dlg-llamar'); });
        document.getElementById('confirmar-llamar').addEventListener('click', e => {
            cerrar(dlgLlamar);
            enviar('MESERO', null, e.currentTarget);
        });
        const dlgCuenta = document.getElementById('dlg-cuenta');
        document.getElementById('btn-cuenta').addEventListener('click', () => { if (!yaAvisado('CUENTA')) abrir('dlg-cuenta'); });
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

        const totalCarrito = () => carrito.reduce((s, i) => s + i.precio * i.cantidad, 0);
        const cambiarCarrito = () => {
            guardar(CLAVE_CARRITO, carrito);
            pintarCarrito();
            if (vista && vista.refrescarPedido) vista.refrescarPedido();
        };
        const pintarCarrito = () => {
            const unidades = carrito.reduce((s, i) => s + i.cantidad, 0);
            document.getElementById('carrito-cantidad').textContent = String(unidades);
            document.getElementById('carrito-boton-total').textContent = dinero(totalCarrito());
            btnCarrito.hidden = unidades === 0;
            document.body.classList.toggle('carta-con-pedido', unidades > 0);
            const lista = document.getElementById('carrito-lista');
            lista.replaceChildren(...carrito.map((i, idx) => {
                const li = nodo('li', 'carta-carrito-item');
                const info = nodo('div', 'carta-carrito-info');
                info.append(nodo('strong', '', i.nombre), nodo('span', 'carta-carrito-unitario', `${dinero(i.precio)} c/u`));
                if (i.notas) info.append(nodo('span', 'carta-nota', `«${i.notas}»`));
                const paso = nodo('div', 'carta-carrito-paso');
                paso.setAttribute('role', 'group');
                paso.setAttribute('aria-label', `Cantidad de ${i.nombre}`);
                const menos = nodo('button', 'carta-paso-btn flat');
                menos.type = 'button';
                menos.append(icono(i.cantidad === 1 ? 'trash' : 'minus'));
                menos.setAttribute('aria-label', i.cantidad === 1 ? `Quitar ${i.nombre}` : `Uno menos de ${i.nombre}`);
                menos.addEventListener('click', () => {
                    if (i.cantidad > 1) i.cantidad -= 1; else carrito.splice(idx, 1);
                    cambiarCarrito();
                    if (!carrito.length) cerrar(dlgCarrito);
                    else lista.querySelectorAll('.carta-carrito-item')[Math.min(idx, carrito.length - 1)]?.querySelector('.carta-paso-btn')?.focus();
                });
                const mas = nodo('button', 'carta-paso-btn flat');
                mas.type = 'button';
                mas.append(icono('plus'));
                mas.setAttribute('aria-label', `Uno más de ${i.nombre}`);
                mas.disabled = i.cantidad >= 99;
                mas.addEventListener('click', () => {
                    i.cantidad = Math.min(99, i.cantidad + 1);
                    cambiarCarrito();
                    lista.querySelectorAll('.carta-carrito-item')[idx]?.querySelectorAll('.carta-paso-btn')[1]?.focus();
                });
                paso.append(menos, nodo('output', 'carta-paso-num', String(i.cantidad)), mas);
                const der = nodo('div', 'carta-carrito-der');
                der.append(nodo('span', 'carta-carrito-precio', dinero(i.precio * i.cantidad)), paso);
                li.append(info, der);
                return li;
            }));
            document.getElementById('carrito-total').textContent = dinero(totalCarrito());
        };

        const animarCarrito = () => {
            btnCarrito.classList.remove('carta-rebote');
            void btnCarrito.offsetWidth;
            btnCarrito.classList.add('carta-rebote');
        };

        const mismoItem = (a, b) => a.platoId === b.platoId && a.presentacionId === b.presentacionId && a.notas === b.notas;
        const agregarItem = item => {
            const igual = carrito.find(i => mismoItem(i, item));
            if (igual) igual.cantidad = Math.min(99, igual.cantidad + item.cantidad); else carrito.push(item);
            cambiarCarrito();
            animarCarrito();
            if (navigator.vibrate) navigator.vibrate(15);
            avisar(`Agregaste ${item.cantidad} × ${item.nombre}`, 'ok', {
                texto: 'Deshacer',
                alHacer: () => {
                    const actual = carrito.find(i => mismoItem(i, item));
                    if (!actual) return;
                    actual.cantidad -= item.cantidad;
                    if (actual.cantidad <= 0) carrito.splice(carrito.indexOf(actual), 1);
                    cambiarCarrito();
                }
            });
        };

        const precioElegido = () => {
            if (!elegido) return 0;
            const tamanos = tamanosMesa(elegido);
            if (!tamanos.length) return Number(elegido.precio) || 0;
            const marcado = opcionesEl.querySelector('input:checked');
            const t = tamanos.find(x => String(x.id) === (marcado && marcado.value)) || tamanos[0];
            return Number(t.precio) || 0;
        };
        const pintarCantidad = () => {
            cantidadEl.textContent = String(cantidad);
            document.getElementById('agregar-menos').disabled = cantidad <= 1;
            document.getElementById('agregar-total').textContent = dinero(precioElegido() * cantidad);
        };
        opcionesEl.addEventListener('change', () => pintarCantidad());

        function abrirAgregar(plato, extra) {
            elegido = platosPorId.get(String(plato.id)) || plato;
            const tamanosRapidos = tamanosMesa(elegido);
            if (extra && extra.rapido && tamanosRapidos.length <= 1) {
                const t = tamanosRapidos[0] || null;
                agregarItem({
                    platoId: elegido.id,
                    presentacionId: t ? t.id : null,
                    nombre: t ? `${elegido.nombre} – ${t.nombre}` : elegido.nombre,
                    precio: t ? t.precio : elegido.precio,
                    cantidad: 1,
                    notas: ''
                });
                return;
            }
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
            cerrar(dlgAgregar);
            agregarItem(item);
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
                document.getElementById('carrito-nota').value = '';
                cambiarCarrito();
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

    const barra = document.querySelector('.carta-barra');
    if (barra) {
        const medir = () => document.documentElement.style.setProperty('--carta-barra-alto', `${barra.offsetHeight}px`);
        medir();
        if (window.ResizeObserver) new ResizeObserver(medir).observe(barra);
        window.addEventListener('resize', medir);
    }

    revisarEstado();
})();
