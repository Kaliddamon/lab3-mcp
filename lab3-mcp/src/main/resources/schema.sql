-- ============================================================
-- Crear la base de datos una sola vez antes de iniciar el servidor MCP.
-- Usar la ruta de lab3-mcp, por ejemplo en PowerShell:
--   mysql -u root -p -e "source C:/Users/CRIST/Desktop/ArquiSoft/lab3-mcp/src/main/resources/schema.sql"
-- ============================================================

CREATE DATABASE IF NOT EXISTS labmcp
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE labmcp;

DROP TABLE IF EXISTS linea_pedido, pedido, cliente, producto, tarea;

CREATE TABLE producto (
    id        BIGINT         NOT NULL AUTO_INCREMENT,
    nombre    VARCHAR(255)   NOT NULL,
    categoria VARCHAR(100)   NOT NULL,
    precio    DECIMAL(10,2)  NOT NULL,
    unidad    VARCHAR(30)    NOT NULL,
    stock     INT            NOT NULL,
    version   BIGINT         DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cliente (
    id        BIGINT        NOT NULL AUTO_INCREMENT,
    nombre    VARCHAR(255)  NOT NULL,
    telefono  VARCHAR(30)   NOT NULL,
    direccion VARCHAR(255)  NOT NULL,
    version   BIGINT        DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cliente_telefono (telefono)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pedido (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    cliente_id         BIGINT         NOT NULL,
    direccion_entrega  VARCHAR(255)   NOT NULL,
    estado             VARCHAR(30)    NOT NULL,
    total              DECIMAL(10,2)  NOT NULL DEFAULT 0,
    version            BIGINT         DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_pedido_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE linea_pedido (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    pedido_id       BIGINT         NOT NULL,
    producto_id     BIGINT         NOT NULL,
    cantidad        INT            NOT NULL,
    precio_unitario DECIMAL(10,2)  NOT NULL,
    version         BIGINT         DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_linea_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id),
    CONSTRAINT fk_linea_producto FOREIGN KEY (producto_id) REFERENCES producto (id),
    UNIQUE (pedido_id, producto_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Catálogo inicial del supermercado. No hay filas de tareas ni de pedidos.
INSERT INTO producto (nombre, categoria, precio, unidad, stock) VALUES
    ('Leche entera',        'lacteos',    4500.00, 'litro',     40),
    ('Queso campesino',     'lacteos',    9800.00, 'libra',     18),
    ('Yogur natural',       'lacteos',    3200.00, 'unidad',    25),
    ('Arroz blanco',        'abarrotes',  3800.00, 'kilogramo', 55),
    ('Frijol bola roja',    'abarrotes',  6200.00, 'libra',     30),
    ('Aceite vegetal',      'abarrotes', 14500.00, 'botella',   20),
    ('Banano',              'frutas',     2500.00, 'kilogramo', 36),
    ('Manzana roja',        'frutas',     7900.00, 'kilogramo', 22),
    ('Jabón de baño',       'aseo',       2800.00, 'unidad',    48),
    ('Detergente en polvo', 'aseo',       9200.00, 'bolsa',     26),
    ('Agua sin gas',        'bebidas',    2000.00, 'botella',   80),
    ('Jugo de naranja',     'bebidas',    5600.00, 'litro',     28);
