$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Find-JdkTool([string] $Name) {
    $extension = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
    if ($env:JAVA_HOME) {
        $toolPath = Join-Path $env:JAVA_HOME "bin/$Name$extension"
        if (-not (Test-Path -LiteralPath $toolPath -PathType Leaf)) {
            throw "JDK tool not found: $toolPath"
        }
        return $toolPath
    }
    return (Get-Command $Name -CommandType Application -ErrorAction Stop).Source
}

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $java = Find-JdkTool 'java'
    $javac = Find-JdkTool 'javac'
    $javaVersion = (& $java --version | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '^(openjdk|java) 17(?:[.\s+-]|$)') {
        throw 'JDK 17 is required. Set JAVA_HOME or update PATH.'
    }
    $compilerVersion = (& $javac --version | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $compilerVersion -notmatch '^javac 17(?:[.\s+-]|$)') {
        throw 'javac 17 is required. Set JAVA_HOME or update PATH.'
    }
    Write-Output $javaVersion
    Write-Output $compilerVersion

    $classes = Join-Path $projectRoot 'build/classes'
    $checks = Join-Path $projectRoot 'build/checks'
    $resultId = [Guid]::NewGuid().ToString('N')
    $results = "build/results-$resultId"
    New-Item -ItemType Directory -Path $classes, $checks -Force | Out-Null

    & $javac --release 17 -encoding UTF-8 -d $classes src/MiniBank.java
    if ($LASTEXITCODE -ne 0) { throw 'Application compilation failed.' }
    & $javac --release 17 -encoding UTF-8 -d $checks tests/MiniBankChecks.java
    if ($LASTEXITCODE -ne 0) { throw 'Check compilation failed.' }
    & $java '-Dfile.encoding=UTF-8' -cp $checks MiniBankChecks $classes $results tests/scenarios-v2.md src/MiniBank.java
    if ($LASTEXITCODE -ne 0) { throw "Checks failed. See $results" }
    Write-Output "Results: $results/results.md"
}
finally {
    Pop-Location
}
