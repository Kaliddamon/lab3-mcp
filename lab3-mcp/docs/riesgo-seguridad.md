# Riesgo de seguridad

## Riesgo

El servidor MCP de este proyecto escucha en `http://localhost:8088/mcp` sin autenticación. Quien alcance ese puerto puede invocar las herramientas publicadas. `confirmarPedido` descuenta stock y `cancelarPedido` puede reponerlo o anular un domicilio, enviando solo el id del pedido.

La conexión a MySQL está declarada con `useSSL=false` y `allowPublicKeyRetrieval=true`. Además, `spring.jpa.hibernate.ddl-auto=update` permite que el arranque modifique el esquema de `labmcp`.

## Mitigación

Dejar el proceso solo en localhost y no abrir el puerto 8088 en el firewall ni hacia la red del laboratorio. Mantener la clave de MySQL en la variable `PWD_UDEA` de la sesión, fuera del código y de las capturas. Antes de llamar `confirmarPedido` o `cancelarPedido`, revisar el pedido con `verPedido`.
