package org.example.mcpclientspringai;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest
class McpClientSpringAiApplicationTests {

    @Test
    void contextLoads() {
        var stdioParams = ServerParameters.builder("java")
                .args("-jar", "E:\\idea_projects\\mcp-server-spring-ai\\target\\mcp-server-spring-ai-0.0.1-SNAPSHOT.jar")
                .build();

        var stdioTransport = new StdioClientTransport(stdioParams);

        var mcpClient = McpClient.sync(stdioTransport).build();

        mcpClient.initialize();

        McpSchema.ListToolsResult toolsList = mcpClient.listTools();

        System.out.println("MCP tools集合：" + toolsList.tools());

        McpSchema.CallToolResult weather = mcpClient.callTool(
                new McpSchema.CallToolRequest("getWeatherForecastByLocation",
                        Map.of("latitude", "47.6062", "longitude", "-122.3321")));

        System.out.println("根据经纬度查询天气信息：" + weather.content());

        McpSchema.CallToolResult alert = mcpClient.callTool(
                new McpSchema.CallToolRequest("getAlerts", Map.of("state", "NY")));

        System.out.println("获取天气状态信息：" + alert.content());

        mcpClient.closeGracefully();
    }

}
