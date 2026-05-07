# boom

BOM 管理系统，Java Swing 桌面应用，使用 H2 本地数据库。

## Windows 正式安装包

项目已配置 Windows 原生安装包构建脚本，使用 JDK 自带的 `jpackage` 生成可分发安装包。默认产物是 `.exe` 安装向导，也可以生成 `.msi`。

安装包行为：

- 安装时可选择安装目录
- 可创建桌面快捷方式
- 可创建开始菜单入口
- Windows “应用和功能” 中可正常卸载
- 自带 Java 运行时，用户电脑无需单独安装 Java
- 应用数据默认写入安装目录下的 `data` 文件夹，例如 `D:\BOM管理系统\data`

### 构建环境

必须在 Windows 上构建 Windows 安装包；`jpackage` 不支持跨平台生成其他系统的原生安装包。

需要安装并加入 `PATH`：

- JDK 17 或更高版本，需包含 `jpackage`
- Maven
- WiX Toolset 3.x，需包含 `candle.exe` 和 `light.exe`

注意：只安装新版 WiX Toolset 且只有 `wix.exe` 时，不能满足 JDK 17 `jpackage` 生成 `.exe`/`.msi` 对 `candle.exe` 和 `light.exe` 的要求。

### 构建命令

在 Windows 终端中执行：

```bat
build-windows.bat
```

默认生成 `.exe`：

```text
dist\windows\BOM管理系统-1.0.0.exe
```

生成 `.msi`：

```bat
build-windows.bat -Type msi
```

按当前用户安装、不要求管理员权限：

```bat
build-windows.bat -PerUser
```

清理旧的安装包输出后重新构建：

```bat
build-windows.bat -CleanDist
```

数据库会随安装目录确定。注意：如果安装到 `C:\Program Files` 这类受保护目录，普通用户可能没有数据库写入权限；建议选择 `D:\BOM管理系统` 这类可写目录，或使用 `-PerUser` 构建/安装。

如果需要自定义图标，把 Windows `.ico` 文件放到项目根目录并命名为 `app.ico`，构建脚本会自动使用。

参考：[Oracle `jpackage` 文档](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html)说明 Windows 可通过 `--win-dir-chooser` 让用户选择安装目录，并且原生安装包必须在目标平台构建。
