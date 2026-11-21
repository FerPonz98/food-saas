(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', () => {
        const destino = document.getElementById('disponibilidad-panel');
        if (destino && window.Disponibilidad) Disponibilidad.montar(destino);
    });
})();
