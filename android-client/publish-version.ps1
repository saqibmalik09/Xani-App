# Registers the version just built in the dashboard's App versions table, so an
# administrator can turn that exact build on or off. A build with no row there
# is never gated — the switch the release is supposed to get would not exist.
#
# Credentials come from the environment and are never written anywhere:
#   $env:XANI_ADMIN_USER  email, username or phone of an account with
#                         app.manage_versions (admin / administrator)
#   $env:XANI_ADMIN_PASS  its password
# Without them this only prints what to add by hand. Never fails the build.
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$ApiBase = $env:API_BASE_URL,
    [string]$Apk = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$defaultMessage = 'A new version is available. Please contact us to update.'

function Read-GradleProp([string]$key) {
    $file = Join-Path $root 'gradle.properties'
    if (-not (Test-Path $file)) { return '' }
    foreach ($line in Get-Content $file) {
        if ($line -match "^\s*$key\s*=\s*(.+?)\s*$") { return $Matches[1] }
    }
    return ''
}

try {
    if ($Apk -and (Test-Path $Apk)) {
        $mb = [Math]::Round((Get-Item $Apk).Length / 1MB, 1)
        Write-Host "publish-version: $Apk ($mb MB), reports version $Version"
    }

    if (-not $ApiBase) { $ApiBase = Read-GradleProp 'API_BASE_URL' }
    if (-not $ApiBase) {
        Write-Host 'publish-version: no API_BASE_URL, nothing registered'
        exit 0
    }
    $ApiBase = $ApiBase.TrimEnd('/')

    $user = "$env:XANI_ADMIN_USER".Trim()
    $pass = "$env:XANI_ADMIN_PASS"
    if (-not $user -or -not $pass) {
        Write-Host "publish-version: set XANI_ADMIN_USER and XANI_ADMIN_PASS to register automatically."
        Write-Host "publish-version: until then add '$Version' under App versions on the dashboard, enabled."
        exit 0
    }

    # Every endpoint answers inside a {statusCode, message, data} envelope.
    $login = Invoke-RestMethod -Method Post -Uri "$ApiBase/v1/auth/login" -TimeoutSec 20 `
        -ContentType 'application/json' `
        -Body (@{ identifier = $user; password = $pass } | ConvertTo-Json -Compress)
    $token = "$($login.data.tokens.access_token)".Trim()
    if (-not $token) {
        Write-Host 'publish-version: sign-in returned no token, nothing registered'
        exit 0
    }
    $auth = @{ Authorization = "Bearer $token" }

    $rows = @((Invoke-RestMethod -Uri "$ApiBase/v1/admin/app-versions" -Headers $auth -TimeoutSec 20).data.items)
    $existing = $rows | Where-Object { "$($_.version)".Trim() -eq $Version } | Select-Object -First 1

    # A new row inherits the contact already in use, so a build is never
    # released with a block card nobody can answer.
    $current = $rows | Where-Object { $_.is_default } | Select-Object -First 1
    $whatsapp = if ($existing) { "$($existing.whatsapp)" } elseif ($current) { "$($current.whatsapp)" } else { '' }
    $message = if ($existing -and "$($existing.message)".Trim()) { "$($existing.message)" } else { $defaultMessage }

    # Released enabled and default: this build is the current one and is allowed
    # to run. Turning it off later is the admin's call, from the dashboard.
    $body = @{
        version    = $Version
        enabled    = $true
        is_default = $true
        message    = $message
        whatsapp   = $whatsapp
    } | ConvertTo-Json -Compress

    if ($existing) {
        Invoke-RestMethod -Method Post -Uri "$ApiBase/v1/admin/app-versions/$($existing.id)" `
            -Headers $auth -TimeoutSec 20 -ContentType 'application/json' -Body $body | Out-Null
        Write-Host "publish-version: $Version updated on the dashboard (enabled, current)"
    } else {
        Invoke-RestMethod -Method Post -Uri "$ApiBase/v1/admin/app-versions" `
            -Headers $auth -TimeoutSec 20 -ContentType 'application/json' -Body $body | Out-Null
        Write-Host "publish-version: $Version added to the dashboard (enabled, current)"
    }
} catch {
    Write-Host "publish-version: skipped ($($_.Exception.Message))"
    Write-Host "publish-version: add '$Version' under App versions on the dashboard to be able to turn it off."
}
exit 0
