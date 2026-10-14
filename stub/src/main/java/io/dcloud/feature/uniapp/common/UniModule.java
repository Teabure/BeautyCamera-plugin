package io.dcloud.feature.uniapp.common;

import io.dcloud.feature.uniapp.UniSDKInstance;

/**
 * 编译期桩：与 uni 引擎 io.dcloud.feature.uniapp.common.UniModule 同名同结构。
 * 运行时由宿主 App 的 uni 引擎提供真实实现。
 */
public abstract class UniModule {
    public UniSDKInstance mUniSDKInstance;
}
