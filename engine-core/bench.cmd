@echo off
if not exist "%~dp0build\chaturanga-engine.jar" call "%~dp0build.cmd" -SkipTests
if errorlevel 1 exit /b %ERRORLEVEL%
java -cp "%~dp0build\chaturanga-engine.jar" io.chaturanga.engine.cli.BenchMain %*
