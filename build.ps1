# CameraTrack build script
# Usage: ./build.ps1   (requires JDK 25 and Maven 3.9+; a bundled Maven under .tools is used if present)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$pom = Join-Path $root 'pom.xml'

# Prefer a Maven bundled under .tools (local development), otherwise use mvn from PATH.
$mvn = $null
$bundled = Get-ChildItem (Join-Path $root '.tools') -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($bundled) {
    $candidate = Join-Path $bundled.FullName 'bin\mvn.cmd'
    if (Test-Path $candidate) {
        $mvn = $candidate
    }
}
if (-not $mvn) {
    $mvn = 'mvn'
}

# Keep the dependency cache inside the project so no global ~/.m2 permissions are needed.
$repo = Join-Path $root '.m2repo'

& $mvn -f $pom -B -DskipTests "-Dmaven.repo.local=$repo" package
if ($LASTEXITCODE -ne 0) {
    throw "Build failed (exit $LASTEXITCODE)"
}

$jar = Get-ChildItem (Join-Path $root 'target') -Filter 'CameraTrack-*.jar' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
Write-Host ""
Write-Host "Build OK: $($jar.FullName)" -ForegroundColor Green
