# AiBar 开发环境一键启动
# 用法：右键"使用 PowerShell 运行"，或在 PowerShell 里执行 .\start-dev.ps1

$ErrorActionPreference = 'Continue'
$root = "E:\Myproject\AiBar"

Write-Host "=== 1. 检查 Redis ===" -ForegroundColor Cyan
$redis = Get-NetTCPConnection -LocalPort 6379 -State Listen -ErrorAction SilentlyContinue
if ($redis) {
    Write-Host "  Redis 已在运行" -ForegroundColor Green
} else {
    Write-Host "  Redis 未启动，尝试启动..." -ForegroundColor Yellow
    $redisExe = "E:\Appstudy\Redis\Redis-x64-5.0.14.1\redis-server.exe"
    if (Test-Path $redisExe) {
        Start-Process -FilePath $redisExe -WorkingDirectory "E:\Appstudy\Redis\Redis-x64-5.0.14.1"
        Start-Sleep -Seconds 2
        Write-Host "  已启动 Redis" -ForegroundColor Green
    } else {
        Write-Host "  未找到 redis-server.exe，请手动启动" -ForegroundColor Red
    }
}

Write-Host "=== 2. 检查 MySQL ===" -ForegroundColor Cyan
$mysql = Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue
if ($mysql) { Write-Host "  MySQL 已在运行" -ForegroundColor Green }
else { Write-Host "  MySQL 未运行，请先启动（否则后端连不上库）" -ForegroundColor Red }

Write-Host "=== 3. 启动后端 (8080) ===" -ForegroundColor Cyan
# Spring Boot 3.2 需要 JDK 17+，这里用 IDEA 自带的 JBR
$jdk = "E:\IntelliJ IDEA 2024.1.4\jbr"
$mvn = "E:\Appstudy\apache-maven-3.8.6\bin\mvn.cmd"
$settings = "$env:TEMP\settings-aliyun.xml"

# 注意：PowerShell 会把 -Dspring-boot.run.profiles=local 拆开，用环境变量最稳
$backendCmd = "`$env:JAVA_HOME='$jdk'; `$env:PATH='$jdk\bin;' + `$env:PATH; `$env:SPRING_PROFILES_ACTIVE='local'; Set-Location '$root\ai-tavern'; & '$mvn' $(if (Test-Path $settings) { "-s '$settings' " })spring-boot:run"
Start-Process -FilePath "powershell.exe" -ArgumentList "-NoExit", "-Command", $backendCmd -WindowStyle Normal
Write-Host "  后端窗口已打开（首次启动约 30-60 秒）" -ForegroundColor Green

Start-Sleep -Seconds 3

Write-Host "=== 4. 启动用户端 (5173) ===" -ForegroundColor Cyan
Start-Process -FilePath "powershell.exe" -ArgumentList "-NoExit", "-Command", "`$env:BROWSER='none'; Set-Location '$root\ai-tavern-web'; & 'C:\Program Files\nodejs\npm.cmd' run dev" -WindowStyle Normal
Write-Host "  用户端窗口已打开" -ForegroundColor Green

Start-Sleep -Seconds 3

Write-Host "=== 5. 启动管理端 (5174) ===" -ForegroundColor Cyan
Start-Process -FilePath "powershell.exe" -ArgumentList "-NoExit", "-Command", "`$env:BROWSER='none'; Set-Location '$root\ai-tavern-admin'; & 'C:\Program Files\nodejs\npm.cmd' run dev" -WindowStyle Normal
Write-Host "  管理端窗口已打开" -ForegroundColor Green

Write-Host ""
Write-Host "=== 启动完成 ===" -ForegroundColor Cyan
Write-Host "  后端:   http://localhost:8080"
Write-Host "  用户端: http://localhost:5173"
Write-Host "  管理端: http://localhost:5174"
Write-Host ""
Write-Host "注意：三个窗口都开着才会一直运行，关掉窗口服务就停止。" -ForegroundColor Yellow
