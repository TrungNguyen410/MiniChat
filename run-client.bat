@echo off
rem Chay MiniChat Client. Tham so (tuy chon): [host] [cong]
cd /d "%~dp0"
chcp 65001 > nul
title MiniChat Client
set JAVA=java
if defined JAVA_HOME set JAVA="%JAVA_HOME%\bin\java"
if not exist bin\minichat call build.bat
%JAVA% -cp bin minichat.client.ChatClientGUI %*

