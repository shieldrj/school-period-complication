<#
.SYNOPSIS
    Builds the school-period complication and installs it on Robert's Galaxy Watch 7.

.DESCRIPTION
    The watch has no stable address. Its IP, its randomized Wi-Fi MAC and its wireless-debugging
    port all change - the port is reassigned every time adbd restarts, which includes any Wi-Fi
    reconnect. The serial is the only fixed identifier, so this script always rediscovers the
    watch by mDNS rather than trusting a remembered ip:port.

    It also refuses to install on anything that is not a watch. An earlier auto-install rule in
    the sibling watch-face repo only checked that *a* device was connected, and quietly pushed
    build after build to the paired phone, where a watch face is inert.

.PARAMETER SkipBuild
    Install the APK already in app\build\outputs\apk\debug without rebuilding it.

.PARAMETER SkipTests
    Build without running the unit tests first. Off by default on purpose: the tests are the
    only gate this repo has, since it has no CI.
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

# The watch's serial. Everything else about its address is temporary.
$WatchSerialFragment = 'RFAX60DJKDZ'
$ApkPath = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
# A hint for when the watch is dozing and not advertising. Gitignored: it is machine-local and
# goes stale constantly, so it must never be mistaken for a fact about the watch.
$AddressCachePath = Join-Path $PSScriptRoot '.watch-address'
$PackageName = 'com.shieldrj.schoolperiod.debug'   # debug builds carry the .debug suffix

function Write-Step($message) { Write-Host "==> $message" -ForegroundColor Cyan }
function Write-Ok($message)   { Write-Host "    $message" -ForegroundColor Green }
function Write-Warn($message) { Write-Host "    $message" -ForegroundColor Yellow }

# --- 1. Build ---------------------------------------------------------------------------------

if (-not $SkipBuild) {
    $gradlew = Join-Path $PSScriptRoot 'gradlew.bat'
    # Built by appending, not as "$tasks = if (...) { @('one') } else { @('a','b') }". PowerShell
    # unwraps a single-element array returned from an if, so the -SkipTests branch became a bare
    # string, and splatting a string passes its characters one at a time: gradle was handed ":".
    $tasks = @()
    if (-not $SkipTests) { $tasks += ':app:testDebugUnitTest' }
    $tasks += ':app:assembleDebug'

    Write-Step "Building ($($tasks -join ', '))"
    & $gradlew @tasks --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed. Nothing was installed - the watch keeps the version it already has."
    }
    Write-Ok 'Build succeeded.'
}

if (-not (Test-Path $ApkPath)) {
    throw "No APK at $ApkPath. Run without -SkipBuild."
}

# --- 2. Find the watch ------------------------------------------------------------------------

function Get-AttachedTransports {
    (& adb devices) |
        Where-Object { $_ -match '\sdevice$' } |
        ForEach-Object { ($_ -split '\s+')[0] }
}

function Get-ConnectedWatchSerial {
    # Ask each attached device what it is, rather than reading the adb transport name.
    # The transport name is NOT a reliable identifier: a device found over mDNS is listed as
    # "adb-RFAX60DJKDZ-vuXso4._adb-tls-connect._tcp", but the very same watch reached through
    # "adb connect <ip>:<port>" is listed as "192.168.1.128:35417", with the serial nowhere in
    # sight. Matching on the name silently failed to find a watch that was plainly attached.
    foreach ($transport in Get-AttachedTransports) {
        $deviceSerial = (& adb -s $transport shell getprop ro.serialno 2>$null) -join ''
        if ($deviceSerial -match $WatchSerialFragment) { return $transport }
    }
    return $null
}

function Find-WatchAddress {
    # mDNS records can be stale (right serial, dead port), so the caller retries.
    $line = (& adb mdns services) |
        Where-Object { $_ -match $WatchSerialFragment -and $_ -match '_adb-tls-connect' } |
        Select-Object -First 1
    if (-not $line) { return $null }
    if ($line -match '(\d{1,3}(?:\.\d{1,3}){3}:\d+)') { return $Matches[1] }
    return $null
}

function Try-Connect($address) {
    if (-not $address) { return $null }
    & adb connect $address 2>&1 | Out-Null
    Start-Sleep -Milliseconds 1200
    return Get-ConnectedWatchSerial
}

Write-Step 'Looking for the watch'
$serial = Get-ConnectedWatchSerial

# The watch stops advertising over mDNS while it dozes, which it does within seconds of the
# screen going off. The last address that worked is therefore worth one try before giving up -
# as a hint only, never as a stored truth: it is proved by connecting, and the device is still
# made to identify itself afterwards.
if (-not $serial -and (Test-Path $AddressCachePath)) {
    # Trim() does not strip a byte-order mark, and a BOM in front of the IP makes adb fail with
    # an address that looks perfectly correct on screen. Strip it explicitly.
    $cached = (Get-Content $AddressCachePath -Raw).Trim().TrimStart([char]0xFEFF)
    Write-Warn "Not advertising; trying the last address that worked ($cached)."
    $serial = Try-Connect $cached
}

if (-not $serial) {
    foreach ($attempt in 1..8) {
        $address = Find-WatchAddress
        if ($address) {
            $serial = Try-Connect $address
            if ($serial) { break }
            Write-Warn "Attempt ${attempt}: $address did not accept the connection."
        } else {
            Write-Warn "Attempt ${attempt}: the watch is not advertising yet."
        }
        Start-Sleep -Seconds 3
    }
}

if (-not $serial) {
    Write-Host ''
    Write-Host 'Could not reach the watch. Nothing was installed.' -ForegroundColor Red
    Write-Host 'Wake the watch and try again - it stops advertising itself while it dozes.'
    Write-Host 'Otherwise, on the watch: Settings > Developer options > Wireless debugging must be ON.'
    Write-Host 'If a port is open but the connection is refused, the pairing was cleared.'
    Write-Host 'Tap "Pair new device" on the watch and run:  adb pair <ip>:<port> <6-digit code>'
    exit 1
}

Write-Ok "Connected: $serial"
if ($serial -match '^\d{1,3}(?:\.\d{1,3}){3}:\d+$') {
    # ascii, not utf8: Windows PowerShell 5.1 writes utf8 *with* a BOM, and an ip:port needs
    # no more than ascii anyway.
    Set-Content -Path $AddressCachePath -Value $serial -Encoding ascii
}

# --- 3. Refuse anything that is not a watch ---------------------------------------------------

$characteristics = (& adb -s $serial shell getprop ro.build.characteristics) -join ''
if ($characteristics -notmatch 'watch') {
    throw "$serial reports characteristics '$characteristics', which is not a watch. Refusing to install."
}
$model = (& adb -s $serial shell getprop ro.product.model) -join ''
Write-Ok "Confirmed a watch: $model"

# --- 4. Install -------------------------------------------------------------------------------

Write-Step 'Installing'
$installOutput = & adb -s $serial install -r $ApkPath 2>&1
$installText = $installOutput -join "`n"
Write-Host $installText

if ($installText -notmatch 'Success') {
    throw 'Install failed. The watch keeps the version it already has.'
}

# The installed version, read back from the watch rather than assumed from the build.
$versionLine = (& adb -s $serial shell dumpsys package $PackageName) |
    Where-Object { $_ -match 'versionName=' } |
    Select-Object -First 1
Write-Ok "Installed $PackageName - $(($versionLine -replace '^\s+', ''))"

# --- 5. Refresh the complication so the new build is showing now -------------------------------

# Without this the watch face keeps rendering data the old build published until the next bell.
& adb -s $serial shell am broadcast `
    -a com.shieldrj.schoolperiod.action.REFRESH_COMPLICATION `
    -n "$PackageName/com.shieldrj.schoolperiod.complication.ComplicationRefreshReceiver" | Out-Null

Write-Ok 'Asked the complication to refresh.'
Write-Host ''
Write-Host 'Done. The direct-boot fix only shows itself on the next reboot of the watch.' -ForegroundColor Green
