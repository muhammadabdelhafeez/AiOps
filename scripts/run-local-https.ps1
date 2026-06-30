<#
.SYNOPSIS
Starts the KFH AIOps Command Center over HTTPS using a local PFX certificate.

.DESCRIPTION
Prompts for the PFX password without echoing it, sets Spring Boot SSL environment
variables for this process, and starts the app over HTTPS. By default this uses
PostgreSQL/JDBC, Flyway, and IdentityJdbcRepository so login user create/update/toggle/delete
persist to identity.users.
Do not hardcode certificate or database passwords in this script or source-controlled files.
#>

[CmdletBinding()]
param(
    [string] $DbUrl,
    [string] $DbUsername,
    [int] $ServerPort
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$certPath = Join-Path $projectRoot "src\main\resources\certs\UTVDISAP01_kfhtesting_local.pfx"

if (-not (Test-Path -LiteralPath $certPath)) {
    throw "PFX certificate was not found at: $certPath"
}

$securePassword = Read-Host "Enter PFX password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$scriptSetDbPassword = $false
$scriptSetSecretKey = $false
$defaultSecretKeyFile = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.kfh-aiops\secret-key.txt'
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $certUri = "file:" + ((Resolve-Path -LiteralPath $certPath).Path -replace "\\", "/")

    $env:SERVER_SSL_ENABLED = "true"
    $env:SERVER_SSL_KEY_STORE = $certUri
    $env:SERVER_SSL_KEY_STORE_PASSWORD = $plainPassword
    $env:SERVER_SSL_KEY_STORE_TYPE = "PKCS12"
    if ($ServerPort) { $env:SERVER_PORT = "$ServerPort" }

    Set-Location -Path $projectRoot
    Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    if (-not $DbUrl) { $DbUrl = $env:DB_URL }
    if (-not $DbUsername) { $DbUsername = $env:DB_USERNAME }
    if (-not $DbUrl) { $DbUrl = "jdbc:postgresql://localhost:5432/Kfh_AiOps" }
    if (-not $DbUsername) { $DbUsername = "postgres" }
    if (-not $env:DB_PASSWORD) {
        $secureDbPassword = Read-Host -Prompt "DB_PASSWORD for $DbUsername" -AsSecureString
        $dbPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureDbPassword)
        try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($dbPasswordPointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($dbPasswordPointer) }
        $scriptSetDbPassword = $true
    }
    $env:DB_URL = $DbUrl
    $env:DB_USERNAME = $DbUsername
    if (-not $env:KFH_BOOTSTRAP_ADMIN_COUNTRY) { $env:KFH_BOOTSTRAP_ADMIN_COUNTRY = "ALL" }
    if (-not $env:KFH_BOOTSTRAP_ADMIN_ENVIRONMENT) { $env:KFH_BOOTSTRAP_ADMIN_ENVIRONMENT = "PROD" }
    if (-not $env:KFH_BOOTSTRAP_ADMIN_ROLE) { $env:KFH_BOOTSTRAP_ADMIN_ROLE = "GLOBAL_ADMIN" }
    $secretKeyFileConfigured = -not [string]::IsNullOrWhiteSpace($env:KFH_AIOPS_SECRET_KEY_FILE)
    $defaultSecretKeyFilePresent = Test-Path -LiteralPath $defaultSecretKeyFile -PathType Leaf
    if ([string]::IsNullOrWhiteSpace($env:KFH_AIOPS_SECRET_KEY) -and -not $secretKeyFileConfigured -and -not $defaultSecretKeyFilePresent) {
        Write-Host "KFH_AIOPS_SECRET_KEY is required to encrypt and test connector secrets. Use the same value across restarts. You can also create %USERPROFILE%\.kfh-aiops\secret-key.txt or set KFH_AIOPS_SECRET_KEY_FILE." -ForegroundColor Yellow
        $secureSecretKey = Read-Host -Prompt "KFH_AIOPS_SECRET_KEY" -AsSecureString
        $secretKeyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecretKey)
        try { $env:KFH_AIOPS_SECRET_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretKeyPointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretKeyPointer) }
        if ([string]::IsNullOrWhiteSpace($env:KFH_AIOPS_SECRET_KEY)) {
            Remove-Item Env:KFH_AIOPS_SECRET_KEY -ErrorAction SilentlyContinue
            throw "KFH_AIOPS_SECRET_KEY cannot be blank because connector credentials are encrypted at rest."
        }
        $scriptSetSecretKey = $true
    }
    Write-Host "Starting KFH AIOps over HTTPS with database-backed identity storage." -ForegroundColor Green
    Write-Host ("DB_URL      = {0}" -f $DbUrl)
    Write-Host ("DB_USERNAME = {0}" -f $DbUsername)
    Write-Host "DB_PASSWORD = ***"
    Write-Host "KFH_AIOPS_SECRET_KEY = ***"
    if ([string]::IsNullOrWhiteSpace($env:KFH_AIOPS_SECRET_KEY)) {
        Write-Host "KFH_AIOPS_SECRET_KEY_FILE = configured/default deployment secret file"
    }
    Write-Host "Profiles    = https-local (local profile disabled)" -ForegroundColor Yellow
    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=https-local"
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    if ($scriptSetDbPassword) { Remove-Item Env:\DB_PASSWORD -ErrorAction SilentlyContinue }
    if ($scriptSetSecretKey) { Remove-Item Env:\KFH_AIOPS_SECRET_KEY -ErrorAction SilentlyContinue }
    Remove-Item Env:\SERVER_SSL_KEY_STORE_PASSWORD -ErrorAction SilentlyContinue
}

