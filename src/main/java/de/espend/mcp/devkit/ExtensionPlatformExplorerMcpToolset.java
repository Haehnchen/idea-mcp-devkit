package de.espend.mcp.devkit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.mcpserver.McpToolset;
import com.intellij.mcpserver.annotations.McpDescription;
import com.intellij.mcpserver.annotations.McpTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class ExtensionPlatformExplorerMcpToolset implements McpToolset {
    private static final String EXTENSION_POINTS_API_URL = "https://plugins.jetbrains.com/api/extension-points";
    private static final String GRAPHQL_API_URL = "https://plugins.jetbrains.com/api/search/graphql";

    @McpTool(name = "intellij_extension_list")
    @McpDescription(description = """
            Lists all available IntelliJ Platform extension points from the JetBrains Marketplace.
           \s
            USE THIS TOOL WHEN:
            - You need to discover what extension points are available in the IntelliJ Platform
            - You want to find out how to extend IntelliJ IDEA functionality
            - You're starting plugin development and need to explore extensibility options
           \s
            RETURNS: A list of all extension point names (e.g., 'com.intellij.completion.contributor', 'com.intellij.psi.referenceContributor')
           \s
            NEXT STEPS: After getting the list, use 'intellij_extension_search' to filter by keyword or 'intellij_extension_detail' to get implementation examples.
       \s""")
    public Object extension_list() {
        return String.join("\n", getExtensions());
    }

    @McpTool(name = "intellij_extension_search")
    @McpDescription(description = """
            Searches for IntelliJ Platform extension points by keyword from the JetBrains Marketplace.
           \s
            USE THIS TOOL WHEN:
            - You know a partial name of an extension point (e.g., 'completion.contributor', 'reference')
           \s
            EXAMPLES:
            - Search 'contributor' to find all contributor-based extension points
            - Search 'completion' to find code completion related extensions
           \s
            RETURNS: Filtered list of matching extension point names
           \s
            NEXT STEPS: Use 'intellij_extension_detail' with a specific extension name to see real-world implementation examples with source code links.
       \s""")
    public Object extension_search(@McpDescription(description = "Keyword to search for in extension point names. Examples: 'contributor', 'completion.contributor', 'action', 'inspection'") @NotNull String search) {
        Set<String> elements = searchExtension(search);
        if (elements.isEmpty()) {
            return "error: no extension found.";
        }

        return String.join("\n", elements);
    }

    @McpTool(name = "intellij_extension_detail")
    @McpDescription(description = """
            Retrieves real-world implementation examples for a specific IntelliJ Platform extension point.
           \s
            USE THIS TOOL WHEN:
            - You have identified an extension point and want to see how it's implemented in practice
            - You need source code examples from existing plugins
            - You want to learn best practices for implementing a specific extension
           \s
            PROVIDES:
            - Plugin names that implement this extension point
            - Direct links to source code repositories (GitHub, GitLab, etc.)
           \s
            EXAMPLE USAGE:
            - extension_detail('com.intellij.psi.referenceContributor') - see reference resolution implementations
           \s
            TIP: Visit the source code links to study implementation patterns and copy/adapt code for your plugin.
       \s""")
    public Object extension_detail(@McpDescription(description = "The fully qualified extension point name, e.g., 'com.intellij.completion.contributor'") @NotNull String extension) {
        StringBuilder content = new StringBuilder();
        for (String s : getExtensions()) {
            if (!s.equalsIgnoreCase(extension)) {
                continue;
            }

            content.append("# ").append(s).append("\n\n");
            content.append("## Plugin Implementations:\n");

            Collection<PluginUsage> pluginUsages = getPluginsForExtension(extension);
            for (PluginUsage pluginUsage : pluginUsages) {
                String githubSearchUrl = buildGitHubSearchUrl(pluginUsage.sourceCodeUrl, extension);

                content.append(" - \"%s\" Source: %s%s\n".formatted(
                    pluginUsage.name,
                    pluginUsage.sourceCodeUrl,
                    githubSearchUrl != null ? " Search: " + githubSearchUrl : ""
                ));
            }

            if (pluginUsages.isEmpty()) {
                content.append("No public implementations found with source code available.\n");
            }
        }

        if (content.isEmpty()) {
            return "Extension point '%s' not found. Use 'intellij_extension_search' to find available extension points.".formatted(extension);
        }

        return content.toString();
    }

    private Set<String> getExtensions() {
        Set<String> result = new HashSet<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXTENSION_POINTS_API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement element : jsonArray) {
                    JsonObject obj = element.getAsJsonObject();
                    if (obj.has("implementationName") && !obj.get("implementationName").isJsonNull()) {
                        result.add(obj.get("implementationName").getAsString());
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            return result;
        }

        return result;
    }


    private Set<String> searchExtension(@NotNull String extension) {
        Set<String> result = new HashSet<>();

        for (String s : getExtensions()) {
            if (s.toLowerCase().contains(extension.toLowerCase())) {
                result.add(s);
            }
        }

        return result;
    }

    private Collection<PluginUsage> getPluginsForExtension(@NotNull String extension) {
        Map<String, PluginUsage> uniquePlugins = new LinkedHashMap<>();

        // First request: sorted by downloads
        fetchPlugins(extension, "DOWNLOADS", uniquePlugins);

        // Second request: sorted by lastUpdateDate
        fetchPlugins(extension, "UPDATE_DATE", uniquePlugins);

        return filterAndSortPlugins(new ArrayList<>(uniquePlugins.values()));
    }

    private void fetchPlugins(@NotNull String extension, @NotNull String sortBy, @NotNull Map<String, PluginUsage> uniquePlugins) {
        String graphqlQuery = """
                { plugins(search: { max: 24, offset: 0, filters: [
                    { field: "fields.extensionPoints", value: "%s" },
                    { field: "hasSource", value: "true" },
                    { field: "family", value: "intellij" }
                ], sortBy: %s }) {
                    total, plugins {
                        id, name, downloads, sourceCodeUrl, lastUpdateDate,
                        organization { id, verified }
                    }
                }}""".formatted(extension, sortBy);

        String requestBody = "{\"query\":" + JsonParser.parseString("\"" + graphqlQuery.replace("\n", " ").replace("\"", "\\\"") + "\"").toString() + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPHQL_API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                if (root.has("data") && root.getAsJsonObject("data").has("plugins")) {
                    JsonObject pluginsData = root.getAsJsonObject("data").getAsJsonObject("plugins");
                    if (pluginsData.has("plugins")) {
                        JsonArray plugins = pluginsData.getAsJsonArray("plugins");
                        for (JsonElement pluginElement : plugins) {
                            JsonObject plugin = pluginElement.getAsJsonObject();
                            String id = plugin.has("id") && !plugin.get("id").isJsonNull() ? plugin.get("id").getAsString() : null;
                            if (id == null || uniquePlugins.containsKey(id)) {
                                continue;
                            }

                            String sourceCodeUrl = plugin.has("sourceCodeUrl") && !plugin.get("sourceCodeUrl").isJsonNull() ? plugin.get("sourceCodeUrl").getAsString() : "";
                            if (sourceCodeUrl.isEmpty()) {
                                continue;
                            }

                            String name = plugin.has("name") && !plugin.get("name").isJsonNull() ? plugin.get("name").getAsString() : "Unknown";
                            int downloads = plugin.has("downloads") && !plugin.get("downloads").isJsonNull() ? plugin.get("downloads").getAsInt() : 0;
                            long lastUpdateDate = plugin.has("lastUpdateDate") && !plugin.get("lastUpdateDate").isJsonNull() ? plugin.get("lastUpdateDate").getAsLong() : 0;
                            boolean verified = false;

                            if (plugin.has("organization") && !plugin.get("organization").isJsonNull()) {
                                JsonObject org = plugin.getAsJsonObject("organization");
                                verified = org.has("verified") && !org.get("verified").isJsonNull() && org.get("verified").getAsBoolean();
                            }

                            uniquePlugins.put(id, new PluginUsage(id, name, downloads, sourceCodeUrl, verified, lastUpdateDate));
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException ignored) {
        }
    }

    private List<PluginUsage> filterAndSortPlugins(@NotNull List<PluginUsage> allPlugins) {
        Set<String> selectedIds = new HashSet<>();
        List<PluginUsage> result = new ArrayList<>();

        // Top 3 by downloads
        allPlugins.stream()
                .sorted(Comparator.comparingInt(PluginUsage::downloads).reversed())
                .limit(3)
                .forEach(p -> {
                    if (selectedIds.add(p.id())) {
                        result.add(p);
                    }
                });

        // Top 3 by recently updated (not already selected)
        allPlugins.stream()
                .filter(p -> !selectedIds.contains(p.id()))
                .sorted(Comparator.comparingLong(PluginUsage::lastUpdateDate).reversed())
                .limit(3)
                .forEach(p -> {
                    if (selectedIds.add(p.id())) {
                        result.add(p);
                    }
                });

        // Top 3 verified (not already selected)
        allPlugins.stream()
                .filter(p -> p.verified() && !selectedIds.contains(p.id()))
                .sorted(Comparator.comparingInt(PluginUsage::downloads).reversed())
                .limit(3)
                .forEach(p -> {
                    if (selectedIds.add(p.id())) {
                        result.add(p);
                    }
                });

        // Fill remaining slots up to maxResults
        int remaining = 13 - result.size();
        if (remaining > 0) {
            allPlugins.stream()
                    .filter(p -> !selectedIds.contains(p.id()))
                    .sorted(Comparator.comparingInt(PluginUsage::downloads).reversed())
                    .limit(remaining)
                    .forEach(p -> {
                        if (selectedIds.add(p.id())) {
                            result.add(p);
                        }
                    });
        }

        return result;
    }

    private static HttpClient createHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Nullable
    private String buildGitHubSearchUrl(@NotNull String url, @NotNull String extension) {
        Pattern githubRepoPattern = Pattern.compile("github\\.com/([^/]+)/([^/?#]+)");
        Matcher matcher = githubRepoPattern.matcher(url);

        String ownerRepo = null;
        if (matcher.find()) {
            ownerRepo = matcher.group(1) + "/" + matcher.group(2);
        }

        if (ownerRepo == null) {
            return null;
        }

        String extensionShortName = extension.contains(".") ? extension.substring(extension.lastIndexOf('.') + 1) : extension;

        String query = "repo:%s %s OR repo:%s %s AND (path:*.java OR path:*.kt OR path:*.xml)".formatted(
                ownerRepo, extension, ownerRepo, extensionShortName
        );

        return "https://github.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=code";
    }

    record PluginUsage(@NotNull String id, @NotNull String name, int downloads, @NotNull String sourceCodeUrl, boolean verified, long lastUpdateDate)
    {
    }
}
