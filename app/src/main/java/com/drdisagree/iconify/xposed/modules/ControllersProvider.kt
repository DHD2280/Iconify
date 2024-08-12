package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.drdisagree.iconify.xposed.ModPack
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ControllersProvider(context: Context?) : ModPack(context!!) {

    private val mMobileDataChangedListeners = ArrayList<OnMobileDataChanged>()
    private val mWifiChangedListeners = ArrayList<OnWifiChanged>()
    private val mBluetoothChangedListeners = ArrayList<OnBluetoothChanged>()
    private val mTorchModeChangedListeners = ArrayList<OnTorchModeChanged>()

    override fun updatePrefs(vararg key: String) {}

    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT != 34) return

        instance = this

        // Network Callbacks
        val CallbackHandler = findClass(
            "com.android.systemui.statusbar.connectivity.CallbackHandler",
            loadPackageParam.classLoader
        )


        // Mobile Data
        hookAllMethods(
            CallbackHandler,
            "setMobileDataIndicators",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    onSetMobileDataIndicators(param.args[0])
                }
            })

        hookAllMethods(CallbackHandler, "setIsAirplaneMode", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                //mAirplane = (boolean) param.args[0];
                onSetIsAirplaneMode(param.args[0])
            }
        })

        hookAllMethods(CallbackHandler, "setNoSims", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                onSetNoSims(param.args[0] as Boolean, param.args[1] as Boolean)
            }
        })


        // WiFi
        hookAllMethods(CallbackHandler, "setWifiIndicators", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                onWifiChanged(param.args[0])
            }
        })

    }

    /**
     * Callbacks for Mobile Data
     */
    interface OnMobileDataChanged {
        fun setMobileDataIndicators(mMobileDataIndicators: Any?)
        fun setNoSims(show: Boolean, simDetected: Boolean)
        fun setIsAirplaneMode(mIconState: Any?)
    }

    /**
     * Callback for WiFi
     */
    interface OnWifiChanged {
        fun onWifiChanged(mWifiIndicators: Any?)
    }

    /**
     * Callback for Bluetooth
     */
    interface OnBluetoothChanged {
        fun onBluetoothChanged(enabled: Boolean)
    }

    /**
     * Callback for FlashLight
     */
    interface OnTorchModeChanged {
        fun onTorchModeChanged(enabled: Boolean)
    }

    fun registerMobileDataCallback(callback: OnMobileDataChanged) {
        instance!!.mMobileDataChangedListeners.add(callback)
    }

    /** @noinspection unused
     */
    fun unRegisterMobileDataCallback(callback: OnMobileDataChanged?) {
        instance!!.mMobileDataChangedListeners.remove(callback)
    }

    fun registerWifiCallback(callback: OnWifiChanged) {
        instance!!.mWifiChangedListeners.add(callback)
    }

    /** @noinspection unused
     */
    fun unRegisterWifiCallback(callback: OnWifiChanged?) {
        instance!!.mWifiChangedListeners.remove(callback)
    }

    fun registerBluetoothCallback(callback: OnBluetoothChanged) {
        instance!!.mBluetoothChangedListeners.add(callback)
    }

    /** @noinspection unused
     */
    fun unRegisterBluetoothCallback(callback: OnBluetoothChanged?) {
        instance!!.mBluetoothChangedListeners.remove(callback)
    }

    fun registerTorchModeCallback(callback: OnTorchModeChanged) {
        instance!!.mTorchModeChangedListeners.add(callback)
    }

    /** @noinspection unused
     */
    fun unRegisterTorchModeCallback(callback: OnTorchModeChanged?) {
        instance!!.mTorchModeChangedListeners.remove(callback)
    }

    private fun onSetMobileDataIndicators(mMobileDataIndicators: Any) {
        for (callback in mMobileDataChangedListeners) {
            try {
                callback.setMobileDataIndicators(mMobileDataIndicators)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onSetIsAirplaneMode(mMobileDataIndicators: Any) {
        for (callback in mMobileDataChangedListeners) {
            try {
                callback.setIsAirplaneMode(mMobileDataIndicators)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onSetNoSims(show: Boolean, simDetected: Boolean) {
        for (callback in mMobileDataChangedListeners) {
            try {
                callback.setNoSims(show, simDetected)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onWifiChanged(WifiIndicators: Any) {
        for (callback in mWifiChangedListeners) {
            try {
                callback.onWifiChanged(WifiIndicators)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onBluetoothChanged(enabled: Boolean) {
        for (callback in mBluetoothChangedListeners) {
            try {
                callback.onBluetoothChanged(enabled)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onTorchModeChanged(enabled: Boolean) {
        for (callback in mTorchModeChangedListeners) {
            try {
                callback.onTorchModeChanged(enabled)
            } catch (ignored: Throwable) {
            }
        }
    }


    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ControllersProvider? = null

        val TAG: String = "ControllersProvider"

        val mBluetoothController: Any? = null
        val mDataController: Any? = null
        val mNetworkController: Any? = null
        val mSignalCallback: Any? = null

        val mBluetoothTile: Any? = null
        val mWifiTile: Any? = null
        val mCellularTile: Any? = null
        val mDeviceControlsTile: Any? = null
        val mCalculatorTile: Any? = null
        val mWalletTile: Any? = null

        val mQsDialogLaunchAnimator: Any? = null
        val mQsMediaDialogController: Any? = null

        fun getInstance(): ControllersProvider {
            return instance!!
            }
        }

}