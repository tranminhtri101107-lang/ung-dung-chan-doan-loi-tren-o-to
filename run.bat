@echo off
rem Build and run with JDK 25; the default Java on PATH is Java 8.
set JDK=C:\Users\MINHTRI\.jdks\openjdk-25.0.1
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
"%JDK%\bin\javac" -encoding UTF-8 -cp "lib\*" -d bin @sources.txt || exit /b 1
del sources.txt
"%JDK%\bin\java" -cp "bin;lib\*" app.Main
