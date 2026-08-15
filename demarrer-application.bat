@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
cd /d "%~dp0"
rem Confort dev local : templates rechargeables sans redemarrer + logs detailles.
rem La production (Railway) ne definit pas ces variables, donc y garde le cache
rem actif et les logs en WARN (voir application.properties).
set "THYMELEAF_CACHE=false"
set "LOG_LEVEL_THYMELEAF=DEBUG"
set "LOG_LEVEL_WEB=DEBUG"
echo Demarrage de l'application EduSystem Pro...
echo Une fois prete, elle sera accessible sur http://localhost:8099
echo.
"C:\Program Files (x86)\Apache Maven\maven-mvnd-1.0.3-windows-amd64\bin\mvnd.exe" spring-boot:run
pause
