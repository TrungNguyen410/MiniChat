@echo off
rem Chay kiem thu tu dong MiniChat (SelfTest)
cd /d "%~dp0"
chcp 65001 > nul
title MiniChat SelfTest
set JAVA=java
if defined JAVA_HOME set JAVA="%JAVA_HOME%\bin\java"
call build.bat
if errorlevel 1 exit /b 1
%JAVA% -cp bin minichat.selftest.SelfTest
pause
