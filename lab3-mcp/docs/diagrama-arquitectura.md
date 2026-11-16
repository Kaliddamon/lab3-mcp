# Diagrama de arquitectura

Roles de este laboratorio: el host contiene al cliente MCP, el servidor es `labmcp` y la herramienta es cada método de `PedidoToolsService`.

```mermaid
flowchart TD
    usuario[Usuario]
    claude[Claude Desktop : host]
    adk[Google ADK : host en el puerto 8080]
    clienteClaude[Cliente MCP via mcp-remote]
    clienteAdk[Cliente MCP via McpToolset]
    servidor[labmcp : servidor MCP en http://localhost:8088/mcp]
    tools[PedidoToolsService : herramientas de domicilio]
    repo[Repositorios JPA de producto, cliente, pedido y linea]
    mysql[(MySQL base labmcp)]

    usuario --> claude
    usuario --> adk
    claude --> clienteClaude
    adk --> clienteAdk
    clienteClaude --> servidor
    clienteAdk --> servidor
    servidor --> tools
    tools --> repo
    repo --> mysql
```

El mismo servidor atiende a los dos hosts. Claude y ADK no se hablan entre sí. El catálogo, el stock y el estado del domicilio viven en MySQL.
