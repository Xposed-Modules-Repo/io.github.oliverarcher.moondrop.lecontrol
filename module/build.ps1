$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$jdk = 'D:\Code\Jdk\jdk-17\bin'
$tools = 'D:\Code\Android\build-tools\37.0.0'
$platform = 'D:\Code\Android\platforms\android-35\android.jar'
$api = Join-Path (Split-Path $project) 'xposed-api-82.jar'
$build = Join-Path $project 'build'
New-Item -ItemType Directory -Force -Path "$build\classes","$build\dex" | Out-Null
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build tool failed with exit code $LASTEXITCODE" } }
& "$jdk\javac.exe" -source 8 -target 8 -encoding UTF-8 -cp "$platform;$api" -d "$build\classes" "$project\src\fishking\moondrop\lecontrol\LeControlHook.java"
Check-Exit
& "$jdk\jar.exe" cf "$build\hook.jar" -C "$build\classes" .
Check-Exit
$env:JAVA_HOME = Split-Path $jdk
& "$tools\d8.bat" --lib $platform --classpath $api --min-api 31 --output "$build\dex" "$build\hook.jar"
Check-Exit
& "$tools\aapt.exe" package -f -M "$project\AndroidManifest.xml" -S "$project\res" -A "$project\assets" -I $platform -F "$build\unsigned.apk"
Check-Exit
Push-Location "$build\dex"
try { & "$tools\aapt.exe" add "$build\unsigned.apk" classes.dex; Check-Exit } finally { Pop-Location }
& "$tools\zipalign.exe" -f 4 "$build\unsigned.apk" "$build\aligned.apk"
Check-Exit
$credentialsFile = 'D:\Code\keystore\fishking-release.CREDENTIALS.txt'
$key = 'D:\Code\keystore\fishking-release.jks'
if (!(Test-Path -LiteralPath $credentialsFile) -or !(Test-Path -LiteralPath $key)) {
  throw 'Release keystore or credentials file is missing.'
}
$credentials = @{}
Get-Content -LiteralPath $credentialsFile | ForEach-Object {
  if ($_ -match '^\s*([^:=]+)[:=]\s*(.*)$') { $credentials[$matches[1].Trim()] = $matches[2].Trim() }
}
$storePass = $credentials['store password']
$keyPass = $credentials['key password']
$alias = $credentials['alias']
if (!$storePass -or !$keyPass -or !$alias) { throw 'Required signing values are missing from credentials file.' }
$out = Join-Path (Split-Path $project) 'Moondrop-LE-Control-0.7.apk'
& "$tools\apksigner.bat" sign --ks $key --ks-key-alias $alias --ks-pass "pass:$storePass" --key-pass "pass:$keyPass" --out $out "$build\aligned.apk"
Check-Exit
& "$tools\apksigner.bat" verify --verbose $out
Check-Exit
Write-Output "Built: $out"
