# check-env.ps1
#
# Reports the real state of everything RecoverAI needs to boot, then tells you the
# single next command to run. Read-only: it does not install, create or change anything.
#
# Usage:
#   .\scripts\check-env.ps1
#   .\scripts\check-env.ps1 -DbPassword mysecret     # if your postgres password is not "postgres"
#
# Note: this file is deliberately ASCII-only. PowerShell 5.1 reads .ps1 files as ANSI
# unless they have a BOM, so fancy dashes and arrows come out as mojibake.

param(
    [string] $DbUser = "postgres",
    [string] $DbPassword = "postgres",
    [string] $DbHost = "localhost",
    [int]    $DbPort = 5432,
    [string] $DbName = "recoverai"
)

$ErrorActionPreference = "Continue"
$problems = New-Object System.Collections.ArrayList

function Say([string]$label, $ok, [string]$detail) {
    if ($ok -eq $true)       { $mark = "[ OK ]"; $color = "Green" }
    elseif ($ok -eq $false)  { $mark = "[FAIL]"; $color = "Red" }
    else                     { $mark = "[warn]"; $color = "Yellow" }

    Write-Host $mark -ForegroundColor $color -NoNewline
    Write-Host "  $label" -NoNewline
    if ($detail) { Write-Host "   $detail" -ForegroundColor DarkGray } else { Write-Host "" }
}

function Note([string]$text) { [void]$problems.Add($text) }

function Test-Port([string]$targetHost, [int]$port, [int]$timeoutMs = 1500) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($targetHost, $port, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne($timeoutMs, $false) -and $client.Connected) {
            $client.EndConnect($iar)
            return $true
        }
        return $false
    }
    catch { return $false }
    finally { $client.Close() }
}

Write-Host ""
Write-Host "RecoverAI environment check" -ForegroundColor Cyan
Write-Host "---------------------------" -ForegroundColor Cyan
Write-Host ""

# ---------------------------------------------------------------- Java
Write-Host "Java" -ForegroundColor White
$javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $javaExe) {
    Say "java on PATH" $false "not found"
    Note "Java is not on PATH. IntelliJ can still compile using its own JDK, so this is only a problem if you want to run mvn from a terminal."
}
else {
    # java -version writes to stderr, hence the redirect.
    $raw = (& java -version 2>&1) -join " "
    if ($raw -match 'version "(\d+)') {
        $major = [int]$Matches[1]
        if ($major -eq 21) {
            Say "java version" $true "$major"
        }
        else {
            Say "java version" $false "$major, but pom.xml targets 21"
            Note "Terminal java is $major and the project needs 21. Either install JDK 21 or just build inside IntelliJ, where the project SDK is what counts."
        }
    }
    else {
        Say "java version" $null "could not parse: $raw"
    }
}
if ($env:JAVA_HOME) { Say "JAVA_HOME" $true $env:JAVA_HOME }
else                { Say "JAVA_HOME" $null "not set (fine if you build in IntelliJ)" }
Write-Host ""

# ---------------------------------------------------------------- Maven
Write-Host "Maven" -ForegroundColor White
$mvnExe = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if ($mvnExe) {
    Say "mvn on PATH" $true $mvnExe
}
else {
    Say "mvn on PATH" $null "not found - use IntelliJ's bundled Maven instead"
    Note "No mvn on PATH and this repo has no Maven wrapper (no mvnw.cmd). Build from IntelliJ's Maven tool window rather than a terminal."
}
Write-Host ""

# ---------------------------------------------------------------- Postgres
Write-Host "Postgres" -ForegroundColor White

$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $found = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
             Sort-Object FullName -Descending | Select-Object -First 1
    if ($found) { $psql = $found.FullName }
}

if ($psql) { Say "psql binary" $true $psql }
else {
    Say "psql binary" $null "not found on PATH or in C:\Program Files\PostgreSQL"
    Note "psql was not found. Postgres may still be installed (or running in Docker) - the port check below is the real answer."
}

$services = @(Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*postgres*" })
if ($services.Count -gt 0) {
    foreach ($svc in $services) {
        $running = ($svc.Status -eq "Running")
        Say "service $($svc.Name)" $running "$($svc.Status)"
        if (-not $running) {
            Note "Service $($svc.Name) is $($svc.Status). Start it with:  Start-Service $($svc.Name)   (needs an admin PowerShell)"
        }
    }
}
else {
    Say "windows service" $null "no service matching *postgres*"
}

$portOpen = Test-Port $DbHost $DbPort
Say "port $DbPort open" $portOpen $(if ($portOpen) { "something is listening on ${DbHost}:${DbPort}" } else { "nothing listening" })
if (-not $portOpen) {
    Note "Nothing is listening on ${DbHost}:${DbPort}. Postgres is not running, so the backend cannot start. Everything below is skipped."
}
Write-Host ""

# ---------------------------------------------------------------- Database
Write-Host "Database '$DbName'" -ForegroundColor White

if (-not $portOpen -or -not $psql) {
    Say "database checks" $null "skipped (need both a reachable port and psql)"
}
else {
    $env:PGPASSWORD = $DbPassword
    try {
        $probe = (& $psql -U $DbUser -h $DbHost -p $DbPort -d postgres -t -A `
                    -c "select 1" 2>&1) -join " "

        if ($LASTEXITCODE -ne 0) {
            Say "login as '$DbUser'" $false $probe.Trim()
            Note "Could not log in as '$DbUser'. If your password is not '$DbPassword', re-run: .\scripts\check-env.ps1 -DbPassword yourpassword"
        }
        else {
            Say "login as '$DbUser'" $true "authenticated"

            # Java on Windows resolves "India Standard Time" to the deprecated IANA alias
            # "Asia/Calcutta", and pgjdbc sends whatever TimeZone.getDefault() reports as a
            # connection startup parameter. A server whose tzdata drops the backward-compat
            # aliases rejects it, and the connection dies before Flyway runs. Cheap to check,
            # thoroughly confusing to debug from the stack trace alone.
            $tz = (& $psql -U $DbUser -h $DbHost -p $DbPort -d postgres -t -A `
                    -c "select string_agg(name, ',' order by name) from pg_timezone_names where name in ('Asia/Calcutta','Asia/Kolkata')" 2>&1) -join ""
            $tz = $tz.Trim()

            if ($tz -like "*Asia/Calcutta*") {
                Say "timezone alias" $true "server accepts Asia/Calcutta, the id Java sends on Windows"
            }
            elseif ($tz -like "*Asia/Kolkata*") {
                Say "timezone alias" $null "server has Asia/Kolkata but not Asia/Calcutta"
                Note "This Postgres does not recognise 'Asia/Calcutta', which is the zone id Java reports on Windows. RecoveraiApplication has a static block pinning Asia/Kolkata to work around it. If you still see 'FATAL: invalid value for parameter TimeZone', that block is missing or the module was not rebuilt after the edit."
            }
            else {
                Say "timezone alias" $null "could not read pg_timezone_names: $tz"
            }

            $exists = (& $psql -U $DbUser -h $DbHost -p $DbPort -d postgres -t -A `
                        -c "select 1 from pg_database where datname = '$DbName'" 2>&1) -join ""

            if ($exists.Trim() -eq "1") {
                Say "database exists" $true $DbName

                $migrations = (& $psql -U $DbUser -h $DbHost -p $DbPort -d $DbName -t -A -c @"
select case
         when to_regclass('public.flyway_schema_history') is null then 'NOT_MIGRATED'
         else coalesce((select string_agg(version, ', ' order by installed_rank)
                        from flyway_schema_history where success), 'EMPTY')
       end
"@ 2>&1) -join ""
                $migrations = $migrations.Trim()

                if ($migrations -eq "NOT_MIGRATED" -or $migrations -eq "EMPTY") {
                    Say "flyway migrations" $null "none applied yet - Flyway will run them on first boot"
                }
                else {
                    Say "flyway migrations" $true "applied: $migrations"
                    # Exact token match, not a substring search: "12" contains "2" but is not V2.
                    $applied = @($migrations -split ',' | ForEach-Object { $_.Trim() })
                    if ($applied -notcontains "2") {
                        Note "V2 (webhook support) has not been applied. It will run on next boot; the webhook_event table does not exist until then."
                    }
                }

                $refs = (& $psql -U $DbUser -h $DbHost -p $DbPort -d $DbName -t -A -c @"
select case
         when to_regclass('public.recovery_attempt') is null then 'NO_TABLE'
         else (select count(*)::text from recovery_attempt where external_ref is not null)
       end
"@ 2>&1) -join ""
                $refs = $refs.Trim()

                if ($refs -eq "NO_TABLE") {
                    Say "attempts with external_ref" $null "recovery_attempt table not created yet"
                }
                elseif ($refs -eq "0") {
                    Say "attempts with external_ref" $null "0 - nothing for a webhook to match yet"
                    Note "No recovery_attempt has an external_ref. Webhooks will correctly come back IGNORED until you run a batch (and, without Razorpay keys, plant a ref by hand - see docs/first-run.md step 6)."
                }
                else {
                    Say "attempts with external_ref" $true "$refs row(s) a webhook could match"
                }
            }
            else {
                Say "database exists" $false "'$DbName' not found"
                Note "Database '$DbName' does not exist. Create it with:  createdb -U $DbUser $DbName    (or in psql:  CREATE DATABASE $DbName;)"
            }
        }
    }
    finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
}
Write-Host ""

# ---------------------------------------------------------------- Backend
Write-Host "Backend process" -ForegroundColor White
$appUp = Test-Port "localhost" 8080 800
if ($appUp) {
    Say "port 8080" $true "the backend appears to be running already"
}
else {
    Say "port 8080" $null "not running (expected, if you have not started it)"
}
Write-Host ""

# ---------------------------------------------------------------- Verdict
Write-Host "Verdict" -ForegroundColor Cyan
Write-Host "-------" -ForegroundColor Cyan
if ($problems.Count -eq 0) {
    Write-Host "Nothing blocking. Next: build the project, then follow docs/first-run.md." -ForegroundColor Green
}
else {
    Write-Host "$($problems.Count) thing(s) to deal with, in order:" -ForegroundColor Yellow
    Write-Host ""
    for ($i = 0; $i -lt $problems.Count; $i++) {
        Write-Host "  $($i + 1). $($problems[$i])"
        Write-Host ""
    }
}
