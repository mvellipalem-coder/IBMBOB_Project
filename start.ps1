# ============================================================
# EBookStore - One-click startup script for Windows 11
# Usage:  .\start.ps1
# ============================================================

$PROJECT_ROOT = $PSScriptRoot

function Write-Green  { param($m) Write-Host $m -ForegroundColor Green  }
function Write-Yellow { param($m) Write-Host $m -ForegroundColor Yellow }
function Write-Red    { param($m) Write-Host $m -ForegroundColor Red    }
function Write-Cyan   { param($m) Write-Host $m -ForegroundColor Cyan   }

Write-Cyan  "================================================"
Write-Cyan  "   EBookStore - Starting up..."
Write-Cyan  "================================================"

# ---- 1. Find Java 21 ----------------------------------------
Write-Yellow "`n[1/5] Looking for Java 21..."

$javaPaths = @(
    "C:\Program Files\Java",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Microsoft"
)

$javaHome = $null
foreach ($p in $javaPaths) {
    if (Test-Path $p) {
        $found = Get-ChildItem $p -ErrorAction SilentlyContinue |
                 Where-Object { $_.Name -match "jdk-21|jdk21" } |
                 Select-Object -First 1
        if ($found) { $javaHome = $found.FullName; break }
    }
}

if (-not $javaHome) {
    Write-Red "ERROR: Java 21 not found. Install from https://www.oracle.com/java/technologies/downloads/#java21"
    Read-Host "Press Enter to exit"
    exit 1
}

$env:JAVA_HOME = $javaHome
$env:Path      = "$env:JAVA_HOME\bin;" + $env:Path
Write-Green "   Java found: $javaHome"

# ---- 2. Check Maven -----------------------------------------
Write-Yellow "`n[2/5] Checking Maven..."
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
    Write-Red "ERROR: Maven not found. Install from https://maven.apache.org/download.cgi"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Green "   Maven found: $($mvnCmd.Source)"

# ---- 3. Check Node / npm ------------------------------------
Write-Yellow "`n[3/5] Checking Node.js..."
$npmCmd = Get-Command npm -ErrorAction SilentlyContinue
if (-not $npmCmd) {
    Write-Red "ERROR: Node.js not found. Install from https://nodejs.org"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Green "   npm found: $($npmCmd.Source)"

# ---- 4. Set environment variables ---------------------------
Write-Yellow "`n[4/5] Setting environment variables..."

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:CORS_ALLOWED_ORIGIN    = "http://localhost:5173"
$env:DB_PASSWORD            = "changeme"
$env:JWT_SECRET             = [Convert]::ToBase64String(
    (1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]]
)

Write-Green "   DB_PASSWORD  : set"
Write-Green "   JWT_SECRET   : generated ($($env:JWT_SECRET.Length) chars)"
Write-Green "   Profile      : dev"

# ---- 5. npm install (first time only) -----------------------
Write-Yellow "`n[5/5] Checking frontend dependencies..."
$nodeModules = Join-Path $PROJECT_ROOT "frontend\node_modules"
if (-not (Test-Path $nodeModules)) {
    Write-Yellow "   Running npm install (one-time, ~30 seconds)..."
    Push-Location (Join-Path $PROJECT_ROOT "frontend")
    npm install --prefer-offline
    Pop-Location
    Write-Green "   npm install complete."
} else {
    Write-Green "   node_modules already present, skipping npm install."
}

# ---- Start backend in a new window --------------------------
Write-Cyan "`n================================================"
Write-Cyan "   Starting Backend (Spring Boot)..."
Write-Cyan "================================================"

$backendDir = Join-Path $PROJECT_ROOT "backend"
$jh         = $env:JAVA_HOME
$dbp        = $env:DB_PASSWORD
$jwts       = $env:JWT_SECRET

$backendScript = "
`$env:JAVA_HOME              = '$jh'
`$env:Path                   = `"$jh\bin;`" + `$env:Path
`$env:DB_PASSWORD            = '$dbp'
`$env:JWT_SECRET             = '$jwts'
`$env:SPRING_PROFILES_ACTIVE = 'dev'
`$env:CORS_ALLOWED_ORIGIN    = 'http://localhost:5173'
Set-Location '$backendDir'
Write-Host 'Starting Spring Boot...' -ForegroundColor Cyan
mvn spring-boot:run
Write-Host 'Backend stopped.' -ForegroundColor Red
Read-Host 'Press Enter to close'
"

$backendScriptFile = Join-Path $env:TEMP "ebookstore_backend.ps1"
Set-Content -Path $backendScriptFile -Value $backendScript -Encoding UTF8
Start-Process powershell -ArgumentList "-NoExit -ExecutionPolicy Bypass -File `"$backendScriptFile`""

# ---- Wait for backend ---------------------------------------
Write-Yellow "`nWaiting for backend on port 8080 (up to 3 minutes)..."

$ready    = $false
$attempts = 0
$maxWait  = 180

while ((-not $ready) -and ($attempts -lt $maxWait)) {
    Start-Sleep -Seconds 3
    $attempts += 3
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 3 -ErrorAction Stop
        if ($r.status -eq "UP") { $ready = $true }
    } catch {
        Write-Host "   [$attempts s] Waiting for Spring Boot..." -ForegroundColor DarkGray
    }
}

if (-not $ready) {
    Write-Red "`nBackend did not respond in $maxWait seconds."
    Write-Yellow "Check the backend window for errors."
    Write-Yellow "If it shows 'Application started', open http://localhost:5173 manually."
    Read-Host "Press Enter to exit launcher (backend keeps running)"
    exit 0
}

Write-Green "`nBackend is UP!"

# ---- Start frontend in a new window -------------------------
Write-Cyan "`n================================================"
Write-Cyan "   Starting Frontend (React / Vite)..."
Write-Cyan "================================================"

$frontendDir = Join-Path $PROJECT_ROOT "frontend"

$frontendScript = "
Set-Location '$frontendDir'
Write-Host 'Starting Vite...' -ForegroundColor Cyan
npm run dev
Write-Host 'Frontend stopped.' -ForegroundColor Red
Read-Host 'Press Enter to close'
"

$frontendScriptFile = Join-Path $env:TEMP "ebookstore_frontend.ps1"
Set-Content -Path $frontendScriptFile -Value $frontendScript -Encoding UTF8
Start-Process powershell -ArgumentList "-NoExit -ExecutionPolicy Bypass -File `"$frontendScriptFile`""

# ---- Wait for frontend then open browser --------------------
Write-Yellow "`nWaiting for frontend on port 5173..."

$fe       = $false
$attempts = 0

while ((-not $fe) -and ($attempts -lt 30)) {
    Start-Sleep -Seconds 2
    $attempts += 2
    try {
        $null = Invoke-WebRequest -Uri "http://localhost:5173" -TimeoutSec 2 -ErrorAction Stop -UseBasicParsing
        $fe = $true
    } catch {
        Write-Host "   [$attempts s] Waiting for Vite..." -ForegroundColor DarkGray
    }
}

Start-Process "http://localhost:5173"

Write-Cyan "`n================================================"
Write-Green "   EBookStore is running!"
Write-Host  "   React App  : http://localhost:5173"
Write-Host  "   Swagger UI : http://localhost:8080/swagger-ui.html"
Write-Host  "   Health     : http://localhost:8080/api/health"
Write-Yellow "   Close the Backend and Frontend windows to stop."
Write-Cyan  "================================================"
Read-Host "`nPress Enter to exit this launcher"
