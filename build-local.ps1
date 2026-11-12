$ErrorActionPreference = 'Stop'
# Uses workspace-local tools when present, otherwise the configured JDK/SDK and Gradle Wrapper.
if (Test-Path "$PSScriptRoot/.tools/jdk") {
    $env:JAVA_HOME = (Get-ChildItem "$PSScriptRoot/.tools/jdk" -Directory | Select-Object -First 1).FullName
}
if (Test-Path "$PSScriptRoot/.tools/android-sdk") { $env:ANDROID_HOME = "$PSScriptRoot/.tools/android-sdk" }
if (Test-Path "$PSScriptRoot/.tools/gradle-8.13/bin/gradle.bat") {
    $env:GRADLE_USER_HOME = "$PSScriptRoot/.tools/gradle-home"
    $gradleExecutable = "$PSScriptRoot/.tools/gradle-8.13/bin/gradle.bat"
} else { $gradleExecutable = "$PSScriptRoot/gradlew.bat" }
Set-Location -LiteralPath $PSScriptRoot
& $gradleExecutable --no-daemon --max-workers=4 :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
exit $LASTEXITCODE
