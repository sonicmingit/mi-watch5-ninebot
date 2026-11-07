# port6 单手表 APK 测试

用户要求手表端合为一个应用，并保留现有双应用方案。旧 port5 主程序、手表授权助手及 2026-11-07 重装 ZIP 均未修改；手机仍安装官方九号和手机授权助手。合并版同包 `com.ninebot.ninebot`、同签、versionCode 90005，覆盖安装用于测试；回退用另备的 `0.0.9-port5-restore / 90006` 同签包覆盖，不清数据；旧 port5 原件不变。

实现：原 OMS SDK 的手表服务定位由独立助手包改为本包；把既有 TLS 配对服务与圆屏配对页放入主 APK 的 `classes2.dex`，注册私有服务和 Activity。原 SDK 初始化完成且无令牌时，在主界面上方打开内置授权页；授权令牌写入后回到九号主界面。使用原包名维持原生加密兼容。已有登录日常直接看车。退出登录仍清除 uid/token 后关闭进程，下次打开进入授权页。

构建：`build_port6_unified.py`，增量清单 `unified_manifest.py`。基于 SHA-256 固定的 port5，保留原资源及业务，补入本地服务与界面；签名前核对本机签名备份，签名与 zipalign 通过。产物 `download/test/ninebot/ninebot-port6-unified.apk`。

实机已通过：同签覆盖安装，已有登录显示实际车辆与 49% 电量、74 km 续航；测试器在应用私有目录暂存令牌后清空，冷启动自动进入内置授权页；第二轮修复启动顺序后，系统服务记录明确显示原 SDK 对 `com.ninebot.ninebot/local.ninebot.bridge.ProbeService` 的 `hasBound=true`。首轮启动页太早导致 SDK 未绑定，该构建已被第二轮替代。

先前手机锁屏时仅完成内置页与绑定验证；手机解锁后本页末尾记录真实授权与冷启动闭环。独立旧助手仍安装，旧 port5 APK 和四包重装 ZIP 保留。

当前交付副本：`watch/releases/九号出行单包_2026-11-07/九号出行_0.0.9-port6-unified_手表.apk`。SHA-256 `252470245c9ee7fd6415d5988f075448e7aef7751e718fc5a3233a299ec6c5fc`。后续已完成真实回传与独立读车验证；旧版恢复见双应用重装包。

已生成恢复包：`watch/releases/九号出行单包_2026-11-07/九号出行_0.0.9-port5-restore_手表双应用版.apk`，SHA-256 `d783cc3515bd5b37dd946b9fc610f6210152cf7e35d23d0a1a52b5c9701429a1`。验证原 port5 的两个 DEX 与 resources.arsc 字节完全一致，只提升版本码供同签覆盖回退；保留旧独立助手。

## 22:24 真实授权闭环通过

用户解锁手机后，测试器先备份原令牌到应用私有目录并暂清 uid/token。单 APK 从无令牌冷启动进入内置授权页；原 SDK 对本包 ProbeService 的绑定为 hasBound=true。手表与手机助手显示相同完整配对码，核对后两端确认；启动手机官方九号，内置服务收到 `requestInitInfo` 响应，日志只证明 token_present=true、uid_present=true，不记录值。手表自动跳转到 DeviceDetailActivity。随后分别 force-stop 手机授权助手、旧独立手表助手和手表主程序，再冷启动主程序：两路请求 successCount=2、deviceCount=1、error_events=0、token_invalid=0；实机显示真实车辆、49% 与 74 km，证据 `download/logs/ninebot/20261107-222400-status.json` 和 `download/test/ninebot/port6-final-independent-home.png`。同签测试器复查本机保存的新 uid/token 均存在，清除临时旧令牌备份后卸载测试器。此轮没有执行车辆控制动作。

至此“单包内置授权 → 自动车辆页 → 关闭助手后独立冷启动读车”在当前设备通过。后续官方版本和长期令牌更新仍按实际运行观察。旧双应用安装包与回退包继续保留。
