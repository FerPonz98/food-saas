window.escapeHtml = function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
};

window.secureFetch = function secureFetch(url, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const headers = new Headers(options.headers || {});
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!token || !header) throw new Error('Token CSRF no disponible');
        headers.set(header, token);
    }
    return fetch(url, { ...options, headers, credentials: 'same-origin' });
};

window.apiError = async function apiError(response) {
    let mensaje;
    try { mensaje = (await response.json()).mensaje; } catch (error) { mensaje = null; }
    return new Error(mensaje || `Error HTTP ${response.status}`);
};

window.showNotice = function showNotice(message, tipo = 'error') {
    const notice = document.createElement('div');
    notice.className = `notice-ui notice-ui-${tipo === 'ok' ? 'ok' : 'error'}`;
    notice.setAttribute('role', tipo === 'ok' ? 'status' : 'alert');
    const texto = document.createElement('span');
    texto.textContent = String(message);
    const cerrar = document.createElement('button');
    cerrar.type = 'button';
    cerrar.className = 'notice-ui-cerrar flat';
    cerrar.textContent = 'Cerrar';
    cerrar.addEventListener('click', () => notice.remove());
    notice.append(texto, cerrar);
    document.body.appendChild(notice);
    setTimeout(() => notice.remove(), 5500);
};

window.confirmAction = function confirmAction(message, opciones = {}) {
    return new Promise(resolve => {
        const crear = (tag, clase, texto) => {
            const el = document.createElement(tag);
            if (clase) el.className = clase;
            if (texto !== undefined) el.textContent = String(texto);
            return el;
        };
        const dialog = crear("dialog", "dialog-ui");
        dialog.setAttribute("aria-labelledby", "dialog-ui-titulo");
        const titulo = crear("h2", null, opciones.titulo || "¿Confirmar acción?");
        titulo.id = "dialog-ui-titulo";
        dialog.append(titulo);
        if (message) dialog.append(crear("p", null, message));
        const filas = opciones.resumen || [];
        const items = opciones.items || [];
        if (filas.length || items.length || opciones.total) {
            const resumen = crear("div", "resumen");
            if (filas.length) {
                const dl = crear("dl");
                filas.filter(f => f && f.valor !== undefined && f.valor !== null && f.valor !== "")
                    .forEach(f => dl.append(crear("dt", null, f.etiqueta), crear("dd", null, f.valor)));
                resumen.append(dl);
            }
            if (items.length) {
                const ul = crear("ul");
                items.forEach(item => {
                    const li = crear("li");
                    li.append(crear("span", null, item.nombre), crear("span", null, item.detalle ?? ""));
                    ul.append(li);
                });
                resumen.append(ul);
            }
            if (opciones.total) {
                const total = crear("div", "total");
                total.append(crear("span", null, "Total"), crear("span", null, opciones.total));
                resumen.append(total);
            }
            dialog.append(resumen);
        }
        const acciones = crear("div", "modal-ui-acciones");
        const cancel = crear("button", "button-secondary", opciones.cancelar || "Cancelar");
        cancel.type = "button";
        const accept = crear("button", opciones.peligro ? "button-danger" : "button-primary", opciones.aceptar || "Confirmar");
        accept.type = "button";
        acciones.append(cancel, accept);
        dialog.append(acciones);
        const finish = answer => { dialog.close(); dialog.remove(); resolve(answer); };
        cancel.addEventListener("click", () => finish(false));
        accept.addEventListener("click", () => finish(true));
        dialog.addEventListener("cancel", event => { event.preventDefault(); finish(false); });
        document.body.appendChild(dialog);
        dialog.showModal();
        (opciones.peligro ? cancel : accept).focus();
    });
};

window.pedirMotivo = function pedirMotivo(opciones = {}) {
    return new Promise(resolve => {
        const crear = (tag, clase, texto) => {
            const el = document.createElement(tag);
            if (clase) el.className = clase;
            if (texto !== undefined) el.textContent = String(texto);
            return el;
        };
        const dialog = crear("dialog", "dialog-ui dialog-motivo");
        dialog.setAttribute("aria-labelledby", "dialog-motivo-titulo");
        const titulo = crear("h2", null, opciones.titulo || "¿Por qué?");
        titulo.id = "dialog-motivo-titulo";
        dialog.append(titulo);
        if (opciones.texto) dialog.append(crear("p", null, opciones.texto));
        const sugerencias = opciones.sugerencias || [];
        const campo = crear("input", "field");
        campo.id = "dialog-motivo-campo";
        campo.maxLength = 200;
        campo.autocomplete = "off";
        campo.placeholder = opciones.placeholder || "Escribe el motivo";
        if (sugerencias.length) {
            const chips = crear("div", "dialog-motivo-chips");
            chips.setAttribute("role", "group");
            chips.setAttribute("aria-label", "Motivos frecuentes");
            sugerencias.forEach(s => {
                const b = crear("button", "dialog-motivo-chip flat", s);
                b.type = "button";
                b.setAttribute("aria-pressed", "false");
                b.addEventListener("click", () => {
                    campo.value = s;
                    chips.querySelectorAll("button").forEach(x => x.setAttribute("aria-pressed", String(x === b)));
                    error.hidden = true;
                    accept.focus();
                });
                chips.append(b);
            });
            dialog.append(chips);
        }
        const etiqueta = crear("label", "dialog-motivo-etiqueta", opciones.obligatorio ? "Motivo" : "Motivo (opcional)");
        etiqueta.htmlFor = campo.id;
        const error = crear("p", "dialog-motivo-error", "Indica el motivo para continuar.");
        error.hidden = true;
        dialog.append(etiqueta, campo, error);
        const acciones = crear("div", "modal-ui-acciones");
        const cancel = crear("button", "button-secondary", opciones.cancelar || "Cancelar");
        cancel.type = "button";
        const accept = crear("button", opciones.peligro ? "button-danger" : "button-primary", opciones.aceptar || "Confirmar");
        accept.type = "button";
        acciones.append(cancel, accept);
        dialog.append(acciones);
        const finish = answer => { dialog.close(); dialog.remove(); resolve(answer); };
        const aceptar = () => {
            const valor = campo.value.trim();
            if (opciones.obligatorio && !valor) { error.hidden = false; campo.focus(); return; }
            finish(valor);
        };
        cancel.addEventListener("click", () => finish(null));
        accept.addEventListener("click", aceptar);
        campo.addEventListener("keydown", e => { if (e.key === "Enter") { e.preventDefault(); aceptar(); } });
        dialog.addEventListener("cancel", event => { event.preventDefault(); finish(null); });
        document.body.appendChild(dialog);
        dialog.showModal();
        (sugerencias.length ? dialog.querySelector(".dialog-motivo-chip") : campo).focus();
    });
};
