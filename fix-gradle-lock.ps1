# === AUTOMATIC FIX FOR R.JAR LOCK ===
Write-Host "=== Searching for locked files ===" -ForegroundColor Cyan

# 1. Download Handle.exe if not present
$handlePath = "$env:TEMP\handle"
if (!(Test-Path "$handlePath\handle64.exe")) {
    Write-Host "Downloading Handle.exe..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri "https://download.sysinternals.com/files/Handle.zip" -OutFile "$env:TEMP\handle.zip"
    Expand-Archive -Path "$env:TEMP\handle.zip" -DestinationPath $handlePath -Force
    reg add "HKCU\Software\Sysinternals\Handle" /v EulaAccepted /t REG_DWORD /d 1 /f | Out-Null
}

# 2. Find processes locking R.jar
$locks = & "$handlePath\handle64.exe" -a 2>$null | Select-String -Pattern "R\.jar|classes\.jar" -Context 1,0

if ($locks) {
    Write-Host "Found locking processes:" -ForegroundColor Red
    
    # Extract unique PIDs
    $pids = @()
    $locks | ForEach-Object {
        if ($_ -match "java.exe\s+pid:\s+(\d+)\s+") {
            $pids += $matches[1]
        }
    }
    
    $pids = $pids | Select-Object -Unique
    
    foreach ($pid in $pids) {
        Write-Host "  - Killing Java process PID: $pid" -ForegroundColor Yellow
        taskkill /F /PID $pid 2>$null
    }
} else {
    Write-Host "No locking processes found" -ForegroundColor Green
}

# 3. Stop Gradle daemon
Write-Host "`n=== Stopping Gradle Daemon ===" -ForegroundColor Cyan
& .\gradlew --stop 2>$null

# 4. Force kill all Java processes
Write-Host "`n=== Killing all Java processes ===" -ForegroundColor Cyan
Get-Process | Where-Object {$_.ProcessName -like "*java*"} | ForEach-Object {
    Write-Host "  - Killing: $($_.ProcessName) (PID: $($_.Id))" -ForegroundColor Yellow
    Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 2

# 5. Clean build folders
Write-Host "`n=== Cleaning build directories ===" -ForegroundColor Cyan
$buildDirs = @("app\build", ".gradle")
foreach ($dir in $buildDirs) {
    if (Test-Path $dir) {
        Write-Host "  - Deleting: $dir" -ForegroundColor Yellow
        Remove-Item -Recurse -Force $dir -ErrorAction SilentlyContinue
    }
}

Write-Host "`n=== DONE! ===" -ForegroundColor Green
Write-Host "Now run the build again:" -ForegroundColor White
Write-Host "  .\gradlew clean" -ForegroundColor Cyan
Write-Host "  .\gradlew assembleDebug" -ForegroundColor Cyan