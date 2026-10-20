param(
    [string]$BackupDirectory = "backups"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ResolvedBackupDirectory = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $BackupDirectory))
if (-not $ResolvedBackupDirectory.StartsWith($RepoRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "BackupDirectory must stay inside the repository: $ResolvedBackupDirectory"
}
New-Item -ItemType Directory -Path $ResolvedBackupDirectory -Force | Out-Null

$Database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "onlinejudge" }
$DatabaseUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "onlinejudge" }
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Output = Join-Path $ResolvedBackupDirectory "onlinejudge-$Stamp.sql"
$Arguments = @("compose", "exec", "-T", "postgres", "pg_dump", "--no-owner", "--no-privileges", "-U", $DatabaseUser, $Database)

$Process = Start-Process -FilePath "docker" -ArgumentList $Arguments -NoNewWindow -Wait -PassThru -RedirectStandardOutput $Output
if ($Process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $Output) -or (Get-Item -LiteralPath $Output).Length -eq 0) {
    Remove-Item -LiteralPath $Output -Force -ErrorAction SilentlyContinue
    throw "Postgres backup failed; startup/migration has been stopped."
}
Write-Host "Postgres backup saved to $Output"
