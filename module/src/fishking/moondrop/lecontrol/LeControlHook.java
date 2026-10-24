package io.github.oliverarcher.moondrop.lecontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import de.robv.android.xposed.XposedBridge;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;
import java.util.Map;
import android.app.AndroidAppHelper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Select the existing GAIA GATT implementation for an exact connected LE Audio address. */
public final class LeControlHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.moondroplab.moondrop.moondrop_app";
    private static final String TAG = "MoondropLeControl";
    private static String lastLog = "";

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName) || !TARGET.equals(lp.processName)) return;
        try {
            // MethodCall is a stable Flutter boundary, unlike R8-obfuscated app classes.
            XposedHelpers.findAndHookMethod("io.flutter.plugin.common.MethodCall", lp.classLoader,
                "argument", String.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!"transportType".equals(p.args[0])) return;
                        try {
                            if (!"connectDeviceBT".equals(XposedHelpers.getObjectField(p.thisObject, "method"))) return;
                            Object raw = XposedHelpers.getObjectField(p.thisObject, "arguments");
                            if (!(raw instanceof Map)) return;
                            Object value = ((Map<?, ?>) raw).get("address");
                            if (!(value instanceof String)) return;
                            String address = (String) value;
                            if (!BluetoothAdapter.checkBluetoothAddress(address)) return;
                            Context context = AndroidAppHelper.currentApplication();
                            if (context == null) return;
                            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                            if (audio == null) return;
                            for (AudioDeviceInfo output : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                                if (output.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET
                                        && address.equalsIgnoreCase(output.getAddress())) {
                                    p.setResult("LOW_ENERGY");
                                    logOnce("Connected LE Audio address matched; GAIA connectDeviceBT -> LOW_ENERGY");
                                    return;
                                }
                            }
                            logOnce("No matching LE Audio output; preserving original transport");
                        } catch (Throwable error) {
                            Log.w(TAG, "Transport check failed; preserving original behavior", error);
                        }
                    }
                });
            installWriteQueueFix(lp.classLoader);
            Log.i(TAG, "Installed Flutter GAIA transport hook v0.6; no model-name restriction");
        } catch (Throwable error) {
            Log.e(TAG, "Unsupported Flutter boundary; hook not installed", error);
        }
    }

    private static void installWriteQueueFix(ClassLoader loader) {
        String[][] pairs = {
            {"fa2", "up5"},
            {"com.qualcomm.qti.gaiaclient.core.bluetooth.client.legatt.operations.GattOperation$Write",
             "com.qualcomm.qti.gaiaclient.core.bluetooth.client.legatt.WriteType"}
        };
        for (String[] pair : pairs) {
            Class<?> operation = XposedHelpers.findClassIfExists(pair[0], loader);
            Class<?> type = XposedHelpers.findClassIfExists(pair[1], loader);
            if (operation == null || type == null) continue;
            final Object acknowledged = XposedHelpers.getStaticObjectField(type, "DEFAULT");
            XposedBridge.hookAllConstructors(operation, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length < 3 || !(p.args[0] instanceof BluetoothGattCharacteristic)) return;
                    BluetoothGattCharacteristic c = (BluetoothGattCharacteristic) p.args[0];
                    if (!gaia(c)) return;
                    int flags = c.getProperties();
                    if ((flags & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0
                            && (flags & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                            && "NO_RESPONSE".equals(String.valueOf(p.args[2]))) {
                        p.args[2] = acknowledged;
                        logOnce("GAIA write operation now DEFAULT; internal queue waits for write callback");
                    }
                }
            });
            Log.i(TAG, "Installed GAIA operation queue fix: " + pair[0]);
        }
    }

    private static void installCodecDiagnostics(ClassLoader loader) {
        Class<?> handler = XposedHelpers.findClassIfExists("ua1", loader);
        Class<?> result = XposedHelpers.findClassIfExists("io.flutter.plugin.common.MethodChannel$Result", loader);
        if (handler != null && result != null) {
            XposedHelpers.findAndHookMethod(handler, "t", result, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Log.i(TAG, "Codec LC3 query entered");
                }
            });
        }
        Class<?> request = XposedHelpers.findClassIfExists("rc2", loader);
        if (request != null) XposedHelpers.findAndHookMethod(request, "w", Context.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object info = XposedHelpers.getObjectField(p.thisObject, "c");
                if (info instanceof Enum && ((Enum<?>) info).name().startsWith("CODEC_"))
                    Log.i(TAG, "Codec request executing: " + info);
            }
        });
        Class<?> registry = XposedHelpers.findClassIfExists("ci", loader);
        Class<?> feature = XposedHelpers.findClassIfExists("r14", loader);
        if (registry != null && feature != null) XposedHelpers.findAndHookMethod(registry, "l", feature, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!"CODEC_TYPE".equals(String.valueOf(p.args[0]))) return;
                Object found = p.getResult();
                Log.i(TAG, "Codec registry vendor=" + XposedHelpers.getIntField(p.thisObject, "b")
                    + " plugin=" + (found == null ? "null" : found.getClass().getName()));
            }
        });
    }

    private static boolean gaia(BluetoothGattCharacteristic c) {
        if (c == null) return false;
        String u = c.getUuid().toString();
        return u.endsWith("-d102-11e1-9b23-00025b00a5a5");
    }

    private static String hex(byte[] data) {
        if (data == null) return "null";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 64); i++) out.append(String.format("%02x", data[i] & 255));
        return out.toString();
    }

    private static void installGattDiagnostics(ClassLoader loader) {
        XposedBridge.hookAllMethods(BluetoothGatt.class, "writeCharacteristic", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                BluetoothGattCharacteristic c = (BluetoothGattCharacteristic) p.args[0];
                if (!gaia(c)) return;
                byte[] data = p.args.length > 1 ? (byte[]) p.args[1] : c.getValue();
                int properties = c.getProperties();
                int type = p.args.length > 2 ? (Integer) p.args[2] : c.getWriteType();
                if (type == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        && (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0
                        && (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                    if (p.args.length > 2) p.args[2] = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                    else c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                    Log.i(TAG, "GAIA characteristic supports acknowledged writes only; correcting write type");
                }
                Log.i(TAG, "GAIA TX " + c.getUuid() + " properties=" + properties + " type=" + type + " data=" + hex(data));
            }
        });
        // This R8 callback class is verified against the installed APK, not assumed portable.
        Class<?> callback = XposedHelpers.findClassIfExists("e00", loader);
        if (callback == null) { Log.w(TAG, "No known callback class for RX diagnostics"); return; }
        XposedBridge.hookAllMethods(callback, "onCharacteristicChanged", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                BluetoothGattCharacteristic c = (BluetoothGattCharacteristic) p.args[1];
                if (!gaia(c)) return;
                byte[] data = p.args.length > 2 ? (byte[]) p.args[2] : c.getValue();
                Log.i(TAG, "GAIA RX " + c.getUuid() + " data=" + hex(data));
            }
        });
        XposedBridge.hookAllMethods(callback, "onDescriptorWrite", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                BluetoothGattDescriptor d = (BluetoothGattDescriptor) p.args[1];
                if (gaia(d.getCharacteristic())) Log.i(TAG, "GAIA CCCD " + d.getCharacteristic().getUuid() + " status=" + p.args[2]);
            }
        });
        XposedBridge.hookAllMethods(callback, "onCharacteristicWrite", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                BluetoothGattCharacteristic c = (BluetoothGattCharacteristic) p.args[1];
                if (gaia(c)) Log.i(TAG, "GAIA write callback " + c.getUuid() + " status=" + p.args[2]);
            }
        });
    }

    private static synchronized void logOnce(String message) {
        if (!message.equals(lastLog)) {
            lastLog = message;
            Log.i(TAG, message);
        }
    }
}
