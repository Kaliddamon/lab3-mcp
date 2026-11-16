# Claude Desktop y Google ADK

Los dos clientes usan el mismo servidor: `labmcp` en `http://localhost:8088/mcp`. Las herramientas que pueden llamar son las de `PedidoToolsService`. El catálogo, los clientes y los pedidos de la base `labmcp` no cambian de un cliente a otro.

Claude Desktop es el host. Llega al servidor por el proceso `mcp-remote`. Google ADK es otro host: el agente `SupermercadoAgent` descubre las herramientas con `McpToolset` y abre su interfaz en el puerto 8080.

Cada host elige su modelo y presenta su propia pantalla. La parte reutilizada es el servidor MCP y el código de pedidos a domicilio.

Esta comparación describe la arquitectura del repositorio. Las capturas del chat quedan para la ejecución en esta máquina: primero MySQL y `labmcp` en el puerto 8088, después el agente ADK en el puerto 8080.
