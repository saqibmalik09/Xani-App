# Pulls the dashboard brand (Settings -> Branding) into the next APK build:
# the home-screen label and the launcher icon. Android reads both from the
# installed APK, so they can only follow the dashboard on a rebuild; every
# in-app surface follows it live through /v1/config.
#
# Writes app/brand/brand.properties (APP_NAME) and
# app/brand/res/drawable/ic_launcher_foreground.png. Never fails the build:
# on any error the previous sync (or the shipped icon and name) stays.
param([string]$ApiBase = $env:API_BASE_URL)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root 'app\brand'
$resDir = Join-Path $out 'res\drawable'
$icon = Join-Path $resDir 'ic_launcher_foreground.png'
$valuesDir = Join-Path $out 'res\values'
$bgFile = Join-Path $valuesDir 'brand_launcher.xml'

function Read-GradleProp([string]$key) {
    $file = Join-Path $root 'gradle.properties'
    if (-not (Test-Path $file)) { return '' }
    foreach ($line in Get-Content $file) {
        if ($line -match "^\s*$key\s*=\s*(.+?)\s*$") { return $Matches[1] }
    }
    return ''
}

try {
    if (-not $ApiBase) { $ApiBase = Read-GradleProp 'API_BASE_URL' }
    if (-not $ApiBase) { Write-Host 'sync-brand: no API_BASE_URL, keeping current brand'; exit 0 }
    $ApiBase = $ApiBase.TrimEnd('/')

    # Every endpoint answers inside a {statusCode, message, data} envelope.
    $cfg = (Invoke-RestMethod -Uri "$ApiBase/v1/config" -TimeoutSec 10).data
    New-Item -ItemType Directory -Force $out | Out-Null

    # What the app itself will read at launch. Printed so a blank tagline or a
    # missing default profile picture is visible here, not only on a phone.
    Write-Host ("sync-brand: dashboard tagline '{0}', default profile {1}, logo {2}" -f
        "$($cfg.tagline)".Trim(),
        $(if ("$($cfg.default_avatar_key)".Trim()) { 'set' } else { 'not set' }),
        $(if ("$($cfg.logo_key)".Trim()) { 'set' } else { 'not set' }))

    $name = "$($cfg.app_name)".Trim()
    if ($name) {
        # Properties escaping: backslash first, then the separators.
        $esc = $name.Replace('\', '\\').Replace('=', '\=').Replace(':', '\:')
        [IO.File]::WriteAllText((Join-Path $out 'brand.properties'), "APP_NAME=$esc`n", [Text.UTF8Encoding]::new($false))
        Write-Host "sync-brand: home-screen name '$name'"
    } else {
        Write-Host 'sync-brand: dashboard app name is empty, keeping gradle.properties APP_NAME'
    }

    # The logo is the launcher icon; a favicon is the fallback so a deployment
    # that has only ever uploaded one still gets its own icon.
    $key = "$($cfg.logo_key)".Trim()
    $source = 'logo'
    if (-not $key) { $key = "$($cfg.favicon_key)".Trim(); $source = 'favicon' }
    $media = "$($cfg.media_base_url)".TrimEnd('/')
    if (-not $key -or -not $media) {
        # Nothing uploaded on the dashboard: fall back to the shipped icon.
        if (Test-Path $icon) { Remove-Item $icon -Force }
        if (Test-Path $bgFile) { Remove-Item $bgFile -Force }
        Write-Host 'sync-brand: no dashboard logo or favicon, keeping the shipped launcher icon'
        exit 0
    }

    $tmp = Join-Path $env:TEMP "brand-logo-$PID"
    Invoke-WebRequest -Uri "$media/$key" -OutFile $tmp -TimeoutSec 20 -UseBasicParsing

    # Adaptive icons mask a 108dp layer down to its centre 72dp circle/squircle,
    # so the logo is drawn at 66% on a transparent 432px canvas to stay whole.
    Add-Type -AssemblyName System.Drawing
    $src = [Drawing.Image]::FromFile($tmp)
    $dim = "$($src.Width)x$($src.Height)"
    try {
        $edge = 432
        $box = [int]($edge * 0.66)
        $scale = [Math]::Min($box / $src.Width, $box / $src.Height)
        $w = [int]($src.Width * $scale); $h = [int]($src.Height * $scale)
        $bmp = New-Object Drawing.Bitmap $edge, $edge, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.Clear([Drawing.Color]::Transparent)
        $g.DrawImage($src, [int](($edge - $w) / 2), [int](($edge - $h) / 2), $w, $h)
        $g.Dispose()
        New-Item -ItemType Directory -Force $resDir | Out-Null
        $bmp.Save($icon, [Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()

        # A logo on a solid plate (most uploads) has to sit on a plate of the
        # same colour, or the launcher's circular mask shows the app's dark
        # background in the corners around it. A transparent logo keeps the
        # shipped colour.
        $corner = ([Drawing.Bitmap]$src).GetPixel(1, 1)
        New-Item -ItemType Directory -Force $valuesDir | Out-Null
        if ($corner.A -gt 250) {
            $hex = '{0:X2}{1:X2}{2:X2}' -f $corner.R, $corner.G, $corner.B
            $xml = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<resources>`n" +
                "    <color name=`"brand_launcher_background`">#$hex</color>`n</resources>`n"
            [IO.File]::WriteAllText($bgFile, $xml, [Text.UTF8Encoding]::new($false))
            Write-Host "sync-brand: icon plate #$hex, taken from the logo"
        } elseif (Test-Path $bgFile) {
            Remove-Item $bgFile -Force
        }
    } finally {
        $src.Dispose()
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
    Write-Host "sync-brand: launcher icon from the dashboard $source ($dim source)"
} catch {
    Write-Host "sync-brand: skipped ($($_.Exception.Message)); keeping current brand"
}
exit 0
