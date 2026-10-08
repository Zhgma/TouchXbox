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
