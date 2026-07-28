@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
cd /d "%~dp0"
echo Demarrage de l'application EduSystem Pro...
echo Une fois prete, elle sera accessible sur http://localhost:8099
echo.
"C:\Program Files (x86)\Apache Maven\maven-mvnd-1.0.3-windows-amd64\bin\mvnd.exe" spring-boot:run
pause
