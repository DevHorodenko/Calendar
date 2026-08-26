<#
.SYNOPSIS
    Gera o instalador do Calendario para Windows.

.DESCRIPTION
    Tres etapas:

      1. mvn -Pdesktop package  -> compila o frontend e o embute no jar, para o
                                   aplicativo instalado ser um processo so.
      2. jlink                  -> monta um runtime Java so com os modulos usados
                                   (~54 MB, contra ~291 MB do JDK completo).
      3. jpackage               -> junta tudo num .exe instalador, com atalho no
                                   Menu Iniciar e entrada de desinstalacao.

    O jpackage precisa do WiX para gerar o .exe. O script procura o candle.exe do
    WiX 3.14 no PATH e em build\wix3; se nao achar, ainda gera a versao portatil.

.PARAMETER SkipInstaller
    Gera so a pasta portatil, sem tentar o .exe. Util quando o WiX nao esta por perto.

.EXAMPLE
    .\installer\package-windows.ps1
#>
[CmdletBinding()]
param(
    [switch]$SkipInstaller,
    [string]$AppVersion = '1.0'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build'
$jarName = 'calendar-backend-1.0-SNAPSHOT.jar'

if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME nao esta definido. Aponte-o para um JDK 25 ou mais novo.'
}
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
$jlink = Join-Path $env:JAVA_HOME 'bin\jlink.exe'
foreach ($tool in @($jpackage, $jlink)) {
    if (-not (Test-Path $tool)) {
        throw "Nao encontrei $tool. JAVA_HOME precisa apontar para um JDK completo, nao para um JRE."
    }
}

# ---------------------------------------------------------------- 1. jar

Write-Host '==> Compilando o backend com o frontend embutido' -ForegroundColor Cyan
Push-Location (Join-Path $root 'backend')
try {
    # O npm ci apaga o node_modules, e o servidor do Vite, se estiver rodando,
    # segura o esbuild.exe e faz a etapa falhar.
    Get-CimInstance Win32_Process |
        Where-Object { $_.Name -eq 'node.exe' -and $_.CommandLine -like '*vite*' } |
        ForEach-Object {
            Write-Host "    parando o servidor do Vite (pid $($_.ProcessId))"
            try { Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop } catch {}
        }

    # Uma instancia aberta do Calendario segura os arquivos em target\ e faz o
    # `clean` falhar. O Windows so libera os identificadores um instante depois de
    # o processo morrer, dai a pausa.
    $rodando = @(Get-Process Calendario -ErrorAction SilentlyContinue)
    if ($rodando) {
        Write-Host "    encerrando o Calendario aberto ($($rodando.Count) processo(s))"
        $rodando | ForEach-Object { try { Stop-Process -Id $_.Id -Force -ErrorAction Stop } catch {} }
        Start-Sleep -Seconds 3
    }

    & mvn -B -Pdesktop clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'A compilacao do backend falhou.' }
} finally {
    Pop-Location
}

$jar = Join-Path $root "backend\target\$jarName"
if (-not (Test-Path $jar)) { throw "Jar nao encontrado em $jar" }

# ---------------------------------------------------------------- 2. runtime

Write-Host '==> Montando o runtime Java enxuto' -ForegroundColor Cyan
$runtime = Join-Path $build 'runtime'
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build | Out-Null

# Modulos que Spring Boot, Hibernate, H2 e a bandeja do sistema exigem em execucao.
# Faltando um, o aplicativo so quebra ao rodar -- nunca ao compilar.
$modules = @(
    'java.base', 'java.compiler', 'java.desktop', 'java.instrument', 'java.logging',
    'java.management', 'java.naming', 'java.net.http', 'java.prefs', 'java.rmi',
    'java.scripting', 'java.security.jgss', 'java.security.sasl', 'java.sql',
    'java.sql.rowset', 'java.transaction.xa', 'java.xml', 'java.xml.crypto',
    'jdk.crypto.cryptoki', 'jdk.crypto.ec', 'jdk.httpserver', 'jdk.jfr',
    'jdk.management', 'jdk.unsupported', 'jdk.unsupported.desktop'
) -join ','

& $jlink --add-modules $modules --strip-debug --no-header-files --no-man-pages `
         --compress=zip-6 --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'O jlink falhou.' }

# ---------------------------------------------------------------- 3. jpackage

$inputDir = Join-Path $build 'input'
$dist = Join-Path $build 'dist'
foreach ($dir in @($inputDir, $dist)) {
    if (Test-Path $dir) {
        try {
            Remove-Item $dir -Recurse -Force -ErrorAction Stop
        } catch {
            # Um instalador ainda aberto segura o proprio .exe, e ele pode estar
            # elevado -- fora do alcance deste processo. Apagar o que da e suficiente:
            # o jpackage sobrescreve o resto, e uma versao nova sai com outro nome.
            Write-Warning "Nao consegui limpar $dir por completo; seguindo com o que deu."
            Get-ChildItem $dir -Recurse -Force -ErrorAction SilentlyContinue |
                Sort-Object { $_.FullName.Length } -Descending |
                ForEach-Object { try { Remove-Item $_.FullName -Force -Recurse -ErrorAction Stop } catch {} }
        }
    }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
Copy-Item $jar $inputDir

# O jpackage copia tudo que estiver em --input para dentro do aplicativo, entao a
# pasta contem apenas o jar.
$common = @(
    '--name', 'Calendario',
    '--app-version', $AppVersion,
    '--vendor', 'Horodenko',
    '--description', 'Calendario pessoal com eventos unicos e recorrentes',
    '--input', $inputDir,
    '--main-jar', $jarName,
    '--runtime-image', $runtime,
    '--icon', (Join-Path $PSScriptRoot 'one-ring.ico'),
    # O perfil chega como propriedade de sistema, e nao como argumento do programa.
    '--java-options', '-Dspring.profiles.active=desktop',
    # Sem isto o Spring liga o modo headless e o icone da bandeja nao aparece.
    '--java-options', '-Djava.awt.headless=false',
    '--java-options', '-Xmx256m',
    '--dest', $dist
)

Write-Host '==> Gerando a versao portatil' -ForegroundColor Cyan
& $jpackage --type app-image @common
if ($LASTEXITCODE -ne 0) { throw 'O jpackage falhou ao gerar a versao portatil.' }

if ($SkipInstaller) {
    Write-Host "`nPronto: $dist\Calendario\Calendario.exe" -ForegroundColor Green
    return
}

# O WiX 3.14 roda direto de uma pasta, sem instalacao e sem privilegio de
# administrador. O WiX 7 tambem serve, mas exige aceitar o EULA da Open Source
# Maintenance Fee -- uma decisao de licenciamento de quem esta empacotando.
$wixLocal = Join-Path $build 'wix3'
if (Test-Path (Join-Path $wixLocal 'candle.exe')) {
    $env:PATH = "$wixLocal;$env:PATH"
}

if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    Write-Warning @"
WiX nao encontrado; o instalador .exe nao sera gerado.
A versao portatil ficou pronta em $dist\Calendario.

Para habilitar o .exe, baixe os binarios do WiX 3.14 (sem instalar nada):
  curl -L -o build\wix314.zip https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip
  Expand-Archive build\wix314.zip -DestinationPath build\wix3
"@
    return
}

# O Windows Installer nao reinstala a mesma versao: ele reconhece o produto ja
# instalado e sai sem fazer nada nem avisar -- o instalador parece "nao abrir".
# Quem permite a troca e o --win-upgrade-uuid, mas so quando a versao muda.
$chaves = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*'
$instalada = Get-ItemProperty $chaves -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -like 'Calendario*' } |
    Select-Object -First 1 -ExpandProperty DisplayVersion -ErrorAction SilentlyContinue
if ($instalada -and $instalada -eq $AppVersion) {
    Write-Warning "A versao $AppVersion ja esta instalada; o instalador vai abrir e sair sem fazer nada. Gere com uma versao maior, por exemplo: -AppVersion 1.0.1"
}

Write-Host '==> Gerando o instalador .exe' -ForegroundColor Cyan
& $jpackage --type exe @common `
    --win-shortcut --win-menu --win-menu-group 'Calendario' --win-dir-chooser `
    --win-upgrade-uuid '6f2c1b84-9d3e-4a7f-8c15-2e9b7a4d6c30'
if ($LASTEXITCODE -ne 0) { throw 'O jpackage falhou ao gerar o instalador.' }

$installer = Join-Path $dist "Calendario-$AppVersion.exe"
Write-Host "`nPronto:" -ForegroundColor Green
Write-Host "  instalador: $installer"
Write-Host "  portatil  : $dist\Calendario\Calendario.exe"
