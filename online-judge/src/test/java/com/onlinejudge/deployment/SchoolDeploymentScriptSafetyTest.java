package com.onlinejudge.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolDeploymentScriptSafetyTest {

    private static final Path SCRIPTS = Path.of("scripts");
    private static final List<String> DESTRUCTIVE_COMMANDS = List.of(
            "docker system prune",
            "docker volume prune",
            "docker compose down -v",
            "docker-compose down -v"
    );

    @Test
    void productionStartupUsesExistingImagesWithoutBuilding() throws IOException {
        String shell = read("start-school.sh");
        String powershell = read("start-school.ps1");

        assertThat(shell).contains("docker compose up --no-build -d");
        assertThat(powershell).contains("docker compose up --no-build -d");
        assertThat(shell).doesNotContain("docker compose up --build", "docker compose build");
        assertThat(powershell).doesNotContain("docker compose up --build", "docker compose build");
    }

    @Test
    void imageBuildRequiresExplicitConfirmationAndDoesNotStartContainers() throws IOException {
        String shell = read("build-school-images.sh");
        String powershell = read("build-school-images.ps1");

        assertThat(shell).contains("--confirm-build", "docker compose build app cpp17-runner");
        assertThat(powershell).contains("[switch]$ConfirmBuild", "docker compose build app cpp17-runner");
        assertThat(shell).doesNotContain("docker compose up");
        assertThat(powershell).doesNotContain("docker compose up");
    }

    @Test
    void githubDeploymentRequiresManualDispatchAndExplicitBuildConfirmation() throws IOException {
        String workflow = readRepoFile(".github", "workflows", "deploy-online-judge.yml");

        assertThat(workflow).contains("workflow_dispatch:", "\"deploy-online-judge --confirm-build\"");
        assertThat(workflow).doesNotContain("push:");
    }

    @Test
    void serverDeploymentEntryUsesGuardedBuildAndSafeStartupScripts() throws IOException {
        String deploy = read("deploy-online-judge.sh");

        assertThat(deploy).contains(
                "--confirm-build",
                "EXPECTED_SSH_COMMAND=\"deploy-online-judge --confirm-build\"",
                "${SSH_ORIGINAL_COMMAND:-}",
                "set -- \"--confirm-build\"",
                "git -C \"${REPO_ROOT}\" fetch origin main",
                "bash scripts/build-school-images.sh --confirm-build",
                "bash scripts/start-school.sh",
                "ROUTE_CONTRACT=\"${OJ_ROUTE_CONTRACT:-${APP_ROOT}/config/route-ownership.json}\"",
                "SCRIPT_SHA_BEFORE=",
                "SCRIPT_SHA_AFTER=",
                "OJ_DEPLOY_REFRESHED=true OJ_DEPLOY_LOCK_HELD=true",
                "PUBLIC_HOST=\"$(jq -er '.productionHost' \"${ROUTE_CONTRACT}\")\"",
                "PUBLIC_PATH=\"$(jq -er '.onlineJudge.publicPath' \"${ROUTE_CONTRACT}\")\"",
                "APP_START_TIMEOUT_SECONDS=\"${OJ_APP_START_TIMEOUT_SECONDS:-120}\"",
                "APP_START_DEADLINE=$((SECONDS + APP_START_TIMEOUT_SECONDS))",
                "CADDY_CONFIG=\"${OJ_CADDY_CONFIG:-/etc/caddy/Caddyfile}\"",
                "caddy validate --config \"${CADDY_CONFIG}\"",
                "http://127.0.0.1:${SERVER_PORT:-8081}${PUBLIC_PATH}",
                "http://127.0.0.1:${SERVER_PORT:-8081}${PUBLIC_API_PREFIX}/system/readiness",
                "--resolve \"${PUBLIC_HOST}:443:127.0.0.1\"",
                "\"https://${PUBLIC_HOST}${PUBLIC_API_PREFIX}/system/readiness\"",
                "\"https://${PUBLIC_HOST}${PUBLIC_PREFIX}/student\"",
                "jq -er '.platform.reservedPaths[]'",
                "x-proxy-source: ${PROXY_MARKER}"
        );
        assertThat(deploy.indexOf("--confirm-build"))
                .isLessThan(deploy.indexOf("git -C \"${REPO_ROOT}\" fetch origin main"));
        assertThat(deploy)
                .doesNotContain("docker compose up", "docker compose build", "code.tuotuzju.com", "nginx -t");
    }

    @Test
    void deploymentScriptsNeverDeleteDockerVolumesOrBroadRuntimeState() throws IOException {
        for (String scriptName : List.of(
                "start-school.sh",
                "start-school.ps1",
                "build-school-images.sh",
                "build-school-images.ps1",
                "deploy-online-judge.sh"
        )) {
            assertThat(read(scriptName))
                    .as(scriptName)
                    .doesNotContain(DESTRUCTIVE_COMMANDS.toArray(String[]::new));
        }
    }

    private String read(String scriptName) throws IOException {
        return Files.readString(SCRIPTS.resolve(scriptName));
    }

    private String readRepoFile(String... parts) throws IOException {
        Path fromProject = Path.of("..").resolve(Path.of("", parts));
        if (Files.exists(fromProject)) {
            return Files.readString(fromProject);
        }
        return Files.readString(Path.of("", parts));
    }
}
