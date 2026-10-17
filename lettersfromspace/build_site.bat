@echo off
rem Builds the lab as a static web site in build\site, with the network running in each visitor's
rem browser: copy that folder to GitHub Pages or any web host. Options go to SiteBuilder, e.g.
rem   build_site.bat --url https://example.org/lettersfromspace/ --counter abacus
cd /d "%~dp0"
call "%~dp0build.bat" || exit /b 1
java -cp build\classes lettersfromspace.web.SiteBuilder %*
