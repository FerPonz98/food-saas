CREATE TABLE plataforma_marca (
    id integer PRIMARY KEY,
    nombre varchar(120) NOT NULL,
    eslogan varchar(240),
    color_primario varchar(7) NOT NULL,
    color_secundario varchar(7) NOT NULL,
    color_acento varchar(7) NOT NULL,
    logo_datos bytea,
    logo_mime_type varchar(40),
    logo_version bigint NOT NULL DEFAULT 1,
    CONSTRAINT plataforma_marca_unica CHECK (id = 1)
);
INSERT INTO plataforma_marca (id, nombre, eslogan, color_primario, color_secundario, color_acento)
VALUES (1, 'Plataforma', 'Administración de negocios y planes', '#24324A', '#64748B', '#C56A3D');
