# TouchXbox 0.5.5 验证记录

2026-10-08。正式 APK 已覆盖安装到 DBR-W10 平板（Android 12 / API 31，鸿蒙 4.2），versionCode=10、versionName=0.5.5。从设备重新拉取的 APK 与交付文件 SHA-256 相同：`0b67f45d98486d42d32fbc231914e3a96d1dda5ecb7747c3cab3e156822f6e73`。构建的 v2/v3 签名检查通过。

## 图标、名称和首次安装

- 使用独立 QA 包运行与正式版相同的应用代码，验证首次安装首页为空、没有活动模板；重复读取和退出未保存的编辑都不会生成模板。真实旧版布局仍能迁移一次，删除全部模板后不会重新出现。
- 实际 Android PackageManager 验证四种协议图标资源互不相同，名称为 TouchXbox、TouchNS、TouchPS、TouchFPV；每次切换后只存在一个启用的桌面入口，模板仍保留。
- 设置提供自动跟随、原始图标、Xbox、NS、PS、FPV 六项。固定 PS 后记录 NS 激活，外观仍为 PS；改回自动后显示 NS；固定原始图标后记录 FPV 激活，仍显示原始图标。原始选项使用未改动的 `ic_pad` 资源。
- 在实际 Android 设置选择框中点击 PS 选项，首页名称随之更新，模板的 FPV 协议未改变；无效选择被拒绝。最终选择原始图标并记录 FPV，落盘 XML 中 `mode=4`、`protocol=3`。
- 0.5.5 已验证选择落盘，未单独进行新固定模式的冷启动测试。协议激活测试直接调用外观更新 API，没有创建输入设备；不能据此声称重新验证了四协议的整条系统输入路径。
- 原生 Android View 离屏绘制结果已目视检查：选择框六项、图标和单选状态清楚，空首页有添加提示。平板当时熄屏，因此这些是实际 View 渲染图，不是整屏实拍。最初的零尺寸离屏绘制测试失败后修复了测试辅助代码，最终测试通过，未修改产品布局来迎合测试。
- 证据：`evidence/tablet-v055-installation-test.txt`、`tablet-v055-persisted-appearance.xml`、`tablet-v055-appearance-picker-view.png`、`tablet-v055-fresh-empty-view.png`。最终四图标预览为 `launcher-icons-controller-preview.png`；早期 `launcher-icons-preview.png` 是已被替换的 Xbox 图案。

## FPV 方形摇杆范围

- FPV 两根摇杆现在独立限制横纵轴到 −1…1；右摇杆不会在对角线处被圆形归一化，四个角都能同时达到两个通道的满值。
- 右摇杆松手后双轴回中；左侧油门仍默认最低、纵轴保留上次值，横轴回中。固定和跟随样式都使用同一映射；其他三协议仍采用圆形范围。
- `FpvStickTest` 覆盖触点 → StickGesture → PadState → FpvCodec 的四角 0/65535 输出、对角线、越界、死区、固定/跟随起点、抬手/取消及油门保持。在主机和 Android 运行时均通过。本轮没有另外做实机手指拖动到系统事件的端到端复测。

## 检查与安装收尾

- 主机八组测试通过：ProtocolTest、AuthTest、LayoutTest、LayoutViewportTest、ResponsiveLayoutTest、MotionReportTest、ShoulderInteractionTest、FpvStickTest。Android 测试也包含模板往返保存和 FPV 测试。
- 正式版使用 `adb install -r` 覆盖安装，没有清空数据、运行激活脚本或新增授权。安装前后的悬浮窗 app-op 均为既有 `allow`，原 shell 桥接 PID 14155 保持运行。
- 升级前后的唯一启用入口均为 `dev.touchxbox.pad/.LauncherNs`，保留原 NS 选择。当前生产包不可用 run-as 读取私有数据；本轮没有重新导出生产模板做逐字段比较，模板保留逻辑在隔离 QA 中验证。
- 曾并行安装独立 QA 包，导致桌面显示两个应用图标；QA 包已卸载。最终 `pm list packages dev.touchxbox` 只返回 `dev.touchxbox.pad`，启动器查询恰好一个入口。验证记录见 `evidence/tablet-v055-install-check.txt`。
- 本轮最终 0.5.5 只安装到 DBR-W10；HBN-AL00 手机未连接，没有宣称在手机复测。此前手机 NS 系统体感回传未打通的问题未在本轮修复，详见 [0.5.3 验证记录](evidence/validation-0.5.3.md)。更早记录：[0.5.2](evidence/validation-0.5.2.md)、[0.5.1](evidence/validation-0.5.1.md)。

实现依据：Android 官方 [activity-alias](https://developer.android.com/guide/topics/manifest/activity-alias-element) 与 [setComponentEnabledSetting](https://developer.android.com/reference/android/content/pm/PackageManager#setComponentEnabledSetting(android.content.ComponentName,%20int,%20int))。

## 0.5.8 FPV 布局模拟 Xbox（2026-10-08）

- 新增模板级 `fpvXbox` 开关，默认关闭；布局协议与 `outputProtocol()` 分开，FPV 方形摇杆、操控习惯和油门保持不随输出协议改变。开关保存、重开、关闭恢复、v9 导入导出及旧模板默认值均通过隔离 Android QA 验证。
- 主机 `run-tests.ps1` 的 13 组测试通过；新增 `FpvXboxTest` 覆盖三种模式、固定/浮动四角、油门启动/保持/重触/复位、辅助通道以及其他协议隔离。
- DBR-W10 实机使用同一 `PadState`、`UhidDevice` 和 Xbox 描述符，经 ADB shell 创建真实 UHID 设备，由独立 `TesterActivity` 接收系统 `MotionEvent`。三种模式各四个角均实际收到 X/Y 同时为 ±1，首次 12 个角及六次最低油门启动/复位事件经日志断言通过。加入自定义通道映射后的复测日志受系统日志环形缓冲区限制，保留了 Mode 1 / 3 的八个角及完整的自定义按键事件，均通过日志断言。系统设备名为 `Microsoft X-Box 360 Pad`。记录：`evidence/0.5.8/xbox-system-events.txt`。
- 编辑器视图已渲染并检查：`evidence/0.5.8/fpv-xbox-editor.png`。独立 QA 包 `dev.touchxbox.pad.qa058` 和临时设备端 APK 已移除，输入设备及 QA 测试进程均无残留，最后确认 `mWakefulness=Asleep`。
- 已生成并验证签名 `TouchXbox-0.5.8.apk`，SHA-256：`57e32e9595477140a250afd4361ad638e7f57eab5a25d6d73ff737f73ee2fe45`。构建验证未覆盖生产安装；发布时通过独立 OTA 包校验、签名连续性检查和 GitHub Actions 附件校验。
- 验证边界：实机覆盖设备创建、报告及系统轴事件；触点到报告由手势单元测试覆盖。尚未实测 Moonlight → Sunshine → Liftoff 的完整串流。Moonlight 12.1 的 `handleAxisSet`/`handleDeadZone` 源码没有斜向圆形归一化；模拟器自己的校准、死区、曲线需要在实际串流中确认。

- CH5～CH8 支持按档位自定义 Xbox 按键 / 方向键 / 满值扳机 / 键盘单键，未自定义的通道保留默认输出。`FpvAuxMappingTest` 覆盖跨通道和屏幕键盘共享同键、原子切档、复位不触发中档映射、键盘设备需求、配置快照隔离、其他协议隔离；Android 导入测试还拒绝无效通道、档位数和按键值。
- 实机收到 `KEYCODE_BUTTON_A` 和 `KEYCODE_SPACE` 的 DOWN / UP。CH5 与 CH6 同时映射空格时，CH5 退出不产生空格 UP，CH6 退出后才释放；reset 也产生空格 UP。编辑器真实选择器、保存重开和恢复默认输出测试通过，结果见 `evidence/0.5.8/fpv-xbox-ui-test.txt`，映射对话框见 `evidence/0.5.8/fpv-xbox-ch5-mapping.png`。
