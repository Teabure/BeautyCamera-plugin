package com.mytools.beautycamera

/**
 * 预览/拍照共用的 GLSL ES 1.00 着色器。
 * 顶点：用 uTexMatrix（SurfaceTexture 变换 * 旋转/镜像）映射纹理坐标。
 * 片元：5x5 盒式模糊做皮肤磨平 + 提亮做美白，强度由 uBeauty / uWhite(0..1) 控制。
 */
object Shaders {
    const val VERTEX = """
        attribute vec2 aPosition;
        attribute vec2 aTextureCoord;
        varying vec2 vTextureCoord;
        uniform mat4 uTexMatrix;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTextureCoord = (uTexMatrix * vec4(aTextureCoord, 0.0, 1.0)).xy;
        }
    """

    const val FRAGMENT = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES uTexture;
        uniform float uBeauty;   // 磨皮 0..1
        uniform float uWhite;    // 美白 0..1
        uniform vec2 uTexel;
        void main() {
            vec4 center = texture2D(uTexture, vTextureCoord);
            vec4 sum = vec4(0.0);
            float n = 0.0;
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    vec2 o = vec2(float(x), float(y)) * uTexel * 1.5;
                    sum += texture2D(uTexture, vTextureCoord + o);
                    n += 1.0;
                }
            }
            vec4 blur = sum / n;
            vec4 smoothC = mix(center, blur, uBeauty * 0.6);
            smoothC.rgb = smoothC.rgb * (1.0 + uWhite * 0.18);
            gl_FragColor = smoothC;
        }
    """
}
