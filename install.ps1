<#
.SYNOPSIS
  Installs the built plugin into DBeaver: copies it to plugins\ and registers it in bundles.info.
  DBeaver must be closed. (DBeaver does not start the p2 dropins scanner, so dropins\ is not used.)
  A DBeaver upgrade rewrites bundles.info; run this script again afterwards.
.EXAMPLE
  .\install.ps1
  .\install.ps1 -Uninstall
#>
param(
    [string]$DBeaverHome = "$env:LOCALAPPDATA\DBeaver",
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$bundleId = 'io.github.rmraja.dbeaver.entraid'
$plugins = Join-Path $DBeaverHome 'plugins'
$bundlesInfo = Join-Path $DBeaverHome 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info'
if (-not (Test-Path $bundlesInfo)) { throw "Not found: $bundlesInfo (use -DBeaverHome)" }

if (Get-Process dbeaver -ErrorAction SilentlyContinue) {
    throw "DBeaver is running. Close it first."
}

# Remove any previous version (plugins\, old dropins\ installs, bundles.info entry)
Get-ChildItem $plugins, (Join-Path $DBeaverHome 'dropins') -Filter "${bundleId}_*.jar" -ErrorAction SilentlyContinue |
    Remove-Item -Force
$backup = "$bundlesInfo.bak"
if (-not (Test-Path $backup)) { Copy-Item $bundlesInfo $backup }
$lines = Get-Content $bundlesInfo | Where-Object { -not $_.StartsWith("$bundleId,") }

if (-not $Uninstall) {
    $jar = Get-ChildItem dist -Filter "${bundleId}_*.jar" | Select-Object -First 1
    if (-not $jar) { throw "No plugin jar in dist\ - run .\build.ps1 first" }
    $version = $jar.BaseName.Substring($bundleId.Length + 1)
    Copy-Item $jar.FullName $plugins
    $lines += "$bundleId,$version,plugins/$($jar.Name),4,true"
}

# bundles.info must stay ASCII/UTF-8 without BOM
[IO.File]::WriteAllLines($bundlesInfo, [string[]]$lines, (New-Object Text.UTF8Encoding $false))

if ($Uninstall) {
    Write-Host "Removed $bundleId from DBeaver."
} else {
    Write-Host "Installed $($jar.Name) into $plugins and registered it in bundles.info"
}
Write-Host "Start DBeaver once with: `"$DBeaverHome\dbeaver.exe`" -clean"
