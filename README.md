# 旅途排排榜 TripRankApp

Android 8.0+ 的旅游项目排行榜应用，当前版本 1.6。与记账 App 分仓库维护。

## 功能

- 为旅游景点、美食等项目添加名称、描述与多张照片。
- 拖动项目进行从夯到拉的分档，同档内部也能调整顺序。
- 多张工作表作为项目筛选视图：同一项目可出现在多张表，各表独立保存档位及排序，共享项目资料。
- 项目库支持搜索、勾选，并显示首图缩略图与简短描述。取消勾选只移除当前表引用；删除项目会影响所有工作表。
- 纳入其他工作表的项目时取并集去重，保留来源表及已有排名。
- 记住本机昵称与身份；App 内选择图片、编辑及导入/导出 JSON 备份。
- 扫码携带热点及邀请信息加入，无需再次输入访问码；Android 仍可能要求系统确认及授权。

## 实验性自动同步

每台手机先编辑本地 SQLite。配对后，双方 App 在前台且热点/局域网可达时约每 3 秒检查更新，无变更时不反复传照片。多人可以分别配对同一个共享节点，修改经该节点传播；离线修改在网络恢复后重试。

基于最后成功同步的共同快照进行记录级三方比较。单边变更传播，同时编辑保留冲突副本。已同步项目删除会传播，删除与编辑冲突保留编辑内容。首次配对合并双方已有内容。工作表名称支持单边更新与确定性冲突处理，工作表元数据删除不传播。

双端写入先远端后本机，并非分布式事务；双方成功才更新共同历史，并通过版本检查防止并发覆盖。同步写入前保留最近 5 份本机备份，可从 App 导出。单次 JSON（含照片）上限 32 MB。停止共享会暂停主动同步及自动接收，重新配对或展示邀请恢复。卸载或清除数据会删除本机内容、身份及同步历史。

普通浏览器编辑网页主机的数据，本地优先流程适用于 App。热点名称或地址改变可能需要重新扫码，手机热点能否互访取决于系统。

## 安装

在 [GitHub Releases](https://github.com/FunnyMudTureGoPee/TripRankApp/releases/tag/v1.6) 下载 `TripRankApp-v1.6.apk`。该安装包沿用原始签名，可覆盖安装之前的旅途排排榜版本，升级前建议导出备份。

`releases/v1.6/` 保存原签名安装包、校验值及版本说明。发布工作流只校验和上传现有 APK，不依赖 SDK 编译；私钥与密码不公开。

## 构建与测试

需要 Linux/macOS、JDK 17、Python 3、Android SDK platform 35 与 build-tools 35.0.0。直接调用 SDK 工具，不依赖 Gradle。

```sh
export ANDROID_HOME=/path/to/android-sdk
sdkmanager "platforms;android-35" "build-tools;35.0.0"
./build.sh
./tests/run.sh
```

输出为 `build/trip-rank.apk`。可用 `TRIPRANK_ANDROID_SDK`、`TRIPRANK_BUILD_TOOLS` 自定义路径。测试首次运行下载 Maven Central 的 `org.json:json:20240303`，也可通过 `JSON_JAR` 指定已有文件。

默认使用本机调试密钥，不能覆盖安装原签名 APK；不同 CI 运行的调试签名也可能不同。升级原安装版本必须使用单独保管的原始密钥：

```sh
export TRIPRANK_KEYSTORE=/private/path/trip-rank.p12
export TRIPRANK_KEYSTORE_PASSWORD_FILE=/private/path/password.txt
./build.sh
```

GitHub Actions 执行编译与 JVM 回归测试，并保存调试 APK 构建产物。SDK 安装显式选择 platform-tools，避免已移除的 tools 包。

## 项目结构与验证范围

`src/app/triprank/` 为 Android 界面容器、HTTP 服务、二维码、身份和同步逻辑；`assets/index.html` 为内置排行榜页面；`res/` 为应用资源。

`tests/` 覆盖 HTTP 合并、工作表引用、独立排序、共享照片描述、旧记录兼容、备份、身份归属、三方自动同步及二维码/代理。`TestHost.java` 是 QR 测试所用的内存存储辅助类。真实手机相机、系统网络授权、热点断线恢复和前后台切换仍需实机验证。

二维码依赖 ZXing Core 3.5.3，遵循 Apache 2.0，许可证保存在 `libs/ZXING-LICENSE.txt` 和 `assets/THIRD_PARTY_LICENSES.txt`。org.json 仅供 JVM 测试，遵循上游许可证，不打包到 APK。本仓库未为原创代码另行指定开源许可证。
