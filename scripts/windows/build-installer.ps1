[CmdletBinding()]
param(
    [ValidateSet("exe", "msi", "app-image")]
    [string]$Type = "exe",

    [string]$AppName = "",
    [string]$AppVersion = "",
    [string]$ArtifactId = "",
    [string]$Vendor = "BOM",
    [string]$MenuGroup = "",

    [switch]$PerUser,
    [switch]$RunTests,
    [switch]$CleanDist,
    [switch]$VerboseJpackage
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-Command {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$InstallHint
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command) {
        throw "[错误] 未找到 $Name。$InstallHint"
    }
    return $command
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]$Command,
        [string[]]$Arguments = @()
    )

    & $Command.Path @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "[错误] 命令执行失败: $($Command.Name) (退出码 $LASTEXITCODE)"
    }
}

function Get-JavaMajorVersion {
    param([Parameter(Mandatory = $true)]$JavaCommand)

    $versionOutput = & $JavaCommand.Path -version 2>&1
    $versionLine = $versionOutput | Select-String -Pattern "version" | Select-Object -First 1
    if ($null -eq $versionLine -or $versionLine.ToString() -notmatch '"(?<version>[^"]+)"') {
        throw "[错误] 无法识别 Java 版本。请确认安装的是 JDK 17 或更高版本。"
    }

    $version = $Matches.version
    if ($version -match "^1\.(?<major>\d+)") {
        return [int]$Matches.major
    }
    return [int]($version.Split(".")[0])
}

function Get-PomValue {
    param(
        [Parameter(Mandatory = $true)][xml]$Pom,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $namespaceManager = New-Object System.Xml.XmlNamespaceManager($Pom.NameTable)
    $namespaceManager.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
    $node = $Pom.SelectSingleNode("/m:project/m:$Name", $namespaceManager)
    if ($null -eq $node -or [string]::IsNullOrWhiteSpace($node.InnerText)) {
        throw "[ERROR] Missing Maven project value: $Name"
    }
    return $node.InnerText.Trim()
}

$DefaultChineseName = "BOM" + [char]0x7ba1 + [char]0x7406 + [char]0x7cfb + [char]0x7edf
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ($env:OS -ne "Windows_NT") {
    throw "[ERROR] This script must run on Windows. jpackage cannot create Windows exe/msi installers from another OS."
}

$PomPath = Join-Path $RepoRoot "pom.xml"
if (!(Test-Path $PomPath)) {
    throw "[错误] 未找到 pom.xml，请在项目根目录内运行构建脚本。"
}

[xml]$Pom = Get-Content $PomPath -Raw -Encoding UTF8
if ([string]::IsNullOrWhiteSpace($ArtifactId)) {
    $ArtifactId = Get-PomValue $Pom "artifactId"
}
if ([string]::IsNullOrWhiteSpace($AppVersion)) {
    $AppVersion = Get-PomValue $Pom "version"
}
if ([string]::IsNullOrWhiteSpace($AppName)) {
    $AppName = $DefaultChineseName
}
if ([string]::IsNullOrWhiteSpace($MenuGroup)) {
    $MenuGroup = $DefaultChineseName
}

$MainJar = "$ArtifactId-$AppVersion.jar"
$TargetDir = Join-Path $RepoRoot "target"
$DistDir = Join-Path $RepoRoot "dist\windows"
$IconPath = Join-Path $RepoRoot "app.ico"
$UpgradeUuid = "53B583DB-4D1D-40EB-99FB-8898602E3856"

Write-Host "============================================================"
Write-Host "    BOM 管理系统 - Windows 安装包构建"
Write-Host "============================================================"
Write-Host ""
Write-Host "[信息] 项目目录: $RepoRoot"
Write-Host "[信息] 安装包类型: $Type"
Write-Host "[信息] 应用版本: $AppVersion"
Write-Host ""

$java = Require-Command "java" "请安装 JDK 17 或更高版本: https://adoptium.net/"
$maven = Require-Command "mvn" "请安装 Maven 并加入 PATH: https://maven.apache.org/download.cgi"
$jpackage = Require-Command "jpackage" "请使用 JDK 17 或更高版本，并确认 JDK bin 目录已加入 PATH。"

$javaMajor = Get-JavaMajorVersion $java
if ($javaMajor -lt 17) {
    throw "[错误] 需要 JDK 17 或更高版本，当前 Java 主版本为 $javaMajor。"
}
Write-Host "[OK] Java / jpackage 已就绪"
Write-Host "[OK] Maven 已就绪"

if ($Type -in @("exe", "msi")) {
    $candle = Get-Command "candle.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    $light = Get-Command "light.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $candle -or $null -eq $light) {
        throw "[错误] 生成 Windows $Type 安装包需要 WiX Toolset 3.x，并确保 candle.exe/light.exe 在 PATH 中。下载地址: https://wixtoolset.org/releases/"
    }
    Write-Host "[OK] WiX Toolset 已就绪"
}

if ($CleanDist -and (Test-Path $DistDir)) {
    Remove-Item $DistDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

Push-Location $RepoRoot
try {
    Write-Host ""
    Write-Host "[1/2] 构建 Fat JAR..."
    $mvnArgs = @("clean", "package")
    if (!$RunTests) {
        $mvnArgs += "-DskipTests"
    }
    Invoke-Checked $maven $mvnArgs

    $JarPath = Join-Path $TargetDir $MainJar
    if (!(Test-Path $JarPath)) {
        throw "[错误] 未找到构建产物: $JarPath"
    }
    Write-Host "[OK] Fat JAR 构建成功: $JarPath"

    Write-Host ""
    Write-Host "[2/2] 生成 Windows 安装包..."
    $jpackageArgs = @(
        "--input", $TargetDir,
        "--main-jar", $MainJar,
        "--main-class", "com.bom.Main",
        "--name", $AppName,
        "--app-version", $AppVersion,
        "--vendor", $Vendor,
        "--description", $DefaultChineseName,
        "--type", $Type,
        "--dest", $DistDir,
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Duser.language=zh",
        "--java-options", "-Duser.country=CN"
    )

    if (Test-Path $IconPath) {
        $jpackageArgs += @("--icon", $IconPath)
    } else {
        Write-Host "[信息] 未发现 app.ico，将使用默认程序图标。"
    }

    if ($Type -in @("exe", "msi")) {
        $jpackageArgs += @(
            "--win-dir-chooser",
            "--win-shortcut",
            "--win-menu",
            "--win-menu-group", $MenuGroup,
            "--win-shortcut-prompt",
            "--win-upgrade-uuid", $UpgradeUuid
        )
        if ($PerUser) {
            $jpackageArgs += "--win-per-user-install"
        }
    }

    if ($VerboseJpackage) {
        $jpackageArgs += "--verbose"
    }

    Invoke-Checked $jpackage $jpackageArgs
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "============================================================"
Write-Host "    构建完成"
Write-Host "    输出目录: $DistDir"
Write-Host "============================================================"
Get-ChildItem $DistDir | Sort-Object LastWriteTime -Descending | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize
