package io.dcloud.feature.uniapp;

import android.content.Context;

/**
 * 编译期桩：与 uni 引擎 io.dcloud.feature.uniapp.UniSDKInstance 同名同签名。
 * 运行时由宿主 App 的 uni 引擎提供真实实现，本类不会打进 aar。
 */
public interface UniSDKInstance {
    Context getContext();
}
