<#
    KFH AIOps Platform - datasource-backed run (PostgreSQL only)
    Starts the backend WITHOUT the local Spring profile so PostgreSQL/JDBC, Flyway,
    JPA, and IdentityJdbcRepository are wired. User Management create/update/toggle/
    delete will persist to identity.users.
#>
[CmdletBinding()]
param(
    [string] $DbUrl,
    [string] $DbUsername,
    [string] $BootstrapPassword,
    [string] $BootstrapUsername,
    [int]    $ServerPort,
    [switch] $UsePackagedWar
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -Path $projectRoot
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE = ''
if (-not $DbUrl)      { $DbUrl      = $env:DB_URL }
if (-not $DbUsername) { $DbUsername = $env:DB_USERNAME }
if (-not $DbUrl)      { $DbUrl      = 'jdbc:postgresql://localhost:5432/Kfh_AiOps' }
if (-not $DbUsername) { $DbUsername = 'postgres' }
if (-not $env:DB_PASSWORD) {
    $secure = Read-Host -Prompt "DB_PASSWORD for $DbUsername" -AsSecureString
    $bstr   = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try   { $env:DB_PASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}
$match = [regex]::Match($DbUrl, '^jdbc:postgresql://([^:/]+)(?::(\d+))?/.+$')
if (-not $match.Success) { throw "DB_URL is not a valid PostgreSQL JDBC URL: $DbUrl" }
$pgHost = $match.Groups[1].Value
$pgPort = if ($match.Groups[2].Success) { [int]$match.Groups[2].Value } else { 5432 }
Write-Host '--- KFH AIOps datasource-backed run ---' -ForegroundColor Cyan
Write-Host ("DB_URL          = {0}" -f $DbUrl)
Write-Host ("DB_USERNAME     = {0}" -f $DbUsername)
Write-Host  "DB_PASSWORD     = ***"
Write-Host  "SPRING_PROFILES = (none - local profile disabled)" -ForegroundColor Yellow
Write-Host ("Probing PostgreSQL at {0}:{1} ..." -f $pgHost, $pgPort) -ForegroundColor Cyan
$tcp = New-Object System.Net.Sockets.TcpClient
try {
    $async = $tcp.BeginConnect($pgHost, $pgPort, $null, $null)
    if (-not ($async.AsyncWaitHandle.WaitOne(3000, $false) -and $tcp.Connected)) {
        throw ("PostgreSQL TCP probe failed at {0}:{1}." -f $pgHost, $pgPort)
    }
    Write-Host "PostgreSQL TCP reachable." -ForegroundColor Green
} finally { $tcp.Close() }
$env:DB_URL      = $DbUrl
$env:DB_USERNAME = $DbUsername
if ($BootstrapUsername) { $env:KFH_BOOTSTRAP_ADMIN_USERNAME = $BootstrapUsername }
if ($BootstrapPassword) { $env:KFH_BOOTSTRAP_ADMIN_PASSWORD = $BootstrapPassword }
if ($ServerPort)        { $env:SERVER_PORT                   = "$ServerPort" }
if (-not $env:KFH_BOOTSTRAP_ADMIN_COUNTRY)     { $env:KFH_BOOTSTRAP_ADMIN_COUNTRY     = 'ALL' }
if (-not $env:KFH_BOOTSTRAP_ADMIN_ENVIRONMENT) { $env:KFH_BOOTSTRAP_ADMIN_ENVIRONMENT = 'PROD' }
if (-not $env:KFH_BOOTSTRAP_ADMIN_ROLE)        { $env:KFH_BOOTSTRAP_ADMIN_ROLE        = 'GLOBAL_ADMIN' }
try {
    if ($UsePackagedWar) {
        & .\mvnw.cmd -q -DskipTests package
        $war = Resolve-Path .\target\kfh-aiops-platform-0.0.1-SNAPSHOT.war
        & java -jar $war
    } else {
        & .\mvnw.cmd spring-boot:run
    }
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    if ($BootstrapPassword) { Remove-Item Env:KFH_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue }
}
