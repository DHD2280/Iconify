package com.drdisagree.iconify.xposed.views

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaController.PlaybackInfo
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.xposed.HookRes.Companion.modRes
import com.drdisagree.iconify.xposed.modules.ControllersProvider
import com.drdisagree.iconify.xposed.modules.LockscreenWidgets.Companion.LaunchableImageView
import com.drdisagree.iconify.xposed.modules.LockscreenWidgets.Companion.LaunchableLinearLayout
import com.drdisagree.iconify.xposed.utils.ActivityLauncherUtils
import com.drdisagree.iconify.xposed.utils.ExtendedFAB
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.min

@SuppressLint("ViewConstructor")
class LockscreenWidgetsView(context: Context, activityStarter: Any?) :
    LinearLayout(context) {

    private val mContext: Context

    // Two Linear Layouts, one for main widgets and one for secondary widgets
    private val mDeviceWidgetContainer: LinearLayout?
    private val mMainWidgetsContainer: LinearLayout?
    private val mSecondaryWidgetsContainer: LinearLayout?
    private var mDeviceWidgetView: DeviceWidgetView? = null

    private var mediaButton: ImageView? = null
    private var torchButton: ImageView? = null
    private var weatherButton: ImageView? = null
    private var mediaButtonFab: ExtendedFAB? = null
    private var torchButtonFab: ExtendedFAB? = null
    private var weatherButtonFab: ExtendedFAB? = null
    private var wifiButtonFab: ExtendedFAB? = null
    private var dataButtonFab: ExtendedFAB? = null
    private var ringerButtonFab: ExtendedFAB? = null
    private var btButtonFab: ExtendedFAB? = null
    private var wifiButton: ImageView? = null
    private var dataButton: ImageView? = null
    private var ringerButton: ImageView? = null
    private var btButton: ImageView? = null
    private val mDarkColor: Int
    private val mDarkColorActive: Int
    private val mLightColor: Int
    private val mLightColorActive: Int

    // Custom Widgets Colors
    private var mCustomColors = false
    private var mBigInactiveColor = 0
    private var mBigActiveColor = 0
    private var mSmallInactiveColor = 0
    private var mSmallActiveColor = 0
    private var mBigIconInactiveColor = 0
    private var mBigIconActiveColor = 0
    private var mSmallIconInactiveColor = 0
    private var mSmallIconActiveColor = 0

    private var mMainLockscreenWidgetsList: String? = null
    private var mSecondaryLockscreenWidgetsList: String? = null
    private var mMainWidgetViews: Array<ExtendedFAB>? = null
    private var mSecondaryWidgetViews: Array<ImageView>? = null
    private var mMainWidgetsList: List<String>? = ArrayList()
    private var mSecondaryWidgetsList: List<String>? = ArrayList()

    private val mAudioManager: AudioManager?
    private val mWifiManager: WifiManager?
    private var mController: MediaController? = null
    private var mMediaMetadata: MediaMetadata? = null
    private var mLastTrackTitle: String? = null

    private var lockscreenWidgetsEnabled = false
    private var deviceWidgetsEnabled = false

    private var isBluetoothOn = false

    private var mIsInflated = false
    private var mIsLongPress = false

    private val mCameraManager: CameraManager
    private var mCameraId: String? = null
    private var isFlashOn = false

    private var mWifiIndicators: Any? = null

    private val mAudioMode = 0
    private val mMediaUpdater: Runnable
    private val mHandler: Handler

    // Dozing State
    private var mDozing: Boolean = false

    private var mActivityLauncherUtils: ActivityLauncherUtils

    private val mMediaCallback: MediaController.Callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaController()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            mMediaMetadata = metadata
            updateMediaController()
        }
    }

    private val mMobileDataCallback: ControllersProvider.OnMobileDataChanged =
        object : ControllersProvider.OnMobileDataChanged {
            override fun setMobileDataIndicators(mMobileDataIndicators: Any?) {
                val qsIcon = XposedHelpers.getObjectField(mMobileDataIndicators, "qsIcon")
                if (qsIcon == null) {
                    updateMobileDataState(false)
                    return
                }
                updateMobileDataState(isMobileDataEnabled)
            }

            override fun setNoSims(show: Boolean, simDetected: Boolean) {
                updateMobileDataState(simDetected && isMobileDataEnabled)
            }

            override fun setIsAirplaneMode(mIconState: Any?) {
                updateMobileDataState(
                    !XposedHelpers.getBooleanField(
                        mIconState,
                        "visible"
                    ) && isMobileDataEnabled
                )
            }
        }

    private val mWifiCallback: ControllersProvider.OnWifiChanged = object : ControllersProvider.OnWifiChanged {
        override fun onWifiChanged(WifiIndicators: Any?) {
            XposedBridge.log("LockscreenWidgets onWifiChanged")
            mWifiIndicators = WifiIndicators
            val qsIcon = XposedHelpers.getObjectField(WifiIndicators, "qsIcon")
            XposedBridge.log("LockscreenWidgets onWifiChanged qsIcon " + (qsIcon != null))
            if (qsIcon == null) {
                updateWiFiButtonState(false)
                return
            }
            updateWiFiButtonState(isWifiEnabled)
        }
    }

    private val mBluetoothCallback: ControllersProvider.OnBluetoothChanged = object : ControllersProvider.OnBluetoothChanged {
        override fun onBluetoothChanged(enabled: Boolean) {
            XposedBridge.log("LockscreenWidgets onBluetoothChanged $enabled")
            isBluetoothOn = enabled
            updateBtState()
        }
    }

    private val mTorchCallback: ControllersProvider.OnTorchModeChanged = object : ControllersProvider.OnTorchModeChanged {

        override fun onTorchModeChanged(enabled: Boolean) {
            XposedBridge.log("LockscreenWidgets onTorchChanged $enabled")
            isFlashOn = enabled
            updateTorchButtonState()
        }
    }

    private fun createDeviceWidgetContainer(context: Context): LinearLayout {
        val deviceWidget = LinearLayout(context)
        deviceWidget.orientation = HORIZONTAL
        deviceWidget.gravity = Gravity.CENTER
        deviceWidget.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        mDeviceWidgetView = DeviceWidgetView(context)

        deviceWidget.addView(mDeviceWidgetView)

        XposedBridge.log("LockscreenWidgets createDeviceWidgetContainer done")

        return deviceWidget
    }

    private fun createMainWidgetsContainer(context: Context): LinearLayout {
        var mainWidgetsContainer: LinearLayout?
        XposedBridge.log("LockscreenWidgets createMainWidgetsContainer LaunchableLinearLayout " + (LaunchableLinearLayout != null))
        try {
            mainWidgetsContainer =
                LaunchableLinearLayout!!.getConstructor(Context::class.java).newInstance(context) as LinearLayout?
        } catch (e: Exception) {
            XposedBridge.log("LockscreenWidgets createMainWidgetsContainer LaunchableLinearLayout not found: " + e.message)
            mainWidgetsContainer = LinearLayout(context)
        }

        if (mainWidgetsContainer == null) {
            mainWidgetsContainer = LinearLayout(context) // Ensure the creation on our linear layout
        }

        XposedBridge.log("LockscreenWidgets createMainWidgetsContainer mainWidgetsContainer " + (mainWidgetsContainer != null))

        mainWidgetsContainer.orientation = HORIZONTAL
        mainWidgetsContainer.gravity = Gravity.CENTER
        mainWidgetsContainer.setLayoutParams(
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Add FABs to the main widgets container
        mMainWidgetViews = arrayOf(
            createFAB(context),
            createFAB(context)
        )

        for (mMainWidgetView: ExtendedFAB in mMainWidgetViews!!) {
            mainWidgetsContainer.addView(mMainWidgetView)
        }

        XposedBridge.log("LockscreenWidgets createMainWidgetsContainer done " + (mainWidgetsContainer != null))

        return mainWidgetsContainer
    }

    private fun createFAB(context: Context): ExtendedFAB {
        val fab: ExtendedFAB = ExtendedFAB(context)
        fab.setId(generateViewId())
        val params: LayoutParams = LayoutParams(
            modRes!!.getDimensionPixelSize(R.dimen.kg_widget_main_width),
            modRes!!.getDimensionPixelSize(R.dimen.kg_widget_main_height)
        )
        params.setMargins(
            modRes!!.getDimensionPixelSize(R.dimen.kg_widgets_main_margin_start),
            0,
            modRes!!.getDimensionPixelSize(R.dimen.kg_widgets_main_margin_end),
            0
        )
        fab.setLayoutParams(params)
        fab.setPadding(
            modRes!!.getDimensionPixelSize(R.dimen.kg_main_widgets_icon_padding),
            modRes!!.getDimensionPixelSize(R.dimen.kg_main_widgets_icon_padding),
            modRes!!.getDimensionPixelSize(R.dimen.kg_main_widgets_icon_padding),
            modRes!!.getDimensionPixelSize(R.dimen.kg_main_widgets_icon_padding)
        )
        fab.setGravity(Gravity.CENTER)
        return fab
    }

    private fun createSecondaryWidgetsContainer(context: Context): LinearLayout {
        var secondaryWidgetsContainer: LinearLayout?
        XposedBridge.log("LockscreenWidgets createSecondaryWidgetsContainer LaunchableLinearLayout " + (LaunchableLinearLayout != null))
        try {
            secondaryWidgetsContainer =
                LaunchableLinearLayout?.getConstructor(Context::class.java)?.newInstance(context) as LinearLayout?
        } catch (e: Exception) {
            XposedBridge.log("LockscreenWidgets createMainWidgetsContainer LaunchableLinearLayout not found: " + e.message)
            secondaryWidgetsContainer = LinearLayout(context)
        }

        if (secondaryWidgetsContainer == null) {
            secondaryWidgetsContainer =
                LinearLayout(context) // Ensure the creation on our linear layout
        }

        secondaryWidgetsContainer.orientation = HORIZONTAL
        secondaryWidgetsContainer.gravity = Gravity.CENTER_HORIZONTAL
        secondaryWidgetsContainer.setLayoutParams(
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        (secondaryWidgetsContainer.layoutParams as MarginLayoutParams).topMargin =
            modRes!!.getDimensionPixelSize(R.dimen.kg_widget_margin_vertical)
        (secondaryWidgetsContainer.layoutParams as MarginLayoutParams).bottomMargin =
            modRes!!.getDimensionPixelSize(R.dimen.kg_widget_margin_bottom)

        // Add ImageViews to the secondary widgets container
        mSecondaryWidgetViews = arrayOf(
            createImageView(context),
            createImageView(context),
            createImageView(context),
            createImageView(context)
        )

        for (mSecondaryWidgetView: ImageView? in mSecondaryWidgetViews!!) {
            secondaryWidgetsContainer.addView(mSecondaryWidgetView)
        }

        XposedBridge.log("LockscreenWidgets createSecondaryWidgetsContainer done, secondaryWidgetsContainer " + (secondaryWidgetsContainer != null))

        return secondaryWidgetsContainer
    }

    private fun createImageView(context: Context): ImageView {
        val imageView: ImageView = try {
            LaunchableImageView?.getConstructor(Context::class.java)?.newInstance(context) as ImageView
        } catch (e: Exception) {
            // LaunchableImageView not found or other error, ensure the creation of our ImageView
            ImageView(context)
        }

        imageView.id = generateViewId()
        val params: LayoutParams = LayoutParams(
            modRes!!.getDimensionPixelSize(R.dimen.kg_widget_circle_size),
            modRes!!.getDimensionPixelSize(R.dimen.kg_widget_circle_size)
        )
        params.setMargins(
            modRes!!.getDimensionPixelSize(R.dimen.kg_widgets_margin_horizontal),
            0,
            modRes!!.getDimensionPixelSize(R.dimen.kg_widgets_margin_horizontal),
            0
        )
        imageView.layoutParams = params
        imageView.setPadding(
            modRes!!.getDimensionPixelSize(R.dimen.kg_widgets_icon_padding),
            modRes!!.getDimensionPixelSize(R.dimen.kg_widgets_icon_padding),
            modRes!!.getDimensionPixelSize(R.dimen.kg_widgets_icon_padding),
            modRes!!.getDimensionPixelSize(R.dimen.kg_widgets_icon_padding)
        )
        imageView.isFocusable = true
        imageView.isClickable = true

        return imageView
    }

    private val isMediaControllerAvailable: Boolean
        get() {
            val mediaController =
                activeLocalMediaController
            return mediaController != null && !TextUtils.isEmpty(mediaController.packageName)
        }

    private val activeLocalMediaController: MediaController?
        get() {
            val mediaSessionManager =
                mContext.getSystemService(MediaSessionManager::class.java)
            var localController: MediaController? = null
            val remoteMediaSessionLists: MutableList<String> = ArrayList()
            for (controller: MediaController in mediaSessionManager.getActiveSessions(null)) {
                val pi = controller.playbackInfo ?: continue
                val playbackState = controller.playbackState ?: continue
                if (playbackState.state != PlaybackState.STATE_PLAYING) {
                    continue
                }
                if (pi.playbackType == PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                    if (localController != null
                        && TextUtils.equals(
                            localController.packageName, controller.packageName
                        )
                    ) {
                        localController = null
                    }
                    if (!remoteMediaSessionLists.contains(controller.packageName)) {
                        remoteMediaSessionLists.add(controller.packageName)
                    }
                    continue
                }
                if (pi.playbackType == PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
                    if (localController == null
                        && !remoteMediaSessionLists.contains(controller.packageName)
                    ) {
                        localController = controller
                    }
                }
            }
            return localController
        }

    private fun isWidgetEnabled(widget: String): Boolean {
        if (mMainWidgetViews == null || mSecondaryWidgetViews == null) {
            return false
        }
        return mMainWidgetsList!!.contains(widget) || mSecondaryWidgetsList!!.contains(widget)
    }

    private fun updateMediaController() {
        if (!isWidgetEnabled("media")) return
        val localController =
            activeLocalMediaController
        if (localController != null && !sameSessions(mController, localController)) {
            if (mController != null) {
                mController!!.unregisterCallback(mMediaCallback)
                mController = null
            }
            mController = localController
            mController!!.registerCallback(mMediaCallback)
        }
        mMediaMetadata = if (isMediaControllerAvailable) mController!!.metadata else null
        updateMediaState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            updateMediaController()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!lockscreenWidgetsEnabled) return
        if (visibility == VISIBLE) {
            onVisible()
        }
    }

    private fun onVisible() {
        XposedBridge.log("LockscreenWidgets onVisible")
        updateTorchButtonState()
        updateRingerButtonState()
        updateWiFiButtonState(isWifiEnabled)
        updateMobileDataState(isMobileDataEnabled)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        XposedBridge.log("LockscreenWidgets onAttachedToWindow")
        onVisible()
    }

    public override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        XposedBridge.log("LockscreenWidgets onConfigurationChanged")
        updateWidgetViews()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        XposedBridge.log("LockscreenWidgets onFinishInflate")
        mIsInflated = true
        updateWidgetViews()
    }

    private fun updateContainerVisibility() {
        val isMainWidgetsEmpty = (mMainLockscreenWidgetsList == null
                || TextUtils.isEmpty(mMainLockscreenWidgetsList))
        val isSecondaryWidgetsEmpty = (mSecondaryLockscreenWidgetsList == null
                || TextUtils.isEmpty(mSecondaryLockscreenWidgetsList))
        val isEmpty = isMainWidgetsEmpty && isSecondaryWidgetsEmpty

        if (mDeviceWidgetContainer != null) {
            mDeviceWidgetContainer.visibility = if (deviceWidgetsEnabled) VISIBLE else GONE
        }
        if (mMainWidgetsContainer != null) {
            mMainWidgetsContainer.visibility = if (isMainWidgetsEmpty) GONE else VISIBLE
        }
        if (mSecondaryWidgetsContainer != null) {
            mSecondaryWidgetsContainer.visibility = if (isSecondaryWidgetsEmpty) GONE else VISIBLE
        }
        val shouldHideContainer = isEmpty || mDozing || !lockscreenWidgetsEnabled
        visibility = if (shouldHideContainer) GONE else VISIBLE
    }

    fun updateWidgetViews() {
        XposedBridge.log("LockscreenWidgets updateWidgetViews lockscreenWidgetsEnabled $lockscreenWidgetsEnabled")

        if (mMainWidgetViews != null && mMainWidgetsList != null) {
            for (i in mMainWidgetViews!!.indices) {
                mMainWidgetViews!![i].visibility = if (i < mMainWidgetsList!!.size) VISIBLE else GONE
            }
            for (i in 0 until min(
                mMainWidgetsList!!.size.toDouble(),
                mMainWidgetViews!!.size.toDouble()
            )
                .toInt()) {
                val widgetType: String = mMainWidgetsList!![i]
                if (i < mMainWidgetViews!!.size) {
                    XposedBridge.log("LockscreenWidgets updateWidgetViews mMainWidgetsList $widgetType")
                    setUpWidgetWiews(null, mMainWidgetViews!![i], widgetType)
                    updateMainWidgetResources(mMainWidgetViews!![i], false)
                }
            }
        }
        if (mSecondaryWidgetViews != null && mSecondaryWidgetsList != null) {
            for (i in mSecondaryWidgetViews!!.indices) {
                mSecondaryWidgetViews!![i].visibility =
                    if (i < mSecondaryWidgetsList!!.size) VISIBLE else GONE
            }
            for (i in 0 until min(
                mSecondaryWidgetsList!!.size.toDouble(),
                mSecondaryWidgetViews!!.size.toDouble()
            )
                .toInt()) {
                val widgetType: String = mSecondaryWidgetsList!![i]
                if (i < mSecondaryWidgetViews!!.size) {
                    XposedBridge.log("LockscreenWidgets updateWidgetViews mSecondaryWidgetsList $widgetType")
                    setUpWidgetWiews(mSecondaryWidgetViews!![i], null, widgetType)
                    updateWidgetsResources(mSecondaryWidgetViews!![i])
                }
            }
        }
        updateContainerVisibility()
        updateMediaController()
    }

    private fun updateMainWidgetResources(efab: ExtendedFAB?, active: Boolean) {
        if (efab == null) return
        efab.setElevation(0F)
        setButtonActiveState(null, efab, false)
        val params: ViewGroup.LayoutParams = efab.layoutParams
        if (params is LayoutParams) {
            if (efab.visibility == VISIBLE && mMainWidgetsList!!.size == 1) {
                params.width = modRes!!.getDimensionPixelSize(R.dimen.kg_widget_main_width)
                params.height = modRes!!.getDimensionPixelSize(R.dimen.kg_widget_main_height)
            } else {
                params.width = 0
                params.weight = 1f
            }
            efab.setLayoutParams(params)
        }
    }

    private fun updateWidgetsResources(iv: ImageView?) {
        if (iv == null) return
        val d = ResourcesCompat.getDrawable(
            modRes!!,
            R.drawable.lockscreen_widget_background_circle,
            mContext.theme
        )
        iv.background = d
        setButtonActiveState(iv, null, false)
    }

    private val isNightMode: Boolean
        get() {
            val config = mContext.resources.configuration
            return ((config.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES)
        }

    private fun setUpWidgetWiews(iv: ImageView?, efab: ExtendedFAB?, type: String) {
        when (type) {
            "none" -> {
                if (iv != null) {
                    iv.visibility = GONE
                }
                efab?.visibility = GONE
            }

            "wifi" -> {
                if (iv != null) {
                    wifiButton = iv
                    wifiButton!!.setOnLongClickListener { v: View ->
                        showWifiDialog(v)
                        true
                    }
                }
                if (efab != null) {
                    wifiButtonFab = efab
                    wifiButtonFab!!.setOnLongClickListener { v ->
                        showWifiDialog(v)
                        true
                    }
                }
                setUpWidgetResources(iv, efab,
                    { v: View? -> toggleWiFi() }, getDrawable(WIFI_INACTIVE, SYSTEMUI_PACKAGE), getString("wifi_Connected", SYSTEMUI_PACKAGE)
                )
            }

            "data" -> {
                if (iv != null) {
                    dataButton = iv
                    dataButton!!.setOnLongClickListener { v: View ->
                        showInternetDialog(v)
                        true
                    }
                }
                if (efab != null) {
                    dataButtonFab = efab
                    dataButtonFab!!.setOnLongClickListener { v ->
                        showInternetDialog(v)
                        true
                    }
                }
                setUpWidgetResources(
                    iv, efab,
                    { toggleMobileData() }, getDrawable(DATA_INACTIVE, SYSTEMUI_PACKAGE), getString(DATA_LABEL, SYSTEMUI_PACKAGE)
                )
            }

            "ringer" -> {
                if (iv != null) {
                    ringerButton = iv
                    ringerButton!!.setOnLongClickListener {
                        mActivityLauncherUtils.launchAudioSettings()
                        true
                    }
                }
                if (efab != null) {
                    ringerButtonFab = efab
                    ringerButtonFab!!.setOnLongClickListener {
                        mActivityLauncherUtils.launchAudioSettings()
                        true
                    }
                }
                setUpWidgetResources(
                    iv, efab,
                    { toggleRingerMode() }, getDrawable(RINGER_NORMAL, SYSTEMUI_PACKAGE), getString(RINGER_LABEL_INACTIVE, SYSTEMUI_PACKAGE)
                )
            }

            "bt" -> {
                if (iv != null) {
                    btButton = iv
                    btButton!!.setOnLongClickListener { v: View ->
                        showBluetoothDialog(v)
                        true
                    }
                }
                if (efab != null) {
                    btButtonFab = efab
                    btButtonFab!!.setOnLongClickListener { v ->
                        showBluetoothDialog(v)
                        true
                    }
                }
                setUpWidgetResources(
                    iv, efab,
                    { toggleBluetoothState() }, getDrawable(BT_INACTIVE,
                        SYSTEMUI_PACKAGE), getString(BT_LABEL, SYSTEMUI_PACKAGE)
                )
            }

            "torch" -> {
                if (iv != null) {
                    torchButton = iv
                }
                if (efab != null) {
                    torchButtonFab = efab
                }
                setUpWidgetResources(
                    iv, efab,
                    { toggleFlashlight() }, getDrawable(TORCH_RES_INACTIVE, SYSTEMUI_PACKAGE), getString(TORCH_LABEL, SYSTEMUI_PACKAGE)
                )
            }

            "timer" -> setUpWidgetResources(iv, efab, { v ->
                mActivityLauncherUtils.launchTimer()
                vibrate(1)
            }, getDrawable("ic_alarm", SYSTEMUI_PACKAGE), modRes!!.getString(R.string.clock_timer))

            "camera" -> setUpWidgetResources(iv, efab, {
                mActivityLauncherUtils.launchCamera()
                vibrate(1)
            }, getDrawable(CAMERA_ICON, SYSTEMUI_PACKAGE), getString(CAMERA_LABEL, SYSTEMUI_PACKAGE))

            "calculator" -> setUpWidgetResources(
                iv,
                efab,
                { v: View? -> openCalculator() },
                getDrawable(CALCULATOR_ICON, SYSTEMUI_PACKAGE),
                getString(
                    CALCULATOR_LABEL, SYSTEMUI_PACKAGE
                )
            )

            "homecontrols" -> setUpWidgetResources(
                iv, efab,
                { view: View ->
                    this.launchHomeControls(
                        view
                    )
                }, HOME_CONTROLS, HOME_CONTROLS_LABEL
            )

            "wallet" -> setUpWidgetResources(iv, efab,
                { view: View ->
                    this.launchWallet(
                        view
                    )
                }, getDrawable(WALLET_ICON, SYSTEMUI_PACKAGE), getString(WALLET_LABEL, SYSTEMUI_PACKAGE)
            )

            "media" -> {
                if (iv != null) {
                    mediaButton = iv
                    mediaButton!!.setOnLongClickListener { v: View ->
                        //showMediaDialog(v)
                        true
                    }
                }
                if (efab != null) {
                    mediaButtonFab = efab
                }
                setUpWidgetResources(
                    iv, efab,
                    { v: View? -> toggleMediaPlaybackState() },
                    ResourcesCompat.getDrawable(modRes!!, R.drawable.ic_play, mContext.theme),
                    getString(MEDIA_PLAY_LABEL, SYSTEMUI_PACKAGE)
                )
            }

            "weather" -> {
                if (iv != null) {
                    weatherButton = iv
                }
                if (efab != null) {
                    weatherButtonFab = efab
                }
                //setUpWidgetResources(iv, efab, v -> mActivityLauncherUtils.launchWeatherApp(), "ic_alarm", R.string.weather_data_unavailable);
//                enableWeatherUpdates()
            }

            else -> {}
        }
    }


    private fun setUpWidgetResources(
        iv: ImageView?, efab: ExtendedFAB?,
        cl: OnClickListener, drawableRes: String, stringRes: String
    ) {
        val d = getDrawable(drawableRes, SYSTEMUI_PACKAGE)
        if (efab != null) {
            efab.setOnClickListener(cl)
            efab.setIcon(d)
            val text = mContext.resources.getString(
                mContext.resources.getIdentifier(
                    stringRes,
                    "string",
                    SYSTEMUI_PACKAGE
                )
            )
            efab.setText(text)
            if (mediaButtonFab === efab) {
                attachSwipeGesture(efab)
            }
        }
        if (iv != null) {
            iv.setOnClickListener(cl)
            iv.setImageDrawable(d)
        }
    }

    private fun setUpWidgetResources(
        iv: ImageView?, efab: ExtendedFAB?,
        cl: OnClickListener, icon: Drawable?, text: String
    ) {
        if (efab != null) {
            efab.setOnClickListener(cl)
            efab.setIcon(icon)
            efab.setText(text)
            if (mediaButtonFab === efab) {
                attachSwipeGesture(efab)
            }
        }
        if (iv != null) {
            iv.setOnClickListener(cl)
            iv.setImageDrawable(icon)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSwipeGesture(efab: ExtendedFAB) {
        val gestureDetector = GestureDetector(mContext, object : SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = e2.x - e1!!.x
                if (abs(diffX.toDouble()) > SWIPE_THRESHOLD && abs(velocityX.toDouble()) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    } else {
                        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT)
                    }
                    vibrate(1)
                    updateMediaController()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                super.onLongPress(e)
                mIsLongPress = true
                //showMediaDialog(efab)
                mHandler.postDelayed({ mIsLongPress = false }, 2500)
            }
        })
        efab.setOnTouchListener { v, event ->
            val isClick: Boolean = gestureDetector.onTouchEvent(event)
            if ((event.getAction() === MotionEvent.ACTION_UP) && !isClick && !mIsLongPress) {
                v.performClick()
            }
            true
        }
    }

    private fun setButtonActiveState(iv: ImageView?, efab: ExtendedFAB?, active: Boolean) {
        val bgTint: Int
        val tintColor: Int

        if (!mCustomColors) {
            if (active) {
                bgTint = if (isNightMode) mDarkColorActive else mLightColorActive
                tintColor = if (isNightMode) mDarkColor else mLightColor
            } else {
                bgTint = if (isNightMode) mDarkColor else mLightColor
                tintColor = if (isNightMode) mLightColor else mDarkColor
            }
            if (iv != null) {
                iv.backgroundTintList = ColorStateList.valueOf(bgTint)
                if (iv !== weatherButton) {
                    iv.imageTintList = ColorStateList.valueOf(tintColor)
                } else {
                    iv.imageTintList = null
                }
            }
            if (efab != null) {
                efab.setBackgroundTintList(ColorStateList.valueOf(bgTint))
                if (efab !== weatherButtonFab) {
                    efab.setIconTint(ColorStateList.valueOf(tintColor))
                } else {
                    efab.setIconTint(null)
                }
                efab.setTextColor(tintColor)
            }
        } else {
            if (iv != null) {
                iv.backgroundTintList =
                    ColorStateList.valueOf(if (active) mSmallActiveColor else mSmallInactiveColor)
                if (iv !== weatherButton) {
                    iv.imageTintList =
                        ColorStateList.valueOf(if (active) mSmallIconActiveColor else mSmallIconInactiveColor)
                } else {
                    iv.imageTintList = null
                }
            }
            if (efab != null) {
                efab.setBackgroundTintList(ColorStateList.valueOf(if (active) mBigActiveColor else mBigInactiveColor))
                if (efab !== weatherButtonFab) {
                    efab.setIconTint(ColorStateList.valueOf(if (active) mBigIconActiveColor else mBigIconInactiveColor))
                } else {
                    efab.setIconTint(null)
                }
                efab.setTextColor(if (active) mBigIconActiveColor else mBigIconInactiveColor)
            }
        }
    }

    private fun updateMediaState() {
        updateMediaPlaybackState()
        mHandler.postDelayed({ this.updateMediaPlaybackState() }, 250)
    }

    private fun toggleMediaPlaybackState() {
        if (isMediaPlaying) {
            mHandler.removeCallbacks(mMediaUpdater)
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PAUSE)
            updateMediaController()
        } else {
            mMediaUpdater.run()
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

//    private fun showMediaDialog(view: View) {
//        if (Build.VERSION.SDK_INT == 33) return  // OOS 13
//
//        updateMediaController()
//        val mediaQsHelper: Array<Any?> = getQsMediaDialog()
//        val finalView: View
//        if (view is ExtendedFAB) {
//            finalView = view.parent as View
//        } else {
//            finalView = view
//        }
//        if (mediaQsHelper[0] == null || mediaQsHelper[1] == null) return
//        callMethod(
//            mediaQsHelper[1], "showPrompt", mContext, finalView,
//            mediaQsHelper[0]
//        )
//        vibrate(0)
//    }

    private fun dispatchMediaKeyWithWakeLockToMediaSession(keycode: Int) {
        val keyIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null)
        val keyEvent = KeyEvent(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            KeyEvent.ACTION_DOWN,
            keycode,
            0
        )
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
        var mediaEvent: KeyEvent? = KeyEvent(KeyEvent.ACTION_DOWN, keycode)
        mAudioManager!!.dispatchMediaKeyEvent(mediaEvent)

        mediaEvent = KeyEvent.changeAction(mediaEvent, KeyEvent.ACTION_UP)
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
        mAudioManager.dispatchMediaKeyEvent(mediaEvent)
    }

    private fun updateMediaPlaybackState() {
        val isPlaying = isMediaPlaying
        val icon = ResourcesCompat.getDrawable(
            modRes!!,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            mContext.theme
        )
        if (mediaButton != null) {
            mediaButton!!.setImageDrawable(icon)
            setButtonActiveState(mediaButton, null, isPlaying)
        }
        if (mediaButtonFab != null) {
            val trackTitle =
                if (mMediaMetadata != null) mMediaMetadata!!.getString(MediaMetadata.METADATA_KEY_TITLE) else ""
            if (!TextUtils.isEmpty(trackTitle) && mLastTrackTitle !== trackTitle) {
                mLastTrackTitle = trackTitle
            }
            val canShowTrackTitle = isPlaying || !TextUtils.isEmpty(mLastTrackTitle)
            mediaButtonFab!!.setIcon(icon)
            mediaButtonFab!!.text = if (canShowTrackTitle) mLastTrackTitle else "Play"
            setButtonActiveState(null, mediaButtonFab, isPlaying)
        }
    }

    private val isMediaPlaying: Boolean
        get() = (isMediaControllerAvailable
                && PlaybackState.STATE_PLAYING == getMediaControllerPlaybackState(mController))

    private fun toggleFlashlight() {
        if (torchButton == null && torchButtonFab == null) return
        try {
            mCameraManager.setTorchMode(mCameraId!!, !isFlashOn)
            isFlashOn = !isFlashOn
            updateTorchButtonState()
            vibrate(1)
        } catch (e: Exception) {
            XposedBridge.log("LockscreenWidgets toggleFlashlight error: " + e.message)
        }
    }

    private fun launchHomeControls(view: View) {
        XposedBridge.log("LockscreenWidgets launchHomeControls")
        val controlsTile: Any = ControllersProvider.mDeviceControlsTile ?: return
        val finalView: View
        if (view is ExtendedFAB) {
            finalView = view.parent as View
        } else {
            finalView = view
        }
        post {
            callMethod(
                controlsTile,
                "handleClick",
                finalView
            )
        }
        vibrate(1)
    }

    private fun launchWallet(view: View) {
        val WalletTile: Any? = ControllersProvider.mWalletTile
        if (WalletTile != null) {
            val finalView: View
            if (view is ExtendedFAB) {
                finalView = view.parent as View
            } else {
                finalView = view
            }
            post({
                callMethod(
                    WalletTile,
                    "handleClick",
                    finalView
                )
            })
        } else {
            mActivityLauncherUtils.launchWallet()
        }
        vibrate(1)
    }

    private fun openCalculator() {
        mActivityLauncherUtils.launchCalculator()
        vibrate(1)
    }

    private fun toggleWiFi() {
        XposedBridge.log("LockscreenWidgets toggleWiFi")
        val networkController: Any? = ControllersProvider.mNetworkController
//        if (networkController == null) {
//            XposedBridge.log("LockscreenWidgets toggleWiFi networkController is null")
//            return
//        }
        val enabled: Boolean = mWifiManager!!.isWifiEnabled
        mWifiManager.isWifiEnabled = !enabled
//        callMethod(networkController, "setWifiEnabled", !enabled)
        updateWiFiButtonState(!enabled)
        mHandler.postDelayed({ updateWiFiButtonState(isWifiEnabled) }, 350L)
        vibrate(1)
    }

    private val isMobileDataEnabled: Boolean
        get() {
            val dataController: Any? = ControllersProvider.mDataController
            XposedBridge.log("LockscreenWidgets isMobileDataEnabled (dataController == null) " + (dataController == null))
            if (dataController != null) {
                return callMethod(dataController, "isMobileDataEnabled") as Boolean
            } else {
                try {
                    val cmClass =
                        Class.forName(ConnectivityManager::class.java.name)
                    val method = cmClass.getDeclaredMethod("getMobileDataEnabled")
                    method.isAccessible = true // Make the method callable
                    // get the setting for "mobile data"
                    return method.invoke(ConnectivityManager::class.java) as Boolean
                } catch (e: Exception) {
                    return false
                }
            }
        }

    private val isWifiEnabled: Boolean
        get() {
            val enabled: Boolean = mWifiManager!!.isWifiEnabled()
            XposedBridge.log("LockscreenWidgets isWifiEnabled $enabled")
            return enabled
        }

    private fun toggleMobileData() {
        if (ControllersProvider.mDataController == null) return
        callMethod(ControllersProvider.mDataController, "setMobileDataEnabled", !isMobileDataEnabled)
        updateMobileDataState(!isMobileDataEnabled)
        mHandler.postDelayed({ updateMobileDataState(isMobileDataEnabled) }, 250L)
        vibrate(1)
    }


    private fun showWifiDialog(view: View) {
        if (Build.VERSION.SDK_INT == 33) return  // OOS 13

        val finalView: View
        if (view is ExtendedFAB) {
            finalView = view.parent as View
        } else {
            finalView = view
        }
        post {
            callMethod(
                ControllersProvider.mWifiTile,
                "handleSecondaryClick",
                finalView
            )
        }
        vibrate(0)
    }

    private fun showInternetDialog(view: View) {
        if (Build.VERSION.SDK_INT == 33) return  // OOS 13

        if (ControllersProvider.mCellularTile == null) return
        val finalView: View = if (view is ExtendedFAB) {
            view.parent as View
        } else {
            view
        }
        post {
            callMethod(
                ControllersProvider.mCellularTile,
                "handleSecondaryClick",
                finalView
            )
        }
        vibrate(0)
    }

    /**
     * Toggles the ringer modes
     * Normal -> Vibrate -> Silent -> Normal
     */
    private fun toggleRingerMode() {
        if (mAudioManager != null) {
            val mode = mAudioManager.ringerMode
            when (mode) {
                AudioManager.RINGER_MODE_NORMAL -> callMethod(
                    mAudioManager,
                    "setRingerModeInternal",
                    AudioManager.RINGER_MODE_VIBRATE
                )

                AudioManager.RINGER_MODE_VIBRATE -> callMethod(
                    mAudioManager,
                    "setRingerModeInternal",
                    AudioManager.RINGER_MODE_SILENT
                )

                AudioManager.RINGER_MODE_SILENT -> callMethod(
                    mAudioManager,
                    "setRingerModeInternal",
                    AudioManager.RINGER_MODE_NORMAL
                )
            }
            updateRingerButtonState()
            vibrate(1)
        }
    }

    private fun updateTileButtonState(
        iv: ImageView?,
        efab: ExtendedFAB?,
        active: Boolean,
        icon: Drawable?,
        text: String
    ) {
        post {
            if (iv != null) {
                iv.setImageDrawable(icon)
                setButtonActiveState(iv, null, active)
            }
            if (efab != null) {
                efab.setIcon(icon)
                efab.text = text
                setButtonActiveState(null, efab, active)
            }
        }
    }

    private fun updateTileButtonState(
        iv: ImageView?,
        efab: ExtendedFAB?,
        active: Boolean,
        activeResource: String,
        inactiveResource: String,
        activeString: String,
        inactiveString: String
    ) {
        post {
            var d: Drawable? = null;
            val resId = mContext.getResources().getIdentifier(
                if (active) activeResource else inactiveResource,
                "drawable",
                SYSTEMUI_PACKAGE
            )
            if (resId != 0) {
                d = ResourcesCompat.getDrawable(modRes!!, resId, mContext.theme)
            }
            if (iv != null) {
                iv.setImageDrawable(d)
                setButtonActiveState(iv, null, active)
            }
            if (efab != null) {
                efab.icon = d
                efab.text = if (active) activeString else inactiveString
                setButtonActiveState(null, efab, active)
            }
        }
    }

    fun updateTorchButtonState() {
        if (!isWidgetEnabled("torch")) return
        XposedBridge.log("LockscreenWidgets updateTorchButtonState $isFlashOn")
        updateTileButtonState(
            torchButton, torchButtonFab, isFlashOn,
            getDrawable(TORCH_RES_ACTIVE, SYSTEMUI_PACKAGE), getString(TORCH_LABEL, SYSTEMUI_PACKAGE))
    }

    private val mRingerModeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateRingerButtonState()
        }
    }

    init {
        instance = this

        mContext = context
        mAudioManager = mContext.getSystemService(AudioManager::class.java)
        mWifiManager = mContext.getSystemService(WifiManager::class.java)
        mCameraManager = mContext.getSystemService(CameraManager::class.java)
        mDarkColor = ResourcesCompat.getColor(
            modRes!!,
            R.color.lockscreen_widget_background_color_dark,
            mContext.theme
        )
        mLightColor = ResourcesCompat.getColor(
            modRes!!,
            R.color.lockscreen_widget_background_color_light,
            mContext.theme
        )
        mDarkColorActive = ResourcesCompat.getColor(
            modRes!!,
            R.color.lockscreen_widget_active_color_dark,
            mContext.theme
        )
        mLightColorActive = ResourcesCompat.getColor(
            modRes!!,
            R.color.lockscreen_widget_active_color_light,
            mContext.theme
        )

        mActivityLauncherUtils = ActivityLauncherUtils(mContext, activityStarter)

        mHandler = Handler(Looper.getMainLooper())
        
        try {
            mCameraId = mCameraManager.cameraIdList[0]
        } catch (e: Throwable) {
            XposedBridge.log("LockscreenWidgets error: " + e.message)
        }

        val container = LinearLayout(context)
        container.orientation = VERTICAL
        container.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Device Widget Container
        mDeviceWidgetContainer = createDeviceWidgetContainer(context)
        container.addView(mDeviceWidgetContainer)

        // Add main widgets container
        mMainWidgetsContainer = createMainWidgetsContainer(context)
        container.addView(mMainWidgetsContainer)

        // Add secondary widgets container
        mSecondaryWidgetsContainer = createSecondaryWidgetsContainer(context)
        container.addView(mSecondaryWidgetsContainer)

        addView(container)

        val ringerFilter = IntentFilter("android.media.INTERNAL_RINGER_MODE_CHANGED_ACTION")
        mContext.registerReceiver(mRingerModeReceiver, ringerFilter)
        mMediaUpdater = object : Runnable {
            override fun run() {
                updateMediaController()
                mHandler.postDelayed(this, 1000)
            }
        }
        updateMediaController()

        ControllersProvider.getInstance().registerMobileDataCallback(mMobileDataCallback)
        ControllersProvider.getInstance().registerWifiCallback(mWifiCallback)
        ControllersProvider.getInstance().registerBluetoothCallback(mBluetoothCallback)
        ControllersProvider.getInstance().registerTorchModeCallback(mTorchCallback)

        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                onVisible()
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    private fun updateWiFiButtonState(enabled: Boolean) {
        XposedBridge.log(
            "LockscreenWidgets updateWiFiButtonState " + enabled + " | " + isWidgetEnabled(
                "wifi"
            )
        )
        if (!isWidgetEnabled("wifi")) return
        if (wifiButton == null && wifiButtonFab == null) return
        var theSsid: String = mWifiManager!!.getConnectionInfo().getSSID()
        if (theSsid == WifiManager.UNKNOWN_SSID) {
            theSsid = getString(WIFI_LABEL, SYSTEMUI_PACKAGE)
        } else {
            if (theSsid.startsWith("\"") && theSsid.endsWith("\"")) {
                theSsid = theSsid.substring(1, theSsid.length - 1)
            }
        }
        val icon: Drawable? = null
        if (mWifiIndicators != null) {
            val qsIcon = getObjectField(mWifiIndicators, "qsIcon")
            if (qsIcon != null) {
                val iconRes = getIntField(qsIcon, "icon")
                val iconResId = mContext.resources.getIdentifier(
                    mContext.resources.getResourceEntryName(iconRes),
                    "drawable",
                    SYSTEMUI_PACKAGE
                )
                if (iconResId != 0) {
                    getDrawable(iconResId, SYSTEMUI_PACKAGE)
                }
            } else {
                getDrawable(WIFI_ACTIVE, SYSTEMUI_PACKAGE)
            }
        } else {
            getDrawable(WIFI_ACTIVE, SYSTEMUI_PACKAGE)
        }
        updateTileButtonState(
            wifiButton, wifiButtonFab,
            isWifiEnabled,
            icon, theSsid
        )
    }

    private fun updateRingerButtonState() {
        XposedBridge.log("LockscreenWidgets updateRingerButtonState " + (isWidgetEnabled("ringer")) + " | " + (ringerButton == null) + " | " + (ringerButtonFab == null))
        if (!isWidgetEnabled("ringer")) return
        if (ringerButton == null && ringerButtonFab == null) return
        if (mAudioManager != null) {
            val soundActive = mAudioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
            updateTileButtonState(
                ringerButton, ringerButtonFab,
                soundActive,
                ringerDrawable,
                ringerText
            )
        }
    }

    private fun updateMobileDataState(enabled: Boolean) {
        if (!isWidgetEnabled("data")) return
        if (dataButton == null && dataButtonFab == null) return
        val networkController: Any? = ControllersProvider.mNetworkController
        val networkName =
            if (networkController == null) "" else (callMethod(
                networkController,
                "getMobileDataNetworkName"
            ) as String)
        val hasNetwork = networkController != null && !TextUtils.isEmpty(networkName)
        val inactive = getString(DATA_LABEL, SYSTEMUI_PACKAGE)
        updateTileButtonState(
            dataButton,
            dataButtonFab,
            enabled,
            DATA_ACTIVE,
            DATA_INACTIVE,
            if (hasNetwork && enabled) networkName else inactive,
            inactive
        )
    }

    private fun toggleBluetoothState() {
        val bluetoothController: Any = ControllersProvider.mBluetoothController ?: return
        callMethod(bluetoothController, "setBluetoothEnabled", !isBluetoothEnabled)
        updateBtState()
        mHandler.postDelayed({ this.updateBtState() }, 350L)
        vibrate(1)
    }

    private fun showBluetoothDialog(view: View) {
        val finalView: View = if (view is ExtendedFAB) {
            view.parent as View
        } else {
            view
        }
        post {
            callMethod(
                ControllersProvider.mBluetoothTile,
                "handleSecondaryClick",
                finalView
            )
        }
        vibrate(0)
    }

    private fun updateBtState() {
        if (!isWidgetEnabled("bt")) return
        XposedBridge.log("LockscreenWidgets updateBtState $isBluetoothOn")
        if (btButton == null && btButtonFab == null) return
        val bluetoothController: Any? = ControllersProvider.mBluetoothController
        val deviceName = if (isBluetoothEnabled) callMethod(
            bluetoothController,
            "getConnectedDeviceName"
        ) as String else ""
        val isConnected = !TextUtils.isEmpty(deviceName)
        val inactiveString = getString(BT_LABEL, SYSTEMUI_PACKAGE)
        updateTileButtonState(
            btButton, btButtonFab, isBluetoothOn,
            BT_ACTIVE, BT_INACTIVE, if (isConnected) deviceName else inactiveString, inactiveString
        )
    }

    private val isBluetoothEnabled: Boolean
        get() {
            val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled
        }

    private fun sameSessions(a: MediaController?, b: MediaController): Boolean {
        if (a == b) {
            return true
        }
        if (a == null) {
            return false
        }
        return a == b
    }

    private fun getMediaControllerPlaybackState(controller: MediaController?): Int {
        if (controller != null) {
            val playbackState = controller.playbackState
            if (playbackState != null) {
                return playbackState.state
            }
        }
        return PlaybackState.STATE_NONE
    }

    /**
     * Set the options for the lockscreen widgets
     * @param lsWidgets true if lockscreen widgets are enabled
     * @param deviceWidget true if device widget is enabled
     * @param mainWidgets comma separated list of main widgets
     * @param secondaryWidgets comma separated list of secondary widgets
     */
    fun setOptions(
        lsWidgets: Boolean, deviceWidget: Boolean,
        mainWidgets: String, secondaryWidgets: String
    ) {
        XposedBridge.log(
            "LockscreenWidgets setOptions " + lsWidgets +
                    " | " + deviceWidget + " | " + mainWidgets + " | " + secondaryWidgets
        )
        instance!!.lockscreenWidgetsEnabled = lsWidgets
        instance!!.deviceWidgetsEnabled = deviceWidget
        instance!!.mMainLockscreenWidgetsList = mainWidgets
        instance!!.mMainWidgetsList = listOf(
            *instance!!.mMainLockscreenWidgetsList!!.split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray())
        instance!!.mSecondaryLockscreenWidgetsList = secondaryWidgets
        instance!!.mSecondaryWidgetsList = listOf(
            *instance!!.mSecondaryLockscreenWidgetsList!!.split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray())
        instance!!.updateWidgetViews()
    }

    /**
     * Set the options for the Device Widget
     * @param customColor true if custom color is enabled
     * @param linearColor color for linear battery progressbar
     * @param circularColor color for circular progressbar
     * @param textColor color for text
     * @param devName device name, keep blank for default Build.MODEL
     */
    fun setDeviceWidgetOptions(
        customColor: Boolean,
        linearColor: Int,
        circularColor: Int,
        textColor: Int,
        devName: String?
    ) {
        if (instance!!.mDeviceWidgetView == null) return
        instance!!.mDeviceWidgetView!!.setCustomColor(customColor, linearColor, circularColor)
        instance!!.mDeviceWidgetView!!.setTextCustomColor(textColor)
        instance!!.mDeviceWidgetView!!.setDeviceName(devName)
    }

    fun setCustomColors(
        customColorsEnabled: Boolean,
        bigInactive: Int, bigActive: Int, smallInactive: Int, smallActive: Int,
        bigIconInactive: Int, bigIconActive: Int, smallIconInactive: Int, smallIconActive: Int
    ) {
        XposedBridge.log("LockscreenWidgets setCustomColors $customColorsEnabled")
        instance!!.mCustomColors = customColorsEnabled
        instance!!.mBigInactiveColor = bigInactive
        instance!!.mBigActiveColor = bigActive
        instance!!.mSmallInactiveColor = smallInactive
        instance!!.mSmallActiveColor = smallActive
        instance!!.mBigIconInactiveColor = bigIconInactive
        instance!!.mBigIconActiveColor = bigIconActive
        instance!!.mSmallIconInactiveColor = smallIconInactive
        instance!!.mSmallIconActiveColor = smallIconActive
        instance!!.updateWidgetViews()
    }

    fun setActivityStarter(activityStarter: Any?) {
        mActivityLauncherUtils = ActivityLauncherUtils(mContext, activityStarter)
    }

    fun setDozingState(isDozing: Boolean) {
        XposedBridge.log("LockscreenWidgets setDozingState $isDozing")
        instance!!.mDozing = isDozing
        instance!!.updateContainerVisibility()
    }

    private fun getDrawable(drawableRes: String, pkg: String): Drawable? {
        try {
            return ContextCompat.getDrawable(
                mContext,
                mContext.resources.getIdentifier(drawableRes, "drawable", pkg)
            )
        } catch (t: Throwable) {
            // We have a calculator icon, so if SystemUI doesn't just return ours
            if ((drawableRes == CALCULATOR_ICON)) return ResourcesCompat.getDrawable(
                modRes!!,
                R.drawable.ic_calculator,
                mContext.theme
            )

            XposedBridge.log("LockscreenWidgets getDrawable $drawableRes from $pkg error $t")
            return null
        }
    }

    private fun getDrawable(drawableRes: Int, pkg: String): Drawable? {
        try {
            return ContextCompat.getDrawable(
                mContext,
                drawableRes
            )
        } catch (t: Throwable) {
            XposedBridge.log("LockscreenWidgets getDrawable $drawableRes from $pkg error $t")
            return null
        }
    }

    private fun getString(stringRes: String, pkg: String): String {
        try {
            return mContext.resources.getString(
                mContext.resources.getIdentifier(stringRes, "string", pkg)
            )
        } catch (t: Throwable) {
            when (stringRes) {
                CALCULATOR_LABEL -> {
                    return modRes!!.getString(R.string.calculator)
                }

                CAMERA_LABEL -> {
                    return modRes!!.getString(R.string.camera)
                }

                WALLET_LABEL -> {
                    return modRes!!.getString(R.string.wallet)
                }
            }
            XposedBridge.log("LockscreenWidgets getString $stringRes from $pkg error $t")
            return ""
        }
    }

    private val ringerDrawable: Drawable?
        get() {
            val resName = when (mAudioManager!!.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> RINGER_NORMAL
                AudioManager.RINGER_MODE_VIBRATE -> RINGER_VIBRATE
                AudioManager.RINGER_MODE_SILENT -> RINGER_SILENT
                else -> throw IllegalStateException("Unexpected value: " + mAudioManager.ringerMode)
            }

            return getDrawable(resName, SYSTEMUI_PACKAGE)
        }

    private val ringerText: String
        get() {
            val RINGER_NORMAL = "volume_footer_ring"
            val RINGER_VIBRATE = "state_button_vibration"
            val RINGER_SILENT = "state_button_silence"

            val resName = when (mAudioManager!!.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> RINGER_NORMAL
                AudioManager.RINGER_MODE_VIBRATE -> RINGER_VIBRATE
                AudioManager.RINGER_MODE_SILENT -> RINGER_SILENT
                else -> throw IllegalStateException("Unexpected value: " + mAudioManager.ringerMode)
            }

            return getString(resName, SYSTEMUI_PACKAGE)
        }

    /**
     * Vibrate the device
     * @param type 0 = click, 1 = tick
     */
    private fun vibrate(type: Int) {
        if (type == 0) {
            this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } else if (type == 1) {
            this.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    companion object {

        val BT_ACTIVE: String = "qs_bluetooth_icon_on"
        val BT_INACTIVE: String = "qs_bluetooth_icon_off"
        val DATA_ACTIVE: String = "status_bar_qs_data_active"
        val DATA_INACTIVE: String = "status_bar_qs_data_inactive"
        val RINGER_NORMAL: String = "status_bar_qs_mute_inactive"
        val RINGER_VIBRATE: String = "status_bar_qs_icon_volume_ringer_vibrate"
        val RINGER_SILENT: String = "status_bar_qs_mute_active"
        val TORCH_RES_ACTIVE: String = "qs_flashlight_icon_on"
        val TORCH_RES_INACTIVE: String = "qs_flashlight_icon_off"
        val WIFI_ACTIVE: String = "quick_settings_wifi_label"
        val WIFI_INACTIVE: String = "quick_settings_wifi_label"
        val HOME_CONTROLS: String = "controls_icon"
        val CALCULATOR_ICON: String = "status_bar_qs_calculator_inactive"
        val CAMERA_ICON: String =
            "status_bar_qs_camera_allowed" // Use qs camera access icon for camera
        val WALLET_ICON: String = "ic_wallet_lockscreen"

        val GENERAL_INACTIVE: String = "switch_bar_off"
        val GENERAL_ACTIVE: String = "switch_bar_on"

        val BT_LABEL: String = "quick_settings_bluetooth_label"
        val DATA_LABEL: String = "quick_settings_cellular_detail_title"
        val WIFI_LABEL: String = "quick_settings_wifi_label"
        val RINGER_LABEL_INACTIVE: String = "state_button_silence"
        val TORCH_LABEL: String = "quick_settings_flashlight_label"
        val HOME_CONTROLS_LABEL: String = "home_controls_dream_label"
        val MEDIA_PLAY_LABEL: String = "controls_media_button_play"
        val CALCULATOR_LABEL: String = "keyboard_shortcut_group_applications_calculator"
        val CAMERA_LABEL: String = "accessibility_camera_button"
        val WALLET_LABEL: String = "wallet_title"

        @Volatile
        private var instance: LockscreenWidgetsView? = null

        @JvmStatic
        fun getInstance(context: Context, activityStarter: Any?): LockscreenWidgetsView {
            return instance ?: synchronized(this) {
                instance ?: LockscreenWidgetsView(context, activityStarter).also { instance = it }
            }
        }

        @JvmStatic
        fun getInstance(): LockscreenWidgetsView? {
            return instance
        }
    }
}
