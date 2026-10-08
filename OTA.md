# OTA 更新与发布

应用中的入口：**齿轮 → 关于 → 检查更新**。用户查看版本说明后下载 APK，下载完成后点击安装；第一次会引导开启系统的“允许此来源安装应用”权限。更新保留模板和设置。下载与安装不需要 ADB 或 Shizuku，虚拟输入的激活要求仍由系统决定。

## 更新来源

- 项目：<https://github.com/Zhgma/TouchXbox>
- 最新版本页：<https://github.com/Zhgma/TouchXbox/releases/latest>
- OTA 信息：<https://github.com/Zhgma/TouchXbox/releases/latest/download/latest.json>
- `latest.json` 和其中 `apkFile` 指定的 APK 必须作为同一个正式 Release 的附件上传。

使用 GitHub 提供的最新 Release 附件链接，不需要设备持有 GitHub 令牌，也不消耗 GitHub REST API 的匿名查询配额。仓库及 Release 必须公开；草稿、预发布版本不作为正式 OTA 版本。首次发布前，检查更新会提示更新文件尚未发布。

应用按 Android `versionCode` 判断更新，不按版本名称排序。只接受 HTTPS，至多跟随五次允许域名的重定向，支持 GitHub 的附件 CDN；不会接受 HTTP 降级或任意站点重定向。安装前核对大小、SHA-256、包名、版本、最低系统版本以及与已安装应用一致的签名证书。当前要求签名证书完全一致，不支持签名密钥轮换。

下载保存在应用私有缓存中，不需要存储权限；未完成或校验失败的下载不会成为可安装文件。下载期间旋转屏幕可以继续，离开更新页面会取消未完成下载；安装器只获得所选 APK 的临时只读权限。应用无法静默安装，最后仍由系统显示安装确认。

## 准备新版本

1. 在 `app/src/main/AndroidManifest.xml` 增加 `versionCode`，更新 `versionName`。
2. 使用与已经分发的 APK **相同的签名密钥**构建，备份该密钥且不要提交到 Git。`-SigningKey` 可以显式指定已有密钥；`-OutputApk` 可以指定独立输出路径。当前构建脚本沿用开发密钥的 `android` 口令，不要误换成新生成的密钥。
3. 运行测试和发布文件生成器：

```powershell
.\run-ota-tests.ps1 -JavaHome 'C:\path\to\jdk-17'
.\prepare-ota.ps1 -ApkPath '.\TouchXbox-0.5.6.apk' `
  -JavaHome 'C:\path\to\jdk-17' -BuildTools 'C:\path\to\build-tools\34.0.0' `
  -ChangelogFile '.\release-notes.txt' -OutputDirectory '.\release'
```

后续发布时，先下载上一版的 `latest.json`，增加 `-PreviousManifest '上一版-latest.json'`，生成器会检查新版本号递增、签名证书未改变。所有元数据都从实际签名 APK 中读取；输出名称包含版本与摘要，避免覆盖旧附件。

4. 本地运行 `python scripts/publish-release.py --tag v0.5.6 --validate-only`，将源代码和 `release/` 中经过检查的 APK、`latest.json` 一起提交。`release/` 是唯一允许提交 APK 的目录；密钥仍保留在本机。
5. 创建并推送对应版本的 Git tag，例如 `v0.5.6`。仓库的 `Publish APK and OTA metadata` 工作流会创建 Draft Release，上传 APK、`latest.json` 和独立授权 CMD，重新下载附件校验后，再发布为正式且最新的 Release。已有公开 Release 不会被覆盖；重复运行只验证已有附件是否一致。
6. 从匿名最新版本链接重新下载 `latest.json` 和 APK，核对 SHA-256、大小、版本及签名。再在安装着上一版本的设备上验证“检查 → 下载 → 系统安装 → 保留模板”的完整流程。

`prepare-ota.ps1` 只生成本地发布文件，不上传、不创建 Release。单个 APK 当前限制 95 MiB。

发布流程使用 GitHub Actions 的仓库内临时令牌，只授予本仓库的 `contents: write`。SSH 部署密钥负责推送代码及 tag；APK 在本地完成签名，签名私钥不上传到仓库或 Actions。

## 更换更新站点

更新源集中在 `OtaSource.java` 的 `BASE_URL`。下一版可以改为其他 HTTPS 静态目录，或另一个仓库的 `https://github.com/OWNER/REPO/releases/latest/download/`，继续提供相同格式的 `latest.json` 与 APK 附件。

迁移时把“切换更新源的版本”也放在旧更新源中，让旧版用户能升级过来；不要立即删除旧入口。设备安装此新版后才会采用新地址，修改远端仓库地址本身不会改变已安装 APK 内的配置。若新站使用其他 CDN，还需在 `OtaSource.allows` 中明确允许该 CDN 域名。

## 元数据格式

```json
{
  "schemaVersion": 1,
  "packageName": "dev.touchxbox.pad",
  "versionCode": 11,
  "versionName": "0.5.6",
  "minSdk": 26,
  "apkFile": "由生成器填写.apk",
  "size": 123456,
  "sha256": "由生成器填写的64位十六进制SHA256",
  "signerSha256": "由生成器读取的签名证书SHA256",
  "publishedAt": "2026-10-08T00:00:00Z",
  "changelog": "本次更新说明"
}
```

以上仅为字段示意，不能用示例值发布。应用只从其已配置目录解析安全的 APK 文件名；不执行更新信息内的脚本或命令。
