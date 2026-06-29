<#
  Digital Signature - one-shot bootstrap & run script
  Usage:
    .\run.ps1                                  # interactive prompts for DB creds (defaults shown)
    .\run.ps1 -DbUser root -DbPassword 12345   # non-interactive, use these creds
    .\run.ps1 -Reinstall                       # force reinstall of backend/frontend deps
    .\run.ps1 -DbOnly                          # only create the DB (skip running the app)
    .\run.ps1 -NoBrowser                       # don't auto-open the browser
    .\run.ps1 -NonInteractive                  # never prompt, use defaults / params only
    .\run.ps1 -ForceKillPorts                  # auto-kill anything occupying 8080 / 5173

  How DB creds are applied (IMPORTANT):
    This script does NOT modify application-dev.yml. Instead it injects the
    creds into the backend process as Spring Boot env vars:
      SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, SPRING_DATASOURCE_URL
    Spring Boot picks these up and they override application-dev.yml at runtime.
    So if someone clones the repo and runs `mvn spring-boot:run` directly
    (without this script), the defaults baked into application-dev.yml apply.

  How .env is applied:
    If a .env file exists at the project root, every KEY=VALUE entry is
    injected as an env var into the backend process at launch. Used for:
      MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, MAIL_HOST, MAIL_PORT, MAIL_ENABLED
      JWT_SECRET, CORS_ALLOWED_ORIGINS, CA_*, PADES_*, etc.
    See .env.example for the supported keys. Do NOT commit your .env.

  What the script does:
    1. Verifies JDK 21, Maven, Node.js, MySQL are installed.
    2. Sets JAVA_HOME for THIS session (does not modify your system env).
    3. Starts the MySQL service if it's stopped.
    4. Creates the DB if missing, using the creds you provide.
    5. Installs Maven + npm dependencies if needed.
    6. Launches backend (with overridden creds) and frontend in two new windows.
    7. Optionally opens http://localhost:5173 in your default browser.
#>

[CmdletBinding()]
param(
  [string]$DbUser,
  [string]$DbPassword,
  [string]$DbName = 'digital_signature',
  [string]$DbHost = 'localhost',
  [int]$DbPort = 3306,
  [switch]$NonInteractive,
  [switch]$Reinstall,
  [switch]$DbOnly,
  [switch]$NoBrowser,
  [switch]$ForceKillPorts,
  [int]$BackendPort = 8080,
  [int]$FrontendPort = 5173
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendDir = Join-Path $ProjectRoot 'frontend'

function Write-Step($msg)    { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)      { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn2($msg)   { Write-Host "    [!]  $msg" -ForegroundColor Yellow }
function Write-Fail($msg)    { Write-Host "    [X]  $msg" -ForegroundColor Red }

# Parse a .env file into an ordered hashtable. Supports KEY=VALUE, ignores blank lines
# and lines starting with '#'. Strips surrounding single/double quotes if present.
function Read-DotEnv([string]$Path) {
  $vars = [ordered]@{}
  if (-not (Test-Path $Path)) { return $vars }
  foreach ($line in Get-Content -Path $Path) {
    $trim = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trim)) { continue }
    if ($trim.StartsWith('#')) { continue }
    $eq = $trim.IndexOf('=')
    if ($eq -lt 1) { continue }
    $key = $trim.Substring(0, $eq).Trim()
    $val = $trim.Substring($eq + 1).Trim()
    if (($val.StartsWith('"') -and $val.EndsWith('"')) -or
        ($val.StartsWith("'") -and $val.EndsWith("'"))) {
      $val = $val.Substring(1, $val.Length - 2)
    }
    $vars[$key] = $val
  }
  return $vars
}

function Read-WithDefault($label, $default, [switch]$Secret) {
  if ($NonInteractive) { return $default }
  $shown = if ($Secret) { '****' } else { $default }
  $raw = Read-Host "    $label [$shown]"
  if ([string]::IsNullOrWhiteSpace($raw)) { return $default }
  return $raw
}

# Returns the list of PIDs holding the given TCP port in LISTEN state (empty if free).
function Get-PortHolders([int]$Port) {
  try {
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) { return @() }
    return @($conns | Select-Object -ExpandProperty OwningProcess -Unique)
  } catch {
    # Fallback for older Windows: parse netstat
    $lines = netstat -ano | Select-String ":$Port\s.*LISTENING"
    $pids = @()
    foreach ($line in $lines) {
      $parts = ($line.ToString().Trim() -split '\s+')
      if ($parts.Length -ge 5) { $pids += [int]$parts[-1] }
    }
    return ($pids | Select-Object -Unique)
  }
}

# Ensures the given port is free. If occupied, prints holder, then kills
# (auto when -ForceKillPorts; otherwise prompts unless -NonInteractive).
# Returns $true if the port ends up free, $false otherwise.
function Free-Port([int]$Port, [string]$Label) {
  $holders = Get-PortHolders -Port $Port
  if (-not $holders -or $holders.Count -eq 0) {
    Write-Ok "Port $Port is free ($Label)"
    return $true
  }

  Write-Warn2 "Port $Port ($Label) is in use by:"
  foreach ($procId in $holders) {
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($proc) {
      Write-Host ("      PID {0,-6} {1}" -f $procId, $proc.ProcessName)
    } else {
      Write-Host ("      PID {0,-6} (process info unavailable)" -f $procId)
    }
  }

  $shouldKill = $false
  if ($ForceKillPorts) {
    $shouldKill = $true
    Write-Warn2 '-ForceKillPorts set, killing without prompt'
  } elseif ($NonInteractive) {
    Write-Fail "Port $Port occupied and running non-interactive without -ForceKillPorts. Aborting."
    return $false
  } else {
    $ans = Read-Host "    Kill these process(es) to free port $Port? [y/N]"
    if ($ans -match '^(y|yes)$') { $shouldKill = $true }
  }

  if (-not $shouldKill) {
    Write-Fail "Port $Port still occupied. Aborting."
    return $false
  }

  foreach ($procId in $holders) {
    try {
      Stop-Process -Id $procId -Force -ErrorAction Stop
      Write-Ok "Killed PID $procId"
    } catch {
      Write-Fail "Could not kill PID ${procId}: $($_.Exception.Message)"
    }
  }

  # Wait briefly for the OS to release the socket.
  for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Milliseconds 300
    if (-not (Get-PortHolders -Port $Port)) {
      Write-Ok "Port $Port now free"
      return $true
    }
  }
  Write-Fail "Port $Port did not become free after kill. Aborting."
  return $false
}

# ---------- 0a. Load .env (if present) for MAIL_*, JWT_SECRET, etc. ----------
Write-Step 'Loading .env (optional)'
$EnvFile = Join-Path $ProjectRoot '.env'
$DotEnv = Read-DotEnv -Path $EnvFile
if ($DotEnv.Count -gt 0) {
  $shown = ($DotEnv.Keys | Where-Object { $_ -notmatch 'PASSWORD|SECRET' }) -join ', '
  Write-Ok "Loaded .env (vars: $shown; passwords/secrets hidden)"
} else {
  Write-Warn2 '.env not found or empty - mail features will be disabled. Copy .env.example and fill in.'
}

# ---------- 0b. Gather DB credentials ----------
Write-Step 'Database credentials'
if (-not $DbUser)     { $DbUser     = Read-WithDefault 'MySQL user' 'root' }
if (-not $DbPassword) { $DbPassword = Read-WithDefault 'MySQL password' '12345' -Secret }
$DbName = Read-WithDefault 'Database name' $DbName
$DbHost = Read-WithDefault 'MySQL host'    $DbHost
$portRaw = Read-WithDefault 'MySQL port'   $DbPort
$DbPort = [int]$portRaw
Write-Ok ("Using {0}@{1}:{2}/{3}" -f $DbUser, $DbHost, $DbPort, $DbName)

# ---------- 1. JDK 21 ----------
Write-Step 'Locating JDK 21'

# Exact paths to try first (fast, no directory scan).
$jdkCandidates = @(
  "$env:JAVA_HOME",
  'C:\Program Files\Eclipse Adoptium\jdk-21.0.9',
  'C:\Program Files\Eclipse Adoptium\jdk-21'
)

# Parent directories to scan for jdk-21* / jdk21* subfolders.
# Covers Adoptium/Temurin, Microsoft, Amazon Corretto, Azul Zulu, BellSoft Liberica,
# IBM Semeru, Oracle, plus IntelliJ-managed JDKs and per-user installs.
$jdkSearchRoots = @(
  'C:\Program Files\Eclipse Adoptium',
  'C:\Program Files\Java',
  'C:\Program Files\Microsoft',
  'C:\Program Files\Amazon Corretto',
  'C:\Program Files\Zulu',
  'C:\Program Files\BellSoft',
  'C:\Program Files\Semeru',
  'D:\Program Files\Eclipse Adoptium',
  'D:\Program Files\Java',
  "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
  "$env:USERPROFILE\.jdks"
)

$jdk21 = $null
foreach ($candidate in $jdkCandidates) {
  if (-not $candidate) { continue }
  $releaseFile = Join-Path $candidate 'release'
  if (Test-Path $releaseFile) {
    $verLine = (Get-Content $releaseFile | Select-String '^JAVA_VERSION=') -replace '"', ''
    if ($verLine -match 'JAVA_VERSION=21') {
      $jdk21 = $candidate
      break
    }
  }
}
if (-not $jdk21) {
  foreach ($root in $jdkSearchRoots) {
    if (-not (Test-Path $root)) { continue }
    $found = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -match '^jdk-?21' }
    foreach ($dir in $found) {
      $releaseFile = Join-Path $dir.FullName 'release'
      if (Test-Path $releaseFile) {
        $verLine = (Get-Content $releaseFile | Select-String '^JAVA_VERSION=') -replace '"', ''
        if ($verLine -match 'JAVA_VERSION=21') {
          $jdk21 = $dir.FullName
          break
        }
      }
    }
    if ($jdk21) { break }
  }
}
if (-not $jdk21) {
  Write-Fail 'JDK 21 not found. Install from https://adoptium.net/temurin/releases/?version=21'
  exit 1
}
$env:JAVA_HOME = $jdk21
$env:Path = "$jdk21\bin;$env:Path"
Write-Ok "JAVA_HOME = $jdk21"

# ---------- 2. Maven ----------
Write-Step 'Checking Maven'
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
  Write-Fail 'Maven not found in PATH. Install Maven 3.8+ and re-run.'
  exit 1
}
Write-Ok "$($mvnCmd.Source)"

# ---------- 3. Node.js ----------
Write-Step 'Checking Node.js'
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if (-not $nodeCmd) {
  Write-Fail 'Node.js not found. Install Node.js 20+ and re-run.'
  exit 1
}
$nodeVer = (& node --version) -replace '^v', ''
Write-Ok "node $nodeVer"

# ---------- 4. MySQL service ----------
Write-Step 'Locating MySQL service'
$svc = Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $svc) {
  Write-Fail 'No MySQL Windows service found. Install MySQL Server and re-run.'
  exit 1
}
if ($svc.Status -ne 'Running') {
  Write-Warn2 "$($svc.Name) is $($svc.Status). Starting..."
  try {
    Start-Service $svc.Name
    Write-Ok "Started $($svc.Name)"
  } catch {
    Write-Fail "Failed to start $($svc.Name). Run PowerShell as Administrator and try again."
    exit 1
  }
} else {
  Write-Ok "$($svc.Name) is running"
}

# ---------- 5. Locate mysql.exe ----------
Write-Step 'Locating mysql.exe'
$mysqlExe = $null
$mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
if ($mysqlCmd) { $mysqlExe = $mysqlCmd.Source }
if (-not $mysqlExe) {
  $mysqlDirs = Get-ChildItem 'C:\Program Files\MySQL' -Directory -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -match 'MySQL Server' } | Sort-Object Name -Descending
  foreach ($d in $mysqlDirs) {
    $candidate = Join-Path $d.FullName 'bin\mysql.exe'
    if (Test-Path $candidate) { $mysqlExe = $candidate; break }
  }
}
if (-not $mysqlExe) {
  Write-Fail 'mysql.exe not found. Add MySQL Server\bin to PATH and re-run.'
  exit 1
}
Write-Ok "$mysqlExe"

# ---------- 6. Create database ----------
Write-Step "Ensuring database '$DbName' exists"
$sql = "CREATE DATABASE IF NOT EXISTS $DbName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
# Pass password via MYSQL_PWD env var (avoids 'password on command line' warning and command-line leak).
$env:MYSQL_PWD = $DbPassword
$prevErr = $ErrorActionPreference
$ErrorActionPreference = 'Continue'  # mysql.exe stderr (warnings) must not halt the script
try {
  $mysqlOut = & $mysqlExe -h $DbHost -P $DbPort -u $DbUser -e $sql 2>&1
  $mysqlExit = $LASTEXITCODE
} finally {
  $ErrorActionPreference = $prevErr
  Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
$mysqlOut | ForEach-Object { Write-Host "    $_" }
if ($mysqlExit -ne 0) {
  Write-Fail "Could not connect/create DB. Check user '$DbUser', password and host '${DbHost}:${DbPort}'."
  exit 1
}
Write-Ok 'Database ready'

if ($DbOnly) {
  Write-Host "`nDone (DB only). Exiting." -ForegroundColor Cyan
  exit 0
}

# ---------- 7. Backend deps ----------
Write-Step 'Backend dependencies'
$targetClasses = Join-Path $ProjectRoot 'target\classes'
if ($Reinstall -or -not (Test-Path $targetClasses)) {
  Push-Location $ProjectRoot
  try {
    & mvn -q clean install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'mvn install failed' }
    Write-Ok 'Maven build complete'
  } finally { Pop-Location }
} else {
  Write-Ok 'target\classes exists, skipping (use -Reinstall to force)'
}

# ---------- 8. Frontend deps ----------
Write-Step 'Frontend dependencies'
$nodeModules = Join-Path $FrontendDir 'node_modules'
if ($Reinstall -or -not (Test-Path $nodeModules)) {
  Push-Location $FrontendDir
  try {
    & npm install --legacy-peer-deps
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
    Write-Ok 'npm install complete'
  } finally { Pop-Location }
} else {
  Write-Ok 'node_modules exists, skipping (use -Reinstall to force)'
}

# ---------- 9. Free required ports ----------
Write-Step "Checking ports $BackendPort (backend) and $FrontendPort (frontend)"
if (-not (Free-Port -Port $BackendPort  -Label 'backend'))  { exit 1 }
if (-not (Free-Port -Port $FrontendPort -Label 'frontend')) { exit 1 }

# ---------- 10. Launch backend with overridden DB creds ----------
$jdbcUrl = "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"

Write-Step 'Launching backend (new window)'
# Build dynamic env-var assignment lines from the .env hashtable.
# DB creds from script params override anything in .env.
$dotEnvLines = ''
foreach ($key in $DotEnv.Keys) {
  if ($key -in @('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD')) {
    continue  # DB creds are owned by the script params
  }
  $escaped = ($DotEnv[$key] -replace "'", "''")  # escape single quotes for PS string literal
  $dotEnvLines += "`$env:$key = '$escaped';`r`n"
}

# Use a here-string with single-quoted vars so PS1 substitutes them BEFORE handing off to the new window.
$backendCmd = @"
`$Host.UI.RawUI.WindowTitle = 'digital-signature - backend';
`$env:JAVA_HOME = '$jdk21';
`$env:Path = '$jdk21\bin;' + `$env:Path;
$dotEnvLines# Spring Boot reads these and overrides application-dev.yml at runtime:
`$env:SPRING_DATASOURCE_URL      = '$jdbcUrl';
`$env:SPRING_DATASOURCE_USERNAME = '$DbUser';
`$env:SPRING_DATASOURCE_PASSWORD = '$DbPassword';
Set-Location '$ProjectRoot';
Write-Host 'Backend starting at http://localhost:8080 ...' -ForegroundColor Cyan;
Write-Host ('DB override: ' + `$env:SPRING_DATASOURCE_URL) -ForegroundColor DarkGray;
if (`$env:MAIL_USERNAME) { Write-Host ('Mail: ' + `$env:MAIL_USERNAME + ' via ' + `$env:MAIL_HOST) -ForegroundColor DarkGray; }
mvn spring-boot:run
"@
Start-Process powershell -ArgumentList '-NoExit', '-Command', $backendCmd | Out-Null
Write-Ok 'Backend window opened'

Write-Step 'Launching frontend (new window)'
$frontendCmd = @"
`$Host.UI.RawUI.WindowTitle = 'digital-signature - frontend';
Set-Location '$FrontendDir';
Write-Host 'Frontend starting at http://localhost:5173 ...' -ForegroundColor Cyan;
npm run dev
"@
Start-Process powershell -ArgumentList '-NoExit', '-Command', $frontendCmd | Out-Null
Write-Ok 'Frontend window opened'

# ---------- 11. Open browser ----------
$frontendUrl = "http://localhost:$FrontendPort"
if (-not $NoBrowser) {
  Write-Step "Waiting for frontend to come up (max 60s)..."
  $deadline = (Get-Date).AddSeconds(60)
  $up = $false
  while ((Get-Date) -lt $deadline) {
    try {
      $r = Invoke-WebRequest -Uri $frontendUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
      if ($r.StatusCode -eq 200) { $up = $true; break }
    } catch {
      Start-Sleep -Milliseconds 1500
    }
  }
  if ($up) {
    Start-Process $frontendUrl
    Write-Ok "Opened $frontendUrl"
  } else {
    Write-Warn2 "Frontend did not respond within 60s. Open $frontendUrl manually."
  }
}

Write-Host "`nAll set." -ForegroundColor Green
Write-Host "  Backend : http://localhost:$BackendPort"
Write-Host "  Swagger:  http://localhost:$BackendPort/swagger-ui.html"
Write-Host "  Frontend: $frontendUrl"
Write-Host "  DB      : $DbUser@${DbHost}:${DbPort}/$DbName"
Write-Host "  Close the two opened PowerShell windows to stop the services."
