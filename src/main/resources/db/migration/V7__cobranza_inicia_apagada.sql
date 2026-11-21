ALTER TABLE parametros_cobro ADD COLUMN cobranza_activa boolean DEFAULT true NOT NULL;
UPDATE parametros_cobro SET cobranza_activa = false WHERE id = 1;
