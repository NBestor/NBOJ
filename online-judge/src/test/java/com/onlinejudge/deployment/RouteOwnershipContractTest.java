package com.onlinejudge.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.shared.web.OnlineJudgeWebPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOwnershipContractTest {

    private static final Path CONTRACT_PATH = Path.of("config", "route-ownership.json");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void routeOwnershipContractHasDisjointNormalizedNamespaces() throws IOException {
        JsonNode contract = readContract();
        String publicPath = contract.at("/onlineJudge/publicPath").asText();
        String apiPath = contract.at("/onlineJudge/apiPath").asText();
        String assetsPath = contract.at("/onlineJudge/assetsPath").asText();
        List<String> reservedPaths = new ArrayList<>();
        contract.at("/platform/reservedPaths").forEach(path -> reservedPaths.add(path.asText()));

        assertThat(contract.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(contract.path("productionHost").asText()).matches("^[a-z0-9.-]+$");
        assertThat(publicPath).startsWith("/").endsWith("/");
        assertThat(apiPath).startsWith(publicPath).endsWith("/");
        assertThat(assetsPath).startsWith(publicPath).endsWith("/");
        assertThat(reservedPaths).isNotEmpty().allMatch(path -> path.startsWith("/") && path.endsWith("/"));

        for (String reservedPath : reservedPaths) {
            assertThat(publicPath.startsWith(reservedPath) || reservedPath.startsWith(publicPath))
                    .as("Online Judge path must not overlap platform-owned root %s", reservedPath)
                    .isFalse();
        }
    }

    @Test
    void backendConstantsMatchTheCrossLayerContract() throws IOException {
        JsonNode contract = readContract();

        assertThat(OnlineJudgeWebPaths.PUBLIC_PATH)
                .isEqualTo(contract.at("/onlineJudge/publicPath").asText());
        assertThat(OnlineJudgeWebPaths.PUBLIC_API_PREFIX + "/")
                .isEqualTo(contract.at("/onlineJudge/apiPath").asText());
        assertThat(OnlineJudgeWebPaths.PUBLIC_ASSETS_PREFIX + "/")
                .isEqualTo(contract.at("/onlineJudge/assetsPath").asText());
        assertThat(OnlineJudgeWebPaths.LEGACY_APP_PREFIX + "/")
                .isEqualTo(contract.at("/compatibility/legacyOnlineJudgePath").asText());
    }

    @Test
    void frontendUsesTheContractAndRouterBasenameInsteadOfEmbeddingThePublicPrefix() throws IOException {
        String viteConfig = Files.readString(Path.of("frontend", "vite.config.mjs"));
        String packageJson = Files.readString(Path.of("frontend", "package.json"));
        String main = Files.readString(Path.of("frontend", "src", "main.tsx"));

        assertThat(viteConfig)
                .contains("config/route-ownership.json", "base: publicPath", "[publicApiPrefix]")
                .doesNotContain("base: \"/code/\"");
        assertThat(packageJson)
                .contains("vite.config.mjs --configLoader native")
                .doesNotContain("vite.config.ts");
        assertThat(main).contains("BrowserRouter basename={routerBasename}");
        assertThat(Path.of("frontend", "vite.config.ts")).doesNotExist();

        try (var sourceFiles = Files.walk(Path.of("frontend", "src"))) {
            for (Path sourceFile : sourceFiles.filter(this::isTypeScriptSource).toList()) {
                assertThat(Files.readString(sourceFile))
                        .as("public prefix must be supplied by BrowserRouter basename: %s", sourceFile)
                        .doesNotContain("\"/code", "'/code", "`/code");
            }
        }
    }

    @Test
    void deploymentReadsRoutesAndReservedNamespacesFromTheContract() throws IOException {
        String deploy = Files.readString(Path.of("scripts", "deploy-online-judge.sh"));

        assertThat(deploy).contains(
                "config/route-ownership.json",
                "jq -er '.productionHost'",
                "jq -er '.onlineJudge.publicPath'",
                "jq -er '.onlineJudge.apiPath'",
                "jq -er '.onlineJudge.assetsPath'",
                "jq -er '.platform.reservedPaths[]'",
                "x-proxy-source: ${PROXY_MARKER}"
        );
        assertThat(deploy).doesNotContain(
                "PUBLIC_PATH=\"${OJ_PUBLIC_PATH:-/code/}\"",
                "for platform_path in /app/ /download/"
        );

        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(dockerfile)
                .contains("COPY config/route-ownership.json /workspace/config/route-ownership.json")
                .contains("/workspace/src/main/resources/static src/main/resources/static")
                .doesNotContain("static/code src/main/resources/static/code");
    }

    private JsonNode readContract() throws IOException {
        return objectMapper.readTree(Files.readString(CONTRACT_PATH));
    }

    private boolean isTypeScriptSource(Path path) {
        String name = path.getFileName().toString();
        return Files.isRegularFile(path) && (name.endsWith(".ts") || name.endsWith(".tsx"));
    }
}
