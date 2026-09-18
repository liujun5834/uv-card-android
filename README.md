# UV卡片实拍合成器 V9.1 - 原生 Android APP

这是把当前 HTML 版功能改成原生 Android 绘图管线的工程，重点解决手机上卡顿的问题。

## 原生版为什么更快

- 移动、缩放、单边拉伸：只修改 Android Matrix / Canvas 绘制，不再逐像素重算整张图。
- 黑字识别和 UV 高光：只在上传图片或调整相关参数时重新预处理。
- 预览：GPU Canvas 实时绘制。
- 导出：只在保存 PNG 时进行一次全分辨率渲染。

## 当前功能

- 真实白卡照片作为物理底图
- 上传要印上去的图片
- 一键铺满“卡片区域”并居中，而不是铺满整张照片
- 四角定位点可拖动
- 上下左右移动
- 放大 / 缩小
- 上下左右单边拉升 / 缩回
- 真实圆角裁切
- 印刷浓度
- 实物卡纹理保留
- 白底保护
- 根据实物卡底图自动调色
- 人物肤色压暗调节（默认按确认样张略暗）
- 头像边缘清晰度调节（减少白/灰边和过度羽化）
- 只给黑色文字 / 数字加 UV
- 严格文字识别
- UV 强度、镜面高光、凸起深度、粗糙度、毛边
- PNG 保存到 Pictures/UVCard

## 生成 APK

### 方法 1：Android Studio
打开项目后直接 Build APK。

### 方法 2：GitHub Actions（手机也能用）
把整个工程上传到 GitHub 仓库，打开 Actions -> Build Android APK -> Run workflow。
完成后在 Artifacts 下载 `UVCard-V9.1-debug-apk`，里面就是 `app-debug.apk`。

> Debug APK 可直接安装测试。正式长期使用建议以后再做 release 签名。
