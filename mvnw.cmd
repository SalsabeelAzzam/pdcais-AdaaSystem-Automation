@REM ---------------------------------------------------------------------------
@REM Maven Wrapper (Windows).
@REM
@REM Downloads the Maven distribution named in .mvn\wrapper\maven-wrapper.properties
@REM into the user's Maven home on first use, then runs it. Only a JDK 17 on PATH
@REM is required; Maven itself is not a prerequisite.
@REM ---------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

set "PROPS=%~dp0.mvn\wrapper\maven-wrapper.properties"
if not exist "%PROPS%" (
  echo mvnw: missing %PROPS% 1>&2
  exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%PROPS%") do (
  if "%%a"=="distributionUrl" set "DIST_URL=%%b"
)
if not defined DIST_URL (
  echo mvnw: distributionUrl is not set in %PROPS% 1>&2
  exit /b 1
)

for %%i in ("!DIST_URL!") do set "ARCHIVE=%%~nxi"
for %%i in ("!DIST_URL!") do set "DIST_NAME=%%~ni"
set "DIST_NAME=!DIST_NAME:-bin=!"

if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_HOME=!MAVEN_USER_HOME!\wrapper\dists\!DIST_NAME!"

if not exist "!MAVEN_HOME!\bin\mvn.cmd" (
  echo mvnw: downloading !DIST_URL!
  if not exist "!MAVEN_HOME!" mkdir "!MAVEN_HOME!"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue';" ^
    "Invoke-WebRequest -Uri '!DIST_URL!' -OutFile '!MAVEN_HOME!\!ARCHIVE!';" ^
    "Expand-Archive -Path '!MAVEN_HOME!\!ARCHIVE!' -DestinationPath '!MAVEN_HOME!' -Force;" ^
    "Get-ChildItem -Path '!MAVEN_HOME!\!DIST_NAME!' -Force | Move-Item -Destination '!MAVEN_HOME!' -Force;" ^
    "Remove-Item '!MAVEN_HOME!\!DIST_NAME!' -Recurse -Force;" ^
    "Remove-Item '!MAVEN_HOME!\!ARCHIVE!' -Force"
  if errorlevel 1 (
    echo mvnw: failed to install Maven 1>&2
    exit /b 1
  )
)

call "!MAVEN_HOME!\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
