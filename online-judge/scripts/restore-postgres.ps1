param(
    [Parameter(Mandatory = $true)]
    [string]$InputFile,
    [switch]$ConfirmRestore
)

$ErrorActionPreference = "Stop"
if (-not $ConfirmRestore) {
    throw "Restore replaces data in the configured database. Re-run with -ConfirmRestore after verifying the target."
}
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ResolvedInput = (Resolve-Path -LiteralPath $InputFile).Path
if (-not $ResolvedInput.StartsWith($RepoRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "InputFile must stay inside the repository: $ResolvedInput"
}
$Database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "onlinejudge" }
$DatabaseUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "onlinejudge" }
$Arguments = @("compose", "exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1", "-U", $DatabaseUser, $Database)
$Process = Start-Process -FilePath "docker" -ArgumentList $Arguments -NoNewWindow -Wait -PassThru -RedirectStandardInput $ResolvedInput
if ($Process.ExitCode -ne 0) {
    throw "Postgres restore failed."
}
Write-Host "Postgres restore completed from $ResolvedInput"
