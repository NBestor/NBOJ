$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$ImageName = if ($env:OJ_CPP17_DOCKER_IMAGE) { $env:OJ_CPP17_DOCKER_IMAGE } else { "wenzhong-oj-cpp17-runner:13" }
$BaseImage = if ($env:OJ_CPP17_BASE_IMAGE) { $env:OJ_CPP17_BASE_IMAGE } else { "gcc:13-bookworm" }

docker build --build-arg "CPP17_BASE_IMAGE=$BaseImage" -t $ImageName (Join-Path $RootDir "docker/cpp17-runner")

$WorkDir = Join-Path ([System.IO.Path]::GetTempPath()) ("oj-cpp17-runner-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $WorkDir | Out-Null

try {
    @"
#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    vector<int> values{1, 2, 3};
    cout << accumulate(values.begin(), values.end(), 0) << '\n';
    return 0;
}
"@ | Set-Content -Encoding UTF8 (Join-Path $WorkDir "solution.cpp")

    $Result = docker run --rm --network none --cpus 1 --memory 128m --pids-limit 64 `
        -v "${WorkDir}:/workspace" -w /workspace $ImageName `
        sh -lc "g++ -std=c++17 -O2 -pipe -o solution solution.cpp && ./solution"

    if (($Result | Out-String).Trim() -ne "6") {
        throw "C++17 runner smoke failed: expected 6, got $Result"
    }

    Write-Host "C++17 runner image is ready: $ImageName"
}
finally {
    Remove-Item -Recurse -Force $WorkDir -ErrorAction SilentlyContinue
}
