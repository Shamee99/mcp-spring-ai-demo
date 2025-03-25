package org.example.mcpserverspringai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class WeatherService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.weather.gov")
                .defaultHeader("Accept", "application/geo+json")
                .defaultHeader("User-Agent", "WeatherApiClient/1.0 (your@email.com)")
                .build();
    }

    @Tool(name="getWeatherForecastByLocation", description = "Get weather forecast for a specific latitude/longitude")
    public String getWeatherForecastByLocation(
            double latitude,
            double longitude
    ) {
        try {
            // Step 1: 获取点位信息
            String pointsResponse = restClient.get()
                    .uri("/points/{lat},{lon}", latitude, longitude)
                    .retrieve()
                    .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), (req, res) -> {
                        throw new WeatherApiException("Failed to get location data: " + res.getStatusText());
                    })
                    .body(String.class);

            JsonNode pointsRoot = objectMapper.readTree(pointsResponse);
            String forecastUrl = pointsRoot.path("properties")
                    .path("forecast")
                    .asText();

            // Step 2: 获取预报数据
            String forecastResponse = restClient.get()
                    .uri(forecastUrl)
                    .retrieve()
                    .body(String.class);

            return parseForecastData(forecastResponse);

        } catch (RestClientResponseException e) {
            throw new WeatherApiException("Weather API error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new WeatherApiException("Failed to retrieve forecast", e);
        }
    }

    private String parseForecastData(String json) throws Exception {
        StringBuilder result = new StringBuilder();
        JsonNode root = objectMapper.readTree(json);
        JsonNode periods = root.path("properties").path("periods");

        result.append("## Weather Forecast\n");
        for (JsonNode period : periods) {
            result.append(String.format(
                    "### %s\n- Temperature: %.1f°F / %.1f°C\n- Wind: %s %s\n- Details: %s\n\n",
                    period.path("name").asText(),
                    period.path("temperature").asDouble(),
                    fahrenheitToCelsius(period.path("temperature").asDouble()),
                    period.path("windDirection").asText(),
                    period.path("windSpeed").asText(),
                    period.path("detailedForecast").asText()
            ));
        }
        return result.toString();
    }

    @Tool(name="getAlerts", description = "Get weather alerts for a US state")
    public String getAlerts(@ToolParam(description = "Two-letter US state code (e.g. CA, NY)") String state) {
        if (state == null || state.length() != 2) {
            throw new IllegalArgumentException("Invalid state code format");
        }

        try {
            String alertResponse = restClient.get()
                    .uri("/alerts/active?area={state}", state.toUpperCase())
                    .retrieve()
                    .body(String.class);

            return parseAlertData(alertResponse);

        } catch (RestClientResponseException e) {
            throw new WeatherApiException("Weather API error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new WeatherApiException("Failed to retrieve alerts", e);
        }
    }

    private String parseAlertData(String json) throws Exception {
        StringBuilder result = new StringBuilder();
        JsonNode root = objectMapper.readTree(json);
        JsonNode features = root.path("features");

        result.append("## Weather Alerts\n");
        for (JsonNode feature : features) {
            JsonNode properties = feature.path("properties");
            result.append(String.format(
                    "### %s\n- Severity: %s\n- Areas: %s\n- Effective: %s\n- Instructions: %s\n\n",
                    properties.path("event").asText(),
                    properties.path("severity").asText(),
                    properties.path("areaDesc").asText(),
                    properties.path("effective").asText(),
                    properties.path("instruction").asText()
            ));
        }

        if (result.isEmpty()) {
            return "No active alerts for this area";
        }
        return result.toString();
    }

    private double fahrenheitToCelsius(double f) {
        return (f - 32) * 5 / 9;
    }

    public static class WeatherApiException extends RuntimeException {
        public WeatherApiException(String message) {
            super(message);
        }

        public WeatherApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
