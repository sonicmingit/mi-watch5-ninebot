# v0.0.9-port6 · 单手表应用

手表端把配对授权页和通信服务合入九号出行。未登录时在同一个应用内配对；授权后自动进入车辆页；日常由手表自身联网查看车辆信息。手机仍需官方九号出行和本 Release 的手机授权助手。

实机验证：国行 Xiaomi Watch 5 / Android 14 与小米手机官方九号 6.10.12。清空手表凭据后，手机真实回传非空账号和手表令牌；手表自动进入车辆页。关闭两端助手并冷启动，两路车辆列表请求成功、1 辆车、零错误，电量和续航正常。未执行车辆控制命令。

安装：手机装 `ninebot-phone-authorization-v0.5.apk`；手表装 `ninebot-watch-unified-v0.0.9-port6.apk`。打开手表九号，未登录时点“开始配对”；手机助手填手表显示的 `IP:38766`，两端核对完整配对码并确认；打开已登录的手机官方九号，手表会自动显示车辆。日后退出登录，可在手表车辆页下滑找到“退出登录”。详见 [README](README.md)。

Release 还提供旧双应用方案的 `ninebot-watch-authorization-v0.5.apk` 和 `ninebot-watch-legacy-restore-v0.0.9-port5.apk`。恢复包版本码 90006，供单包版同签覆盖回退；原双应用源码资料见 [`legacy/`](legacy/README.md)。保留现有数据时不要先卸载手表主应用。

| 文件 | SHA-256 |
| --- | --- |
| `ninebot-watch-unified-v0.0.9-port6.apk` | `252470245c9ee7fd6415d5988f075448e7aef7751e718fc5a3233a299ec6c5fc` |
| `ninebot-phone-authorization-v0.5.apk` | `aa65b9e5993228dcc347169cf80153398605be13c1be07e44db2b5fdd89db18a` |
| `ninebot-watch-authorization-v0.5.apk` | `a22632716e724807cf6699dda7d1e420d3e9af7d65e6caa3641fec8bea97237a` |
| `ninebot-watch-legacy-restore-v0.0.9-port5.apk` | `d783cc3515bd5b37dd946b9fc610f6210152cf7e35d23d0a1a52b5c9701429a1` |

`SHA256SUMS.txt` 作为第五个 Release 资产提供。手机官方九号 APK 不随本项目发布。
