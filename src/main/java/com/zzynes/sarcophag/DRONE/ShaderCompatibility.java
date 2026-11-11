package com.zzynes.sarcophag.DRONE;

import java.lang.reflect.Method;

public final class ShaderCompatibility {

    private static boolean resolved;
    private static Object irisApi;
    private static Method irisShaderPackInUse;
    private static Method optifineShaders;

    private ShaderCompatibility() {
    }

    public static boolean isExternalShaderPackActive() {
        resolve();
        if (irisApi != null && irisShaderPackInUse != null) {
            try {
                if (Boolean.TRUE.equals(irisShaderPackInUse.invoke(irisApi))) {
                    return true;
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                irisApi = null;
                irisShaderPackInUse = null;
            }
        }
        if (optifineShaders != null) {
            try {
                return Boolean.TRUE.equals(optifineShaders.invoke(null));
            } catch (ReflectiveOperationException | LinkageError e) {
                optifineShaders = null;
            }
        }
        return false;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        ClassLoader classLoader = ShaderCompatibility.class.getClassLoader();
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, classLoader);
            Object api = irisApiClass.getMethod("getInstance").invoke(null);
            if (api != null) {
                irisApi = api;
                irisShaderPackInUse = irisApiClass.getMethod("isShaderPackInUse");
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            irisApi = null;
            irisShaderPackInUse = null;
        }
        try {
            Class<?> optifineConfigClass = Class.forName("net.optifine.Config", false, classLoader);
            optifineShaders = optifineConfigClass.getMethod("isShaders");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            optifineShaders = null;
        }
    }
}
