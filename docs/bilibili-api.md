# Bilibili 接口使用说明

本文说明本仓库如何请求 B 站接口：客户端、签名、Cookie，以及每个端点由谁调用、传什么、读什么。端点常量集中在 `core/network/BiliApiEndpoints.kt`。`AppBase`（`https://app.bilibili.com`）已声明，当前没有调用方。

## 调用链

```text
页面 ViewModel
  -> VideoRepository / PlaybackRepository / AuthRepository
    -> BiliApiClient
      -> OkHttp（API 客户端带 Brotli）
        -> api.bilibili.com / passport.bilibili.com / comment.bilibili.com
```

`VideoRepository` 是首页、搜索、动态、历史、空间和关注的门面，内部再分到 `HomeVideoRepository`、`SearchVideoRepository`、`UserFeedRepository`、`SpaceVideoRepository`。播放相关由 `PlaybackRepository` 转给弹幕、评论、雪碧图和空降仓库。

`AppContainer` 创建两个 OkHttp 客户端：

| 客户端 | 超时 | 拦截器 | 用途 |
| --- | --- | --- | --- |
| API | 连接/读/写各 15 秒 | Brotli；默认补 User-Agent 和 Referer | JSON 与弹幕、雪碧图字节 |
| Playback | 同上 | 补 User-Agent、Referer、Origin，不挂 Brotli | Media3 拉 DASH 分片 |

默认请求头在 `BiliHeaders`：

- User-Agent：桌面 Chrome 标识。
- Referer：`https://www.bilibili.com/`。
- Origin：`https://www.bilibili.com`。
- 空间接口单独改成 `https://space.bilibili.com` 和对应空间页 Referer。

请求若带内部头 `X-BiliTV-Omit-Referer: 1`，API 拦截器会去掉 Referer。业务代码目前没有主动设置这个头。

Cookie 由 `BiliHeaders.cookie` 拼接，只放有值的字段：`SESSDATA`、`bili_jct`、`buvid3`、`buvid4`、`DedeUserID`。会话存在 DataStore，不写日志。

JSON 使用 kotlinx.serialization，忽略未知字段。HTTP 非 2xx 抛 `BiliNetworkException`；响应体 `code != 0` 抛 `BiliApiCodeException`。搜索和空间在签名失败时有未签名或刷新 key 的回退；弹幕和评论有主接口失败后的旧接口回退。

## 签名

### WBI

需要 WBI 的接口先取 `img_key` 与 `sub_key`，再由 `WbiSigner` 生成 `wts` 和 `w_rid`。

1. `WbiKeyRepository` 读本地缓存。key 仍新鲜则直接用。
2. 否则 GET `/x/web-interface/nav`，从 `data.wbi_img.img_url`、`sub_url` 的文件名取出 key，并写入 DataStore。刷新失败时继续用旧缓存。
3. 签名时去掉参数值里的 `!'()*`，按 key 排序，拼查询串，对 `查询串 + mixinKey` 做 MD5，得到 `w_rid`。mixin 表和 32 位截取规则是固定的纯 Kotlin 实现，不引入额外加密库。

使用 WBI 的接口：推荐、搜索、播放地址、空间投稿。搜索在签名请求失败后会再试一次不签名的同一 URL。

### TV 登录

`TvLoginSigner` 给参数加上固定的 TV `appkey`、`ts`，按 key 排序拼接后把 `appsec` 附在末尾做 MD5，写入 `sign`。这是 B 站 TV 登录公开的 appkey/appsec，写在 `TvLoginSigner` 里。`local_id` 固定为 `0`。

## 登录与设备

| 方法 | 路径 | 调用方 | 说明 |
| --- | --- | --- | --- |
| POST | `https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code` | `AuthRepository.generateTvQrCode` | 查询参数即签名后的 `local_id`。成功读 `data.url`、`data.auth_code`，由 ZXing 画成二维码 |
| POST | `https://passport.bilibili.com/x/passport-tv-login/qrcode/poll` | `AuthRepository.pollTvLogin` | 额外带 `auth_code`。`code`：`0` 成功，`86039` 等待，`86090` 已扫码，`86038` 过期。成功从 `data.cookie_info.cookies` 取出 `SESSDATA`、`bili_jct`、`buvid3`、`buvid4`，并保存 `mid` |
| GET | `https://api.bilibili.com/x/web-interface/nav` | 登录后 `refreshUserProfile`，以及 `WbiKeyRepository` | 登录态下读 `mid`、`uname`、`face`、`vip.status`。同时是 WBI 图片 key 的来源 |
| GET | `https://api.bilibili.com/x/frontend/finger/spi` | `SpaceVideoRepository` | 空间请求前补设备号。读 `data.b_3`、`data.b_4` 作为 `buvid3`、`buvid4`。失败则本地生成 `{uuid}infoc` |
| POST | `https://api.bilibili.com/x/internal/gaia-gateway/ExClimbWuzhi` | 同上，拿到新 buvid 后 | 表单字段 `payload` 是一段风控 JSON。失败只记日志，不阻断空间列表 |

二维码轮询绑定页面生命周期，应用进后台后停止。

## 首页、搜索与用户流

未登录也可以拉推荐、热门、分区、搜索和视频详情。动态、历史、关注和心跳需要 `SESSDATA`；关注修改和心跳还要 `bili_jct` 作为 `csrf`。

| 方法 | 路径 | 调用方 | 参数与结果 |
| --- | --- | --- | --- |
| GET | `/x/web-interface/wbi/index/top/feed/rcmd` | 首页推荐 | WBI。`fresh_idx`、`fresh_type=4`、`ps=20`。读 `data.item[]`，映射为 `VideoSummary` |
| GET | `/x/web-interface/popular` | 首页热门 | `pn`、`ps=20`。读 `data.list[]` |
| GET | `/x/web-interface/newlist` | 首页分区 | `rid` 为分区 tid，`pn`、`ps=20`。读 `data.archives[]`。分区 tid：动画 1、音乐 3、游戏 4、番剧 13、知识 36、舞蹈 129、生活 160、电影 181、科技 188、美食 211 |
| GET | `/x/web-interface/wbi/search/type` | 搜索 | WBI，失败再试未签名。`search_type=video`、`keyword`、`page`、`pagesize=20`、`order`。读 `data.result[]` |
| GET | `https://s.search.bilibili.com/main/suggest` | 搜索建议 | `term`、`main_ver=v1`、`highlight` 为空。读 `result.tag[].value` |
| GET | `/x/polymer/web-dynamic/v1/feed/all` | 动态 | 需登录。`type=all`，翻页带 `offset`。读 `data.items`、`data.offset`、`data.has_more` |
| GET | `/x/web-interface/history/cursor` | 历史 | 需登录。`ps` 默认 30，继续翻页带 `view_at`、`max`。读 `data.list` 和 `data.cursor` |
| GET | `/x/web-interface/archive/related` | 播放页相关推荐 | `bvid`。`data` 为稿件数组 |
| GET | `/x/space/wbi/arc/search` | UP 主投稿 | WBI。`mid`、`pn`、`ps=25`、`order`、`index=1`、`order_avoided=true`、`platform=web`、`web_location=333.1387`。读 `data.list.vlist`。Cookie 带登录态和 buvid。HTTP 412/429/5xx 会按交互或恢复模式延迟重试；恢复模式还会刷新 WBI 或退回未签名请求 |
| GET | `/x/relation` | 是否关注 | 需登录。`fid=mid`。`data.attribute` 为 2 或 6 视为已关注 |
| POST | `/x/relation/modify` | 关注/取关 | 表单 `fid`、`act`（1 关注，2 取关）、`csrf=bili_jct` |

搜索排序取值由设置/界面传入，默认 `totalrank`。空间默认排序 `pubdate`。

## 播放

### 稿件与地址

| 方法 | 路径 | 调用方 | 说明 |
| --- | --- | --- | --- |
| GET | `/x/web-interface/view` | `resolveCid`、`getVideoMetadata` | `bvid`。元数据读 `aid`、标题、`owner`、`stat`、`pubdate`、`pages[]`（`cid`、`page`、`part`、`duration`）。只有 bvid 时用顶层 `cid` 或第一页 `cid` |
| GET | `/x/player/wbi/playurl` | `getPlaybackInfo` | WBI。`bvid`、`cid`、`qn`、`fnval`、`fourk=1`。请求头带播放 Cookie。读 `data.dash.video`、`data.dash.audio` 和清晰度列表 |
| GET | `/x/player/online/total` | 播放中在线人数 | `aid`、`cid`。读 `data.total`，空则读 `data.count`。刷新合并进播放器进度循环，不单独轮询 |
| POST | `/x/click-interface/web/heartbeat` | `reportProgress` | 已登录且有 `bili_jct` 才发。查询参数含 `bvid`、`cid`、`played_time`、`real_played_time`、`start_ts`、`csrf`。失败不影响本地进度 |

`qn` 来自用户画质偏好或当前请求。`fnval` 从 16（DASH）起步，设备支持且偏好不是强制 H.264 时或上 64（H.265）；Auto 或显式 AV1 且设备支持时再或上 1024。返回的视频轨按编码偏好和 `MediaCodec` 探测结果过滤，仍保留 H.264 回退。

分片不走 `BiliApiClient`。`BiliMediaDataSourceFactory` 把同一套播放头交给 Media3，每个分片请求都带 User-Agent、Referer、Origin 和 Cookie。

### 弹幕、评论与预览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/x/v1/dm/list.so` | 主弹幕。`type=1`、`oid=cid`。字节流可能是 XML、gzip 或 deflate，在 IO 线程解压并用 XmlPullParser 解析 `<d p="...">`。时间、模式、颜色来自 `p` 的前几段；滚动模式保留 1/2/3/6，底部 4，顶部 5。上限 5000 条 |
| GET | `https://comment.bilibili.com/{cid}.xml` | 主接口没有解析出弹幕时的旧 XML |
| GET | `/x/v2/reply/main` | 主评论。`type=1`、`oid=aid`、`mode=3`、`ps=20`、`plat=1`、`web_location=1315875`、`seek_rpid=0`、`pagination_str={"offset":""}`。合并 `hots` 与 `replies`，按 `rpid` 去重 |
| GET | `/x/v2/reply` | 主评论为空时的回退。`sort=2`、`pn=1`、`ps=20`。读 `data.replies` |
| GET | `/x/player/videoshot` | 快进预览索引。`bvid`，有 cid 时附带。读 `data.image`、行列尺寸，以及 `pvdata` 二进制时间轴。图片 URL 把协议相对地址补成 https |

雪碧图图片再用带 Cookie 和 Referer 的 `getBytes` 下载，不走 Coil，也不强制 `RGB_565`。

## 空降助手

空降不是 B 站接口。开启后 `AirJumpRepository` 请求：

```text
GET https://bsbsb.top/api/skipSegments
  videoID={bvid}
  category=sponsor|intro|outro|interaction|selfpromo
```

响应是 JSON 数组，每段含 `segment: [startSeconds, endSeconds]`、`category`、`UUID`。HTTP 404 视为没有片段。结果只用于进度条标记和跳过提示。
