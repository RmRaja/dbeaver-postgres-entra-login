<#
.SYNOPSIS
  Builds the DBeaver Entra ID plugin jar against the installed DBeaver's plugins.
.EXAMPLE
  .\build.ps1
  .\build.ps1 -DBeaverHome "C:\Program Files\DBeaver"
#>
param(
    [string]$DBeaverHome = "$env:LOCALAPPDATA\DBeaver"
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$plugins = Join-Path $DBeaverHome 'plugins'
if (-not (Test-Path $plugins)) { throw "DBeaver plugins folder not found: $plugins (use -DBeaverHome)" }

$version = ((Get-Content META-INF\MANIFEST.MF | Where-Object { $_ -match '^Bundle-Version:' }) -split ':\s*')[1].Trim()
$jarName = "io.github.rmraja.dbeaver.entraid_$version.jar"

Remove-Item -Recurse -Force build, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory build\classes, build\tools, dist | Out-Null

$sources = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName }
& javac -source 21 -target 21 -encoding UTF-8 -nowarn -d build\classes -cp "$plugins\*" $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item plugin.xml build\classes\
& jar cfm "dist\$jarName" META-INF\MANIFEST.MF -C build\classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

# Standalone token test tool (not part of the plugin)
& javac -source 21 -target 21 -encoding UTF-8 -nowarn -d build\tools -cp build\classes tools\TokenCli.java
if ($LASTEXITCODE -ne 0) { throw "javac (tools) failed" }

Write-Host "Built dist\$jarName"
