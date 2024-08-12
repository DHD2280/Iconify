package com.drdisagree.iconify.xposed.modules

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_CUSTOM_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_ENABLED
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_EXTRAS
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_INACTIVE
import com.drdisagree.iconify.config.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.views.LockscreenWidgetsView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LockscreenWidgets(context: Context?) : ModPack(context!!) {

    // Parent
    private var mStatusViewContainer: ViewGroup? = null

    // Activity Starter
    private var mActivityStarter: Any? = null

    // Widgets Prefs
    // Lockscreen Widgets
    private var mWidgetsEnabled: Boolean = false
    private var mDeviceWidgetEnabled = false
    private var mDeviceCustomColor = false
    private var mDeviceLinearColor = Color.WHITE
    private var mDeviceCircularColor = Color.WHITE
    private var mDeviceTextColor = Color.WHITE
    private var mWidgetsCustomColor = false
    private var mBigInactiveColor = Color.BLACK
    private var mBigActiveColor = Color.WHITE
    private var mSmallInactiveColor = Color.BLACK
    private var mSmallActiveColor = Color.WHITE
    private var mBigIconActiveColor = Color.WHITE
    private var mBigIconInactiveColor = Color.BLACK
    private var mSmallIconActiveColor = Color.WHITE
    private var mSmallIconInactiveColor = Color.BLACK
    private var mDeviceName = ""
    private var mMainWidgets: String = ""
    private var mExtraWidgets: String = ""

    override fun updatePrefs(vararg key: String) {
        if (Xprefs == null) return

        // Widgets
        mWidgetsEnabled = Xprefs!!.getBoolean(LOCKSCREEN_WIDGETS_ENABLED, false)
        mDeviceWidgetEnabled = Xprefs!!.getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET, false)
        mMainWidgets = Xprefs!!.getString(LOCKSCREEN_WIDGETS, "")!!
        mExtraWidgets = Xprefs!!.getString(LOCKSCREEN_WIDGETS_EXTRAS, "")!!
        mDeviceCustomColor =
            Xprefs!!.getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH, false)
        mDeviceLinearColor =
            Xprefs!!.getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR, Color.WHITE)
        mDeviceCircularColor =
            Xprefs!!.getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR, Color.WHITE)
        mDeviceTextColor = Xprefs!!.getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR, Color.WHITE)
        mDeviceName = Xprefs!!.getString(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE, "")!!
        mWidgetsCustomColor = Xprefs!!.getBoolean(LOCKSCREEN_WIDGETS_CUSTOM_COLOR, false)
        mBigInactiveColor = Xprefs!!.getInt(LOCKSCREEN_WIDGETS_BIG_INACTIVE, Color.BLACK)
        mBigActiveColor = Xprefs!!.getInt(LOCKSCREEN_WIDGETS_BIG_ACTIVE, Color.WHITE)
        mSmallInactiveColor = Xprefs!!.getInt(LOCKSCREEN_WIDGETS_SMALL_INACTIVE, Color.BLACK)
        mSmallActiveColor = Xprefs!!.getInt(LOCKSCREEN_WIDGETS_SMALL_ACTIVE, Color.WHITE)
        mBigIconActiveColor = Xprefs!!.getInt(LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE, Color.BLACK)
        mBigIconInactiveColor = Xprefs!!.getInt(LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE, Color.WHITE)
        mSmallIconActiveColor = Xprefs!!.getInt(LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE, Color.BLACK)
        mSmallIconInactiveColor =
            Xprefs!!.getInt(LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE, Color.WHITE)


        if (key.isNotEmpty() &&
            (key[0] == LOCKSCREEN_WIDGETS_ENABLED ||
                    key[0] == LOCKSCREEN_WIDGETS_DEVICE_WIDGET ||
                    key[0] == LOCKSCREEN_WIDGETS ||
                    key[0] == LOCKSCREEN_WIDGETS_EXTRAS )
        ) {
            updateLockscreenWidgets()
        }

    }

    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        try {
            LaunchableImageView = findClass("", loadPackageParam.classLoader)
        } catch (t: Throwable) {
            log(ControllersProvider.TAG + " Error finding LaunchableImageView: " + t.message)
        }

        try {
            LaunchableLinearLayout = findClass("", loadPackageParam.classLoader)
        } catch (t: Throwable) {
            log(ControllersProvider.TAG + " Error finding LaunchableImageView: " + t.message)
        }

        val keyguardStatusViewClass = findClass(
            "com.android.keyguard.KeyguardStatusView",
            loadPackageParam.classLoader
        )

        try {
            val KeyguardQuickAffordanceInteractor = findClass(
                "com.android.systemui.keyguard.domain.interactor.KeyguardQuickAffordanceInteractor",
                loadPackageParam.classLoader
            )
            XposedBridge.hookAllConstructors(
                KeyguardQuickAffordanceInteractor,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        mActivityStarter = getObjectField(param.thisObject, "activityStarter")
                        setActivityStarter()
                    }
                })
        } catch (ignored: Throwable) {
        }

        hookAllMethods(keyguardStatusViewClass, "onFinishInflate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {

                mStatusViewContainer = getObjectField(
                    param.thisObject,
                    "mStatusViewContainer"
                ) as ViewGroup

                placeWidgets()
            }
        })

        val dozeScrimControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.DozeScrimController",
            loadPackageParam.classLoader
        )

        hookAllMethods(dozeScrimControllerClass, "onDozingChanged", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                updateDozingState(param.args[0] as Boolean)
            }
        })
    }

    private fun placeWidgets() {
        if (mStatusViewContainer == null) return
        try {
            val lsWidgets = LockscreenWidgetsView.getInstance(mContext, mActivityStarter)
            try {
                (lsWidgets.parent as ViewGroup).removeView(lsWidgets)
            } catch (ignored: Throwable) {
            }
            mStatusViewContainer!!.addView(lsWidgets)
            updateLockscreenWidgets()
            updateLsDeviceWidget()
            updateLockscreenWidgetsColors()
        } catch (ignored: Throwable) {
        }

    }

    private fun updateLockscreenWidgets() {
        log(TAG + "Updating Lockscreen Widgets")
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setOptions(mWidgetsEnabled, mDeviceWidgetEnabled, mMainWidgets, mExtraWidgets)
    }

    private fun updateLsDeviceWidget() {
        log(TAG + "Updating Lockscreen Device Widget")
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setDeviceWidgetOptions(
            mDeviceCustomColor,
            mDeviceLinearColor,
            mDeviceCircularColor,
            mDeviceTextColor,
            mDeviceName
        )
    }

    private fun updateLockscreenWidgetsColors() {
        log(TAG + "Updating Lockscreen Widgets Colors")
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setCustomColors(
            mWidgetsCustomColor,
            mBigInactiveColor, mBigActiveColor,
            mSmallInactiveColor, mSmallActiveColor,
            mBigIconInactiveColor, mBigIconActiveColor,
            mSmallIconInactiveColor, mSmallIconActiveColor
        )
    }

    private fun updateDozingState(isDozing: Boolean) {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setDozingState(isDozing)
    }

    private fun setActivityStarter() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setActivityStarter(mActivityStarter)
    }

    companion object {
        private val TAG = "Iconify - ${LockscreenWidgets::class.java.simpleName}: "

        var LaunchableImageView: Class<*>? = null
        var LaunchableLinearLayout: Class<*>? = null

    }

}