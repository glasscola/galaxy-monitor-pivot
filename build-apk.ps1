param(
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [string]$BuildToolsVersion = '36.0.0',
    [string]$BuildPath,
    [string]$OutputName = 'PivotControl-1.2.0.apk',
    [string]$KeyStorePath,
    [string]$KeyAlias = 'pivot',
    [string]$StorePassword = $env:PIVOT_STORE_PASSWORD
)
$ErrorActionPreference = 'Stop'
$projectPath = $PSScriptRoot
$buildPath = if ($BuildPath) { [System.IO.Path]::GetFullPath($BuildPath) } else { Join-Path $projectPath 'build-manual' }
$toolPath = Join-Path $SdkPath "build-tools/$BuildToolsVersion"
$androidJar = Join-Path $SdkPath 'platforms/android-36/android.jar'
function Run-Checked([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed: $LASTEXITCODE" }
}
foreach ($dir in @('aar','classes','dex','resources','tmp')) { New-Item -ItemType Directory -Force -Path (Join-Path $buildPath $dir) | Out-Null }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$libraries = @()
foreach ($aar in Get-ChildItem -LiteralPath (Join-Path $projectPath 'libs') -Filter '*.aar') {
    $destination = Join-Path $buildPath "aar/$($aar.BaseName)"
    [System.IO.Compression.ZipFile]::ExtractToDirectory($aar.FullName,$destination,$true)
    $libraries += Join-Path $destination 'classes.jar'
}
$libraries += (Get-ChildItem -LiteralPath (Join-Path $projectPath 'libs') -Filter '*.jar').FullName
$source = (Get-ChildItem -LiteralPath (Join-Path $projectPath 'app/src/main/java') -Filter '*.java' -Recurse).FullName
Run-Checked 'javac' (@('-encoding','UTF-8','--release','8','-classpath',((@($androidJar)+$libraries) -join ';'),'-d',"$buildPath/classes") + $source)
Run-Checked 'jar' @("-J-Djava.io.tmpdir=$buildPath/tmp",'cf',"$buildPath/app-classes.jar",'-C',"$buildPath/classes",'.')
Run-Checked "$toolPath/aapt2.exe" @('compile','--dir',"$projectPath/app/src/main/res",'-o',"$buildPath/compiled.zip")
Run-Checked "$toolPath/aapt2.exe" @('link','-o',"$buildPath/unsigned.apk",'-I',$androidJar,'--manifest',"$projectPath/app/src/main/AndroidManifest.xml",'--min-sdk-version','30','--target-sdk-version','35',"$buildPath/compiled.zip")
Run-Checked "$toolPath/d8.bat" (@('--release','--min-api','30','--lib',$androidJar,'--output',"$buildPath/dex","$buildPath/app-classes.jar") + $libraries)
Run-Checked 'jar' @("-J-Djava.io.tmpdir=$buildPath/tmp",'uf',"$buildPath/unsigned.apk",'-C',"$buildPath/dex",'classes.dex')
Run-Checked "$toolPath/zipalign.exe" @('-f','-p','4',"$buildPath/unsigned.apk","$buildPath/aligned.apk")
if (!$KeyStorePath) {
    # Local test key. Keep it private if future updates should use the same identity.
    $KeyStorePath = Join-Path $buildPath 'local-test.jks'
    $StorePassword = 'pivot-local-test'
    if (!(Test-Path -LiteralPath $KeyStorePath)) {
        Run-Checked 'keytool' @('-genkeypair','-keystore',$KeyStorePath,'-storepass',$StorePassword,'-keypass',$StorePassword,'-alias',$KeyAlias,'-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=Pivot Control Local Test')
    }
}
if (!$StorePassword) { throw 'Set PIVOT_STORE_PASSWORD or pass StorePassword for the signing key.' }
$env:PIVOT_SIGN_PASSWORD = $StorePassword
try {
    Run-Checked "$toolPath/apksigner.bat" @('sign','--ks',$KeyStorePath,'--ks-key-alias',$KeyAlias,'--ks-pass','env:PIVOT_SIGN_PASSWORD','--key-pass','env:PIVOT_SIGN_PASSWORD','--out',"$projectPath/$OutputName","$buildPath/aligned.apk")
} finally { Remove-Item Env:PIVOT_SIGN_PASSWORD -ErrorAction SilentlyContinue }
Run-Checked "$toolPath/apksigner.bat" @('verify','--verbose','--print-certs',"$projectPath/$OutputName")
Get-FileHash -Algorithm SHA256 -LiteralPath "$projectPath/$OutputName"
