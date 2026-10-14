# BeautyCamera 原生插件（云端编译版）

专业相机原生插件：Camera2 + OpenGL ES 实时美颜（磨皮/美白）+ 对焦 / 变焦 / 曝光补偿 / 白平衡 / 闪光模式 / 前后摄。

## ❗ 不再需要 uniapp-release.aar
工程用「编译期 API 桩」（`stub` 模块）代替 uni 引擎基础包。桩类**只参与编译、不会打进 aar**；
运行时真正的 `UniModule` / `UniJSMethod` / `UniJSCallback` 由你自定义基座里的 uni 引擎提供。
因此你**不需要**从 HBuilderX 目录里翻找 `uniapp-release.aar`。

## 1. 推到 GitHub 触发云端编译
```bash
cd BeautyCameraPlugin
git init
git add .
git commit -m "BeautyCamera plugin"
gh repo create BeautyCameraPlugin --private   # 或网页新建后 git remote add
git branch -M main
git push -u origin main
```
推送后自动触发 Actions → `Build BeautyCamera AAR`（GitHub 免费额度，无需本地装任何东西）。

## 2. 下载产物
GitHub 仓库 → **Actions** → 最新运行 → **Artifacts** → 下载 `BeautyCamera.aar`。

## 3. 放进 HBuilderX 工程
把 `BeautyCamera.aar` 复制到：
```
你的工程/nativeplugins/BeautyCamera/android/aar/BeautyCamera.aar
```
（没有 `aar` 目录就新建；`package.json` 里 `integrateType` 须为 `aar`。）

## 4. 制作自定义基座并运行
HBuilderX → 运行到手机 → **制作自定义基座**（会把 aar 打进 APK）→ 安装并选该基座运行。

## 插件 JS 接口（uni.requireNativePlugin('BeautyCamera')）
| 方法 | 参数 | 说明 |
|------|------|------|
| init | {} | 初始化并启动预览，callback 返回 {code,msg} |
| capture | {} | 拍照，callback 返回 {code,path,uri} |
| setBeauty | {beauty:0~100, white:0~100} | 磨皮 / 美白 |
| focus | {x:0~1, y:0~1} | 点击对焦（归一化坐标） |
| setZoom | {zoom:1~maxZoom} | 数码变焦 |
| setExposure | {ev:int} | 曝光补偿 |
| setWhiteBalance | {mode:'auto'|'daylight'|'cloudy'|'fluorescent'|'incandescent'} | 白平衡 |
| setFlash | {mode:'off'|'on'|'auto'|'torch'} | 闪光 |
| switchCamera | {} | 前后摄切换 |
| getInfo | {} | 返回 {maxZoom,facing} |
| release | {} | 释放资源 |
