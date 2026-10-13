param([string]$BuildPath)
$ErrorActionPreference='Stop'
if (!$BuildPath) { $BuildPath=Join-Path $PSScriptRoot 'build-tests' }
New-Item -ItemType Directory -Force -Path $BuildPath | Out-Null
$sources=(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests') -Filter '*.java' -Recurse).FullName
$sources+=Join-Path $PSScriptRoot 'app/src/main/java/com/pivotcontrol/app/PivotService.java'
$sources+=Join-Path $PSScriptRoot 'app/src/main/java/com/pivotcontrol/app/RotationPolicy.java'
$sources+=Join-Path $PSScriptRoot 'app/src/main/java/com/pivotcontrol/app/MirrorGeometry.java'
& javac -encoding UTF-8 --release 8 -d $BuildPath @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& java -cp $BuildPath com.pivotcontrol.app.PivotServiceTest
if ($LASTEXITCODE -ne 0) { throw 'Host tests failed' }
& java -cp $BuildPath com.pivotcontrol.app.MirrorGeometryTest
if ($LASTEXITCODE -ne 0) { throw 'Mirror geometry tests failed' }
