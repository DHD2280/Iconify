package com.drdisagree.iconify.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.SWITCH_ANIMATION_DELAY
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_ENABLED
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_EXTRAS
import com.drdisagree.iconify.config.RPrefs
import com.drdisagree.iconify.config.RPrefs.getBoolean
import com.drdisagree.iconify.config.RPrefs.putBoolean
import com.drdisagree.iconify.config.RPrefs.putString
import com.drdisagree.iconify.databinding.FragmentXposedLockscreenWidgetsBinding
import com.drdisagree.iconify.ui.base.BaseFragment
import com.drdisagree.iconify.ui.utils.ViewHelper.setHeader
import com.drdisagree.iconify.utils.SystemUtil

class XposedLockscreenWidgets : BaseFragment() {

    private lateinit var binding: FragmentXposedLockscreenWidgetsBinding

    private var bigWidget1 = 0
    private var bigWidget2 = 0
    private var miniWidget1 = 0
    private var miniWidget2 = 0
    private var miniWidget3 = 0
    private var miniWidget4 = 0

    private var mWidgetsValues: Array<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentXposedLockscreenWidgetsBinding.inflate(inflater, container, false)

        val view: View = binding.getRoot()

        // Header
        setHeader(
            requireContext(),
            getParentFragmentManager(),
            binding.header.toolbar,
            R.string.activity_title_lockscreen_widgets
        )

        readPrefs()
        updateEnabled(getBoolean(LOCKSCREEN_WIDGETS_ENABLED, false))

        // Main Switch
        binding.lockscreenWidgetsSwitch.isSwitchChecked = getBoolean(LOCKSCREEN_WIDGETS_ENABLED, false)
        binding.lockscreenWidgetsSwitch.setSwitchChangeListener { _: CompoundButton?, isChecked: Boolean ->

            putBoolean(LOCKSCREEN_WIDGETS_ENABLED, isChecked)

            updateEnabled(isChecked)

            Handler(Looper.getMainLooper()).postDelayed(
                { SystemUtil.handleSystemUIRestart() },
                SWITCH_ANIMATION_DELAY
            )
        }

        // Device Widget
        binding.deviceWidget.isSwitchChecked = getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET, false)
        binding.deviceWidget.setSwitchChangeListener { _: CompoundButton?, isChecked: Boolean ->
            putBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET, isChecked)
        }

        // Big Widget 1
        binding.bigWidget1.setSelectedIndex(bigWidget1)
        binding.bigWidget1.setOnItemSelectedListener { index: Int ->
            bigWidget1 = index
            updatePrefs()
        }

        // Big Widget 2
        binding.bigWidget2.setSelectedIndex(bigWidget2)
        binding.bigWidget2.setOnItemSelectedListener { index: Int ->
            bigWidget2 = index
            updatePrefs()
        }

        // Mini Widget 1
        binding.miniWidget1.setSelectedIndex(miniWidget1)
        binding.miniWidget1.setOnItemSelectedListener { index: Int ->
            miniWidget1 = index
            updatePrefs()
        }

        // Mini Widget 2
        binding.miniWidget2.setSelectedIndex(miniWidget2)
        binding.miniWidget2.setOnItemSelectedListener { index: Int ->
            miniWidget2 = index
            updatePrefs()
        }

        // Mini Widget 3
        binding.miniWidget3.setSelectedIndex(miniWidget3)
        binding.miniWidget3.setOnItemSelectedListener { index: Int ->
            miniWidget3 = index
            updatePrefs()
        }

        // Mini Widget 4
        binding.miniWidget4.setSelectedIndex(miniWidget4)
        binding.miniWidget4.setOnItemSelectedListener { index: Int ->
            miniWidget4 = index
            updatePrefs()
        }

        return view
    }

    private fun updateEnabled(enabled: Boolean) {
        binding.deviceWidget.setEnabled(enabled)
        binding.bigWidget1.setEnabled(enabled)
        binding.bigWidget2.setEnabled(enabled)
        binding.miniWidget1.setEnabled(enabled)
        binding.miniWidget2.setEnabled(enabled)
        binding.miniWidget3.setEnabled(enabled)
        binding.miniWidget4.setEnabled(enabled)
    }

    private fun readPrefs() {
        val mainWidgets: String? = RPrefs.getString(LOCKSCREEN_WIDGETS, "")
        val extraWidgets: String? = RPrefs.getString(LOCKSCREEN_WIDGETS_EXTRAS, "")

        mWidgetsValues = resources.getStringArray(R.array.lockscreen_widgets_values)

        fun assignWidgets(widgets: Array<String>, indices: MutableList<Int>) {
            for (i in widgets.indices) {
                indices[i] = getWidgetIndex(mWidgetsValues!!, widgets[i])
            }
        }

        val mainWi = mainWidgets?.split(",")?.toTypedArray() ?: emptyArray()
        val bigWidgets = mutableListOf(0, 0)
        assignWidgets(mainWi, bigWidgets)
        bigWidget1 = bigWidgets[0]
        bigWidget2 = bigWidgets[1]

        val extraWi = extraWidgets?.split(",")?.toTypedArray() ?: emptyArray()
        val miniWidgets = mutableListOf(0, 0, 0, 0)
        assignWidgets(extraWi, miniWidgets)
        miniWidget1 = miniWidgets[0]
        miniWidget2 = miniWidgets[1]
        miniWidget3 = miniWidgets[2]
        miniWidget4 = miniWidgets[3]

    }

    private fun getWidgetIndex(values: Array<String>, widget: String): Int {
        val mainIndex = values.indexOf(widget)
        return if (mainIndex != -1) {
            mainIndex
        } else {
            val extraIndex = values.indexOf(widget)
            if (extraIndex != -1) values.size + extraIndex else 0
        }
    }

    private fun updatePrefs() {
        val mainWidgets = "${mWidgetsValues!![bigWidget1]},${mWidgetsValues!![bigWidget2]}"
        val extraWidgets = "${mWidgetsValues!![miniWidget1]},${mWidgetsValues!![miniWidget2]},${mWidgetsValues!![miniWidget3]},${mWidgetsValues!![miniWidget4]}"

        Log.d("LockscreenWidgets", "Main: $mainWidgets")
        Log.d("LockscreenWidgets", "Extra: $extraWidgets")

        putString(LOCKSCREEN_WIDGETS, mainWidgets)
        putString(LOCKSCREEN_WIDGETS_EXTRAS, extraWidgets)
    }

}