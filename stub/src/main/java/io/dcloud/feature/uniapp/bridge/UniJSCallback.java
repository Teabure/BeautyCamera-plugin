package io.dcloud.feature.uniapp.bridge;

/**
 * 编译期桩：与 uni 引擎 io.dcloud.feature.uniapp.bridge.UniJSCallback 同名同签名。
 */
public interface UniJSCallback {
    void invoke(Object data);
    void invokeAndKeepAlive(Object data);
}
