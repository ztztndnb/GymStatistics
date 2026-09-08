# GymStatistics

离线优先的安卓训练记录应用 + 局域网网页查看。

## 功能

- **记录每日训练动作**:一次保存录入一个动作,字段为“数据(数值)+单位+个数(每组)+组数”;单位默认可选 无单位/kg/lbs,也可自定义输入。展示格式:`60kg · 10 × 3组`,组数为 1 时只显示个数 `60kg · 10个`。备注框置于对话框底部;添加记录自动落入当前选中的日期,无需手填日期(对话框顶部会显示“将添加到 …”)。
- **每日动作分别展示/修改/删除**:主页面按选中日期把每个动作单独一张卡片展示(不带日期头),每张卡片各有“编辑”(预填原值)与“删除”按钮;删除前会弹出**确认窗口**;编辑/删除只影响该条动作。
- **日期选择系统**(仿常见饮食/健康 App):顶部“今天 ▾”点击弹出月历(支持上/下月切换、回今天),下方以“周”为单位列出 日…六 七天可选,选中日高亮;列表按选中日期过滤,新建记录默认使用选中日期。**主页面左右滑动**可快速切换上/下一天(左滑→下一天、右滑→上一天),并带**滑入过渡动画**。
- **平滑过渡动画**:主页面切换日期、进出“历史动作/局域网同步”,以及历史“列表↔动作详情”之间,均有滑入/滑出+Fade 的流畅过渡动画;系统返回/侧滑同样带过渡回到上一级。
- **历史动作 + 趋势图**(顶栏“历史”):进入后先列出所有添加过的动作(同名视为一种,按被添加次数降序);点击某动作**先展示该动作的所有历史记录**(按 单位字典序降序(无单位最低)→ 数据降序 → 总个数(个数×组数)降序 → 组数降序 → 个数降序)与趋势图;随后**点击某一条记录**才按该条的数据填入“记录训练”页。
  - **疲劳预警**:某动作若在 3 天内做过,则在列表该动作右侧显示**黄色 ⚠ 疲劳预警**,并写明上次日期与“N 天前”;可在该动作详情页用“疲劳预警”开关**单独关闭**。
  - **趋势图按单位拆分**:动作若含多个单位(如 kg/lbs),数据与总个数趋势会**每个单位各生成一张图**(如“数据趋势(kg)”“数据趋势(lbs)”);纵轴显示单位与刻度数字,横轴标注具体日期。
- **离线优先**:数据以 JSON 保存在手机本地,健身房没网也能正常记录。
- **局域网同步**:底部“局域网同步”按钮进入独立页面,可**开关同步**、显示局域网链接、并**一键复制链接**;手机作为数据源,内嵌 HTTP 服务,同一 WiFi/局域网下的电脑浏览器即可实时查看数据,并可导出 JSON / CSV。导出的 CSV 为 **UTF-8(含 BOM)** 编码以兼容 Excel 中文;导出文件名带时间戳(如 `gymstatistics_20260905-132249.csv`)。
- **手机为权威来源**:单一数据源,结构简单、无冲突。
- **从电脑导入数据**:在“局域网同步”页点“从电脑导入数据(选文件)”,选择导出的 JSON 文件,可**全部导入**或**按日期导入**;若导入日期与现有数据重复,**会提示用户**选择“合并(覆盖)”或“跳过重复”。同时服务端提供 `POST /api/import`(body 为 `{"mode":"all|date","date":"...","overwriteDuplicates":bool,"sessions":[...]}`),电脑可经局域网回传数据,并返回 `{added,duplicates,skipped,overwritten}`。
- **记录动作/同步按钮**:主页面底部垂直排列“记录动作”与“局域网同步”两个按钮,不再使用右下角悬浮按钮;原有的局域网同步开关卡片已移除,统一到同步页管理。
- **系统返回走返回键/侧滑**:在“历史动作”列表、“具体动作详情”以及“局域网同步”页面按系统返回(或手势侧滑)会**返回上一级**(历史详情→历史列表→主界面;同步页→主界面),不再是直接退出应用。

## 技术栈

- Kotlin 2.0.21 · Jetpack Compose · Material3
- kotlinx.serialization(JSON 持久化,存储于 `filesDir/workouts.json`)
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)(内嵌局域网 HTTP 服务)
- Android Gradle Plugin 8.7.3 · Gradle 8.10 · compileSdk 36 / minSdk 26
- 依赖仓库:`google()`,`mavenCentral()`

## 构建

```powershell
# 调试版(首次构建会下载 AGP 与依赖,需联网)
.\gradlew.bat assembleDebug

# 正式版 release(已开启 R8 压缩,需先配置密钥库,见下)
.\gradlew.bat assembleRelease
```

APK 输出:
- 调试版:`app\build\outputs\apk\debug\app-debug.apk`
- 正式版:`app\build\outputs\apk\release\app-release.apk`(已签名,versionName `1.0.6`)

> 注:本仓库的 `local.properties` 指向本机 SDK,`build/`、`.gradle/`、`keystore.properties`、`keystore/` 等已被 `.gitignore` 忽略。

## 正式版发布信息
- 当前版本:**1.0.6**(`versionCode=7`,`versionName="1.0.6"`)。
- Release 构建启用 **R8 压缩 + 资源收缩**,并保留 `kotlinx.serialization` 序列化器与 `NanoHTTPD` 的混淆规则,release 包约 1.2MB。
- 签名:release 使用根目录 `keystore.properties` 指向的 `keystore/gymstatistics-release.jks`(alias `gymstatistics`)。该文件已被 gitignore,不进入版本库。
- ⚠️ 该密钥库为本地生成的示例,口令较弱且已出现在本会话命令中;**若要用于应用商店正式上架,请换用你自己生成、妥善保管的强口令密钥库**,且务必备份好——丢失将无法对后续版本用同一签名更新。

## 运行

```powershell
# 模拟器安装
adb install -r app\build\outputs\apk\debug\app-debug.apk
# 启动
adb shell am start -n com.gymstatistics/.MainActivity
```

## 局域网同步(电脑查看)

1. 手机和电脑连接到 **同一个 WiFi / 局域网**。
2. 打开 App,把「局域网同步」开关打开。
3. 界面会显示形如 `http://192.168.1.5:8931` 的地址(端口固定 `8931`)。
4. 电脑浏览器打开该地址即可查看训练数据并导出 JSON / CSV。

- 数据来自手机,手机是唯一权威来源;电脑端是查看/导出窗口。
- 页面每 5 秒自动刷新,数据实时反映手机内容。

## 测试用命令行参数(可选)

`MainActivity` 支持两个调试用 Intent 附加项,便于自动化验证:

```powershell
# 空数据时注入演示数据,并自动开启局域网同步
adb shell am start -n com.gymstatistics/.MainActivity \
  --ez extra_seed_demo true --ez extra_sync true
```
