$envFile = Join-Path $PSScriptRoot "..\.env"
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $parts = $_ -split '=', 2
    if ($parts.Length -eq 2) {
        [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim())
    }
}
$mvn = "C:\Users\tanor\AppData\Local\apache-maven-3.9.9\bin\mvn.cmd"
& $mvn -f (Join-Path $PSScriptRoot "..\backend\pom.xml") spring-boot:run
