(function () {
    'use strict';
    const API = '/api/superadmin';

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    function campo(id, etiqueta, valor, tipo, ayuda) {
        const caja = crear('div', 'admin-field');
        const label = crear('label', '', etiqueta);
        label.htmlFor = id;
        const input = crear('input', 'field');
        input.id = id;
        input.name = id.split('-').pop();
        input.type = tipo || 'text';
        if (tipo === 'number') {
            input.min = '0';
            input.step = 'any';
            input.inputMode = 'decimal';
        }
        input.value = valor == null ? '' : String(valor);
        caja.append(label, input);
        if (ayuda) caja.append(crear('p', 'admin-help', ayuda));
        return caja;
    }

    function numero(form, nombre) {
        const v = form.elements[nombre].value.trim();
        return v === '' ? null : Number(v);
    }

    function tarjeta(plan, modulos) {
        const form = crear('form', 'admin-card sa-plan');
        form.noValidate = true;
        const cab = crear('div', 'admin-card-head');
        cab.append(crear('h2', '', plan.nombre), crear('p', '', plan.descripcion || ''));
        const cuerpo = crear('div', 'admin-card-body admin-form-grid sa-form-dos');
        const p = plan.codigo;
        cuerpo.append(
            campo(`${p}-nombre`, 'Nombre', plan.nombre),
            campo(`${p}-descripcion`, 'Descripción', plan.descripcion),
            campo(`${p}-precioMensual`, 'Precio mensual (Bs)', plan.precioMensual, 'number'),
            campo(`${p}-precioAnual`, 'Precio anual (Bs)', plan.precioAnual, 'number'),
            campo(`${p}-precioImplementacion`, 'Implementación (Bs)', plan.precioImplementacion, 'number'),
            campo(`${p}-mesasIncluidas`, 'Mesas incluidas', plan.mesasIncluidas, 'number', 'Vacío = sin límite'),
            campo(`${p}-productosIncluidos`, 'Productos incluidos', plan.productosIncluidos, 'number', 'Vacío = sin límite'),
            campo(`${p}-usuariosIncluidos`, 'Usuarios incluidos', plan.usuariosIncluidos, 'number', 'Vacío = sin límite'));
        const activo = crear('label', 'sa-check admin-campo-ancho');
        const chk = crear('input');
        chk.type = 'checkbox';
        chk.name = 'activo';
        chk.checked = !!plan.activo;
        activo.append(chk, crear('span', '', 'Disponible para nuevos negocios'));
        cuerpo.append(activo);

        const fs = crear('fieldset', 'sa-plan-modulos admin-campo-ancho');
        fs.append(crear('legend', '', 'Módulos incluidos'));
        modulos.forEach(m => {
            const l = crear('label', 'sa-check');
            const c = crear('input');
            c.type = 'checkbox';
            c.name = 'modulo';
            c.value = m.codigo;
            c.checked = plan.modulos.includes(m.codigo);
            const texto = crear('span');
            texto.append(crear('strong', '', m.etiqueta), crear('span', 'admin-help', m.descripcion));
            l.append(c, texto);
            fs.append(l);
        });
        cuerpo.append(fs);
        const pie = crear('div', 'sa-acciones-form admin-campo-ancho');
        const guardar = crear('button', 'button-primary', `Guardar plan ${plan.nombre}`);
        guardar.type = 'submit';
        pie.append(guardar);
        cuerpo.append(pie);
        form.append(cab, cuerpo);

        form.addEventListener('submit', async e => {
            e.preventDefault();
            const datos = {
                nombre: form.elements.nombre.value.trim(),
                descripcion: form.elements.descripcion.value.trim() || null,
                precioMensual: numero(form, 'precioMensual'),
                precioAnual: numero(form, 'precioAnual'),
                precioImplementacion: numero(form, 'precioImplementacion'),
                mesasIncluidas: numero(form, 'mesasIncluidas'),
                productosIncluidos: numero(form, 'productosIncluidos'),
                usuariosIncluidos: numero(form, 'usuariosIncluidos'),
                activo: form.elements.activo.checked,
                modulos: [...form.querySelectorAll('input[name=modulo]:checked')].map(c => c.value)
            };
            const ok = await confirmAction(null, {
                titulo: `¿Guardar el plan ${datos.nombre}?`,
                resumen: [
                    { etiqueta: 'Mensual', valor: `Bs ${datos.precioMensual}` },
                    { etiqueta: 'Módulos', valor: String(datos.modulos.length) }
                ],
                items: [{ nombre: 'Los cambios de módulos se aplican al momento a todos los negocios de este plan', detalle: '' }],
                aceptar: 'Guardar'
            });
            if (!ok) return;
            guardar.disabled = true;
            try {
                const r = await secureFetch(`${API}/planes/${encodeURIComponent(plan.codigo)}`, {
                    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(datos)
                });
                if (!r.ok) throw await apiError(r);
                showNotice('Plan guardado', 'ok');
            } catch (err) {
                showNotice(err.message);
            } finally {
                guardar.disabled = false;
            }
        });
        return form;
    }

    document.addEventListener('DOMContentLoaded', async () => {
        const cont = document.getElementById('sa-planes');
        Admin.cargando(cont, 4);
        try {
            const [planes, modulos] = await Promise.all([
                fetch(`${API}/planes`).then(async r => { if (!r.ok) throw await apiError(r); return r.json(); }),
                fetch(`${API}/modulos`).then(async r => { if (!r.ok) throw await apiError(r); return r.json(); })
            ]);
            Admin.listo(cont);
            cont.replaceChildren(...planes.map(p => tarjeta(p, modulos)));
        } catch (e) {
            Admin.vacio(cont, { icono: '⚠️', titulo: 'No se pudieron cargar los planes', texto: e.message });
        }
    });
})();
