(function () {
    'use strict';

    const CICLOS = { MENSUAL: 'Mensual', ANUAL: 'Anual' };
    const ESTADOS = { ACTIVO: 'badge badge-ok', PRUEBA: 'badge badge-info', SUSPENDIDO: 'badge badge-warn', CANCELADO: 'badge badge-off' };

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    function fecha(iso) {
        if (!iso) return 'Sin fecha de vencimiento';
        const d = new Date(`${iso}T00:00:00`);
        return isNaN(d) ? iso : d.toLocaleDateString('es', { day: 'numeric', month: 'long', year: 'numeric' });
    }

    function medidor(titulo, usado, incluido) {
        const caja = crear('div', 'plan-medidor');
        const cab = crear('div', 'plan-medidor-cab');
        cab.append(crear('span', 'plan-medidor-titulo', titulo),
            crear('span', 'plan-medidor-valor', incluido == null ? `${usado} · sin límite` : `${usado} de ${incluido}`));
        caja.append(cab);
        const barra = crear('div', 'plan-barra');
        barra.setAttribute('role', 'meter');
        barra.setAttribute('aria-label', titulo);
        barra.setAttribute('aria-valuemin', '0');
        barra.setAttribute('aria-valuenow', String(usado));
        const relleno = crear('span', 'plan-barra-relleno');
        if (incluido == null) {
            relleno.style.width = '100%';
            barra.classList.add('plan-barra-libre');
        } else {
            barra.setAttribute('aria-valuemax', String(incluido));
            const proporcion = incluido > 0 ? Math.min(1, usado / incluido) : 1;
            relleno.style.width = `${Math.round(proporcion * 100)}%`;
            if (usado > incluido) barra.classList.add('plan-barra-excedida');
        }
        barra.append(relleno);
        caja.append(barra);
        return caja;
    }

    function pintar(p) {
        document.getElementById('plan-nombre').textContent = p.planNombre;
        const estado = crear('span', ESTADOS[p.estado] || 'badge', p.estadoEtiqueta);
        document.getElementById('plan-estado').replaceChildren(estado);
        document.getElementById('plan-ciclo').textContent = CICLOS[p.ciclo] || p.ciclo;
        let vence = fecha(p.venceEn);
        if (p.diasParaVencer != null && p.diasParaVencer >= 0) vence += p.diasParaVencer === 0 ? ' (hoy)' : ` (en ${p.diasParaVencer} ${p.diasParaVencer === 1 ? 'día' : 'días'})`;
        document.getElementById('plan-vence').textContent = vence;

        const n = window.NEGOCIO || {};
        document.getElementById('plan-uso').replaceChildren(
            medidor(n.puntoAtencionPlural || 'Mesas', p.uso.mesas, p.mesasIncluidas),
            medidor('Productos', p.uso.productos, p.productosIncluidos),
            medidor('Usuarios activos', p.uso.usuarios, p.usuariosIncluidos));
        const avisos = document.getElementById('plan-avisos');
        avisos.replaceChildren(...p.avisos.map(a => crear('li', 'plan-aviso', a)));
        avisos.hidden = !p.avisos.length;

        const grupos = new Map();
        p.modulos.forEach(m => {
            if (!grupos.has(m.grupo)) grupos.set(m.grupo, []);
            grupos.get(m.grupo).push(m);
        });
        const contenedor = document.getElementById('plan-modulos');
        contenedor.replaceChildren();
        grupos.forEach((lista, grupo) => {
            const bloque = crear('div', 'plan-grupo');
            bloque.append(crear('h3', 'plan-grupo-titulo', grupo));
            const ul = crear('ul', 'plan-lista');
            lista.forEach(m => {
                const li = crear('li', `plan-modulo ${m.incluido ? 'plan-incluido' : 'plan-no-incluido'}`);
                li.id = `modulo-${m.codigo}`;
                const marca = crear('span', 'plan-marca', m.incluido ? '✓' : '—');
                marca.setAttribute('aria-hidden', 'true');
                const texto = crear('div', 'plan-modulo-texto');
                texto.append(crear('strong', '', m.etiqueta), crear('span', 'admin-help', m.descripcion));
                const estadoTxt = crear('span', m.incluido ? 'badge badge-ok' : 'badge badge-off', m.incluido ? 'Incluido' : 'No incluido');
                li.append(marca, texto, estadoTxt);
                ul.append(li);
            });
            bloque.append(ul);
            contenedor.append(bloque);
        });

        const pedido = new URLSearchParams(location.search).get('modulo');
        const aviso = document.getElementById('plan-aviso-modulo');
        const modulo = pedido && p.modulos.find(m => m.codigo === pedido);
        if (modulo && !modulo.incluido) {
            aviso.textContent = `«${modulo.etiqueta}» no está incluido en tu plan ${p.planNombre}. Pide a soporte que lo active o que te cambie de plan.`;
            aviso.hidden = false;
            const fila = document.getElementById(`modulo-${modulo.codigo}`);
            if (fila) fila.classList.add('plan-resaltado');
        }
    }

    async function cargar() {
        try {
            const r = await fetch('/api/admin/plan', { headers: { Accept: 'application/json' } });
            if (!r.ok) throw await apiError(r);
            pintar(await r.json());
        } catch (e) {
            showNotice(e.message || 'No se pudo cargar tu plan');
        }
    }

    document.addEventListener('DOMContentLoaded', cargar);
})();
