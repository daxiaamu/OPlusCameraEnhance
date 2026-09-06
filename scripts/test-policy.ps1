param([string]$GradleCache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testOutput = Join-Path $projectRoot 'app\build\policy-tests'
New-Item -ItemType Directory -Force $testOutput | Out-Null
$junit = (Get-ChildItem (Join-Path $GradleCache 'junit\junit\4.13.2') -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$hamcrest = (Get-ChildItem (Join-Path $GradleCache 'org.hamcrest\hamcrest-core') -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
if (!$junit -or !$hamcrest) { throw 'Run Gradle dependency resolution first, or pass -GradleCache.' }
$javaCompiler = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javac.exe' } else { 'javac' }
$javaRunner = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
& $javaCompiler -encoding UTF-8 -cp "$junit;$hamcrest" -d $testOutput (Join-Path $projectRoot 'app\src\main\java\com\daxiaamu\opluscameraenhance\NativeSupportPolicy.java') (Join-Path $projectRoot 'app\src\test\java\com\daxiaamu\opluscameraenhance\NativeSupportPolicyTest.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $javaRunner -cp "$testOutput;$junit;$hamcrest" org.junit.runner.JUnitCore com.daxiaamu.opluscameraenhance.NativeSupportPolicyTest
exit $LASTEXITCODE