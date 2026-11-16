package com.udea.agente_mcp;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Gemini;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpToolset;
import com.google.adk.tools.mcp.StreamableHttpServerParameters;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.ThinkingConfig;

import java.time.Duration;
import java.util.List;

public class SupermercadoAgent {
        public static BaseAgent ROOT_AGENT = buildAgent();

        private static GenerateContentConfig noThinkingConfig() {
                return GenerateContentConfig.builder()
                                .thinkingConfig(ThinkingConfig.builder()
                                                .thinkingBudget(Integer.valueOf(0))
                                                .includeThoughts(false)
                                                .build())
                                .build();
        }

        private static BaseLlm buildLlm() {
                String apiKey = System.getenv("GOOGLE_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                        apiKey = System.getenv("GEMINI_API_KEY");
                }
                final String key = apiKey;

                return new ResilientLlm(List.of(
                                Gemini.builder().modelName("gemini-3.8-flash-lite").apiKey(key).build(),
                                Gemini.builder().modelName("gemini-2.5-flash-lite").apiKey(key).build(),
                                Gemini.builder().modelName("gemini-2.5-flash").apiKey(key).build()), 3);
        }

        private static BaseAgent buildAgent() {
                StreamableHttpServerParameters params = StreamableHttpServerParameters.builder()
                                .url("http://localhost:8088/mcp")
                                .timeout(Duration.ofSeconds(30L))
                                .build();
                McpToolset mcpToolset = new McpToolset(params);

                try {
                        List<BaseTool> mcpTools = mcpToolset.getTools(null)
                                        .toList()
                                        .blockingGet();
                        System.out.println("Tools MCP: " + mcpTools.size());

                        return LlmAgent.builder()
                                        .name("SupermercadoAgent")
                                        .model(buildLlm())
                                        .description("Agente de pedidos de supermercado a domicilio")
                                        .instruction("""
                                                        Eres un asistente de pedidos de supermercado a domicilio. Responde siempre en español.
                                                        Usa SOLO las herramientas MCP para consultar precios, stock y gestionar pedidos.
                                                        Antes de mencionar un precio, busca el producto con la herramienta correspondiente.
                                                        Para hacer un pedido: pide nombre, teléfono y dirección al cliente.
                                                        Abre el pedido con crearPedido, agrega productos con agregarProductoAlPedido.
                                                        Muestra el total con verPedido y llama confirmarPedido solo cuando el cliente acepte.
                                                        Si el servidor rechaza una operación, explica el mensaje y no inventes resultados.
                                                        """)
                                        .tools(mcpTools)
                                        .generateContentConfig(noThinkingConfig())
                                        .build();

                } catch (Exception e) {
                        System.err.println("MCP no disponible: " + e.getMessage());
                        return LlmAgent.builder()
                                        .name("SupermercadoAgent")
                                        .model(buildLlm())
                                        .description("Agente de supermercado (sin MCP)")
                                        .instruction("El servidor MCP no está disponible. Informa al usuario y no inventes productos ni precios.")
                                        .generateContentConfig(noThinkingConfig())
                                        .build();
                }
        }
}