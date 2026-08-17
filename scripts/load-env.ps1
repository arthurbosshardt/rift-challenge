# Setup local RiftChallenge (Windows PowerShell)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $projectRoot ".env"

if (Test-Path $envFile) {
  Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
      Set-Item -Path "env:$($matches[1].Trim())" -Value $matches[2].Trim()
    }
  }
}

$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
$mavenBin = Join-Path $env:LOCALAPPDATA "apache-maven-3.9.9\bin"

if (Test-Path $javaHome) {
  $env:JAVA_HOME = $javaHome
  $env:Path = "$javaHome\bin;$mavenBin;" + $env:Path
}

Write-Host "Java:" (java -version 2>&1 | Select-Object -First 1)
Write-Host "Maven:" (mvn -version 2>&1 | Select-Object -First 1)
Write-Host "PostgreSQL password par defaut installe: postgres (superuser)"
