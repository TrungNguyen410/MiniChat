@echo off
rem Bien dich MiniChat vao thu muc bin (can JDK 17 tro len)
cd /d "%~dp0"
set JAVAC=javac
if defined JAVA_HOME set JAVAC="%JAVA_HOME%\bin\javac"
if not exist bin mkdir bin
%JAVAC% -encoding UTF-8 --release 17 -d bin src\minichat\common\*.java src\minichat\server\*.java src\minichat\client\*.java src\minichat\client\ui\*.java src\minichat\selftest\*.java
if errorlevel 1 (echo Bien dich that bai & pause & exit /b 1)
echo Bien dich thanh cong.
