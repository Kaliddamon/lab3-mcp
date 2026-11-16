# Pasos de la solución

El entregable es un chatbot que hace pedidos de supermercado a domicilio. El chatbot habla con un servidor MCP y ese servidor lee y escribe MySQL. El dominio anterior, de tareas, se reemplazó por catálogo, clientes y pedidos.

## 1. Base de datos

`src/main/resources/schema.sql` crea la base `labmcp` y cuatro tablas: `producto`, `cliente`, `pedido` y `linea_pedido`. También borra la tabla vieja `tarea` e inserta doce productos con precio y stock.

En PowerShell, con MySQL en marcha:

```powershell
mysql -u root -p -e "source C:/Users/CRIST/Desktop/ArquiSoft/lab3-mcp/src/main/resources/schema.sql"
```

La clave no va en el código. El servidor la toma de la variable de entorno `PWD_UDEA`.

## 2. Modelo y acceso a datos

Las entidades JPA viven en `com.example.labmcp.model`. `Pedido` guarda la dirección de entrega, el total y un estado: `BORRADOR`, `CONFIRMADO`, `EN_PREPARACION`, `EN_CAMINO`, `ENTREGADO` o `CANCELADO`. Cada repositorio extiende `JpaRepository` y agrega búsquedas por nombre, categoría, teléfono o estado.

## 3. Herramientas MCP

`PedidoToolsService` publica un método por operación. Spring AI las expone porque cada método lleva `@Tool` y el arranque las registra en `LabmcpApplication` con `MethodToolCallbackProvider`.

El flujo de un domicilio es este:

1. `buscarProductos` o `listarProductosPorCategoria` consultan el catálogo.
2. `crearPedido` abre un borrador con nombre, teléfono y dirección. Si el teléfono ya existe, reutiliza el cliente.
3. `agregarProductoAlPedido` suma unidades solo mientras el pedido está en borrador y la cantidad no supera el stock. El precio de la línea queda fijo en ese momento.
4. `verPedido` muestra líneas, dirección y total. El total lo calcula el servidor.
5. `confirmarPedido` descuenta el stock y pasa el pedido a `CONFIRMADO`.
6. `avanzarEntrega` recorre preparación, camino y entrega. `cancelarPedido` anula el pedido y, si el stock ya se había descontado, lo devuelve.

El servidor sigue en `http://localhost:8088/mcp`, protocolo streamable, sin stdio. Para levantarlo, desde `lab3-mcp`:

```powershell
$env:PWD_UDEA = "<clave de MySQL>"
.\mvnw.cmd spring-boot:run
```

## 4. Chatbot

El cliente está en el proyecto `agentemcp`, clase `SupermercadoAgent`. Copia el cliente MCP que ya existía: `StreamableHttpServerParameters` apunta a `http://localhost:8088/mcp` y `McpToolset` descarga las tools al arrancar. La instrucción del agente le pide buscar precios en las tools, armar el borrador y llamar `confirmarPedido` solo cuando la persona acepta el total.

Con el servidor ya arriba, desde `agentemcp`:

```powershell
.\mvnw.cmd compile exec:java
```

La interfaz de ADK queda en el puerto 8080. En el arranque debe imprimir `Tools MCP:` con un número mayor que cero. Si el servidor está apagado, el agente arranca sin tools y lo dice en la instrucción de respaldo.

## 5. Comprobación

Las reglas de stock, borrador y cancelación están en `PedidoToolsServiceTest`. Desde `lab3-mcp`:

```powershell
.\mvnw.cmd -Dtest=PedidoToolsServiceTest test
```

Un pedido de punta a punta, con MySQL y el servidor en marcha, es: buscar un producto, crear el pedido, agregar una cantidad menor al stock, ver el total, confirmar y comprobar que el stock de ese producto bajó.
