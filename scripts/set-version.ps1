param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+([.-][0-9A-Za-z.-]+)?$')][string]$VersionName,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2100000000)][int]$VersionCode
)

$versionFile = Join-Path (Split-Path -Parent $PSScriptRoot) 'version.properties'
$content = "VERSION_NAME=$VersionName`nVERSION_CODE=$VersionCode`n"
[IO.File]::WriteAllText($versionFile, $content, [Text.UTF8Encoding]::new($false))
Write-Host "Version updated: $VersionName ($VersionCode)"
