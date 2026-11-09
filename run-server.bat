@echo off
rem Chay MiniChat Server. Tham so (tuy chon): [cong]
cd /d "%~dp0"
chcp 65001 > nul
title MiniChat Server
set JAVA=java
if defined JAVA_HOME set JAVA="%JAVA_HOME%\bin\java"
if not exist bin\minichat call build.bat
%JAVA% -cp bin minichat.server.ChatServer %*
pause
