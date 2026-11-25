package io.rabbitcontrol.android.core.data

import android.content.Context
import androidx.compose.runtime.Immutable
import io.rabbitcontrol.android.core.input.ScancodeMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class QuickActionCategory(val label: String) {
    Zoom("缩放"),
    System("系统"),
    Edit("编辑"),
    Mouse("鼠标"),
    Mode("模式"),
    Other("其他"),
}

enum class QuickActionKind(
    val label: String,
    val shortLabel: String,
    val category: QuickActionCategory,
) {
    ZoomIn("放大画面", "放大", QuickActionCategory.Zoom),
    ZoomOut("缩小画面", "缩小", QuickActionCategory.Zoom),
    ResetZoom("重置缩放", "1:1", QuickActionCategory.Zoom),
    ShowDesktop("显示桌面", "显示桌面", QuickActionCategory.System),
    AllWindows("展示所有窗口", "所有窗口", QuickActionCategory.System),
    SwitchWindow("切换窗口", "切换窗口", QuickActionCategory.System),
    Screenshot("截屏", "截屏", QuickActionCategory.System),
    TaskManager("任务管理/强退", "任务管理", QuickActionCategory.System),
    LockScreen("锁定远端屏幕", "锁屏", QuickActionCategory.System),
    Copy("复制", "复制", QuickActionCategory.Edit),
    Paste("粘贴", "粘贴", QuickActionCategory.Edit),
    Undo("撤销", "撤销", QuickActionCategory.Edit),
    SelectAll("全选", "全选", QuickActionCategory.Edit),
    Enter("回车", "回车", QuickActionCategory.Edit),
    Escape("Esc 键", "Esc", QuickActionCategory.Edit),
    LeftClick("鼠标左键", "左键", QuickActionCategory.Mouse),
    RightClick("鼠标右键", "右键", QuickActionCategory.Mouse),
    MiddleClick("鼠标中键", "中键", QuickActionCategory.Mouse),
    HoldRightClick("按住右键模式", "右键模式", QuickActionCategory.Mouse),
    HoldDrag("按住拖动模式", "拖动模式", QuickActionCategory.Mouse),
    DragLock("锁定拖动切换", "锁定拖动", QuickActionCategory.Mouse),
    ScrollUp("向上滚动", "上滚", QuickActionCategory.Mouse),
    ScrollDown("向下滚动", "下滚", QuickActionCategory.Mouse),
    ToggleKeyboard("展开/收起键盘", "键盘", QuickActionCategory.Mode),
    ToggleTouchMode("触控/直点切换", "触控切换", QuickActionCategory.Mode),
    ToggleMute("静音切换", "静音", QuickActionCategory.Mode),
    ToggleHud("性能 HUD 切换", "HUD", QuickActionCategory.Mode),
    ToggleKeymap("按键映射开关", "按键映射", QuickActionCategory.Mode),
    ToggleOrientation("横竖屏切换", "横竖屏", QuickActionCategory.Mode),
    Empty("空槽位 (点击配置)", "自定义", QuickActionCategory.Other),
}

@Immutable
data class SidebarConfig(
    val leftSlots: List<QuickActionKind> = listOf(
        QuickActionKind.HoldRightClick,
        QuickActionKind.HoldDrag,
        QuickActionKind.ZoomIn,
    ),
    val rightSlots: List<QuickActionKind> = listOf(
        QuickActionKind.ToggleKeyboard,
        QuickActionKind.ShowDesktop,
        QuickActionKind.AllWindows,
    ),
    val showLandscapeSidebars: Boolean = true,
    val gestureGuideSeen: Boolean = false,
)

enum class VirtualKeyKind {
    Key,
    MouseButton,
    MouseWheel,
    DpadWasd,
    DpadArrows,
    QuickAction,
}

@Immutable
data class VirtualKeyItem(
    val id: String,
    val label: String,
    val kind: VirtualKeyKind,
    val scancode: Int = 0,
    val modifiers: Int = 0,
    val mouseButton: Int = 0,
    val wheelDelta: Int = 0,
    val actionKind: QuickActionKind? = null,
    val xNorm: Float,
    val yNorm: Float,
    val sizeDp: Int = 54,
)

@Immutable
data class KeyMapProfile(
    val id: String,
    val name: String,
    val items: List<VirtualKeyItem>,
)

@Immutable
data class KeyMapState(
    val enabled: Boolean = false,
    val opacity: Float = 0.62f,
    val activeProfileId: String = "wasd",
    val profiles: List<KeyMapProfile> = defaultKeyMapProfiles(),
) {
    val activeProfile: KeyMapProfile
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
}

fun defaultKeyMapProfiles(): List<KeyMapProfile> = listOf(
    KeyMapProfile(
        id = "wasd",
        name = "WASD 键位",
        items = listOf(
            VirtualKeyItem(
                id = "wasd_pad",
                label = "WASD",
                kind = VirtualKeyKind.DpadWasd,
                xNorm = 0.14f,
                yNorm = 0.72f,
                sizeDp = 128,
            ),
            VirtualKeyItem(
                id = "wasd_esc",
                label = "Esc",
                kind = VirtualKeyKind.Key,
                scancode = ScancodeMapper.SC_ESC,
                xNorm = 0.10f,
                yNorm = 0.18f,
                sizeDp = 48,
            ),
            VirtualKeyItem(
                id = "wasd_shift",
                label = "Shift",
                kind = VirtualKeyKind.Key,
                scancode = ScancodeMapper.SC_LEFT_SHIFT,
                modifiers = ScancodeMapper.MOD_LEFT_SHIFT,
                xNorm = 0.12f,
                yNorm = 0.42f,
                sizeDp = 54,
            ),
            VirtualKeyItem(
                id = "wasd_e",
                label = "E",
                kind = VirtualKeyKind.Key,
                scancode = 0x12,
                xNorm = 0.76f,
                yNorm = 0.56f,
                sizeDp = 52,
            ),
            VirtualKeyItem(
                id = "wasd_f",
                label = "F",
                kind = VirtualKeyKind.Key,
                scancode = 0x21,
                xNorm = 0.87f,
                yNorm = 0.50f,
                sizeDp = 52,
            ),
            VirtualKeyItem(
                id = "wasd_r",
                label = "R",
                kind = VirtualKeyKind.Key,
                scancode = 0x13,
                xNorm = 0.88f,
                yNorm = 0.34f,
                sizeDp = 50,
            ),
            VirtualKeyItem(
                id = "wasd_space",
                label = "Space",
                kind = VirtualKeyKind.Key,
                scancode = ScancodeMapper.SC_SPACE,
                xNorm = 0.86f,
                yNorm = 0.78f,
                sizeDp = 62,
            ),
            VirtualKeyItem(
                id = "wasd_lmb",
                label = "左键",
                kind = VirtualKeyKind.MouseButton,
                mouseButton = 0,
                xNorm = 0.73f,
                yNorm = 0.77f,
                sizeDp = 56,
            ),
            VirtualKeyItem(
                id = "wasd_rmb",
                label = "右键",
                kind = VirtualKeyKind.MouseButton,
                mouseButton = 1,
                xNorm = 0.63f,
                yNorm = 0.80f,
                sizeDp = 52,
            ),
        ),
    ),
    KeyMapProfile(
        id = "office",
        name = "办公快捷",
        items = listOf(
            VirtualKeyItem(
                id = "off_pad",
                label = "方向",
                kind = VirtualKeyKind.DpadArrows,
                xNorm = 0.14f,
                yNorm = 0.72f,
                sizeDp = 116,
            ),
            VirtualKeyItem(
                id = "off_esc",
                label = "Esc",
                kind = VirtualKeyKind.QuickAction,
                actionKind = QuickActionKind.Escape,
                xNorm = 0.10f,
                yNorm = 0.18f,
                sizeDp = 48,
            ),
            VirtualKeyItem(
                id = "off_copy",
                label = "复制",
                kind = VirtualKeyKind.QuickAction,
                actionKind = QuickActionKind.Copy,
                xNorm = 0.87f,
                yNorm = 0.34f,
                sizeDp = 54,
            ),
            VirtualKeyItem(
                id = "off_paste",
                label = "粘贴",
                kind = VirtualKeyKind.QuickAction,
                actionKind = QuickActionKind.Paste,
                xNorm = 0.87f,
                yNorm = 0.52f,
                sizeDp = 54,
            ),
            VirtualKeyItem(
                id = "off_undo",
                label = "撤销",
                kind = VirtualKeyKind.QuickAction,
                actionKind = QuickActionKind.Undo,
                xNorm = 0.75f,
                yNorm = 0.52f,
                sizeDp = 54,
            ),
            VirtualKeyItem(
                id = "off_enter",
                label = "回车",
                kind = VirtualKeyKind.Key,
                scancode = ScancodeMapper.SC_ENTER,
                xNorm = 0.86f,
                yNorm = 0.74f,
                sizeDp = 60,
            ),
        ),
    ),
    KeyMapProfile(
        id = "arrows",
        name = "方向与确认",
        items = listOf(
            VirtualKeyItem(
                id = "arr_pad",
                label = "方向",
                kind = VirtualKeyKind.DpadArrows,
                xNorm = 0.15f,
                yNorm = 0.72f,
                sizeDp = 126,
            ),
            VirtualKeyItem(
                id = "arr_esc",
                label = "Esc",
                kind = VirtualKeyKind.Key,
                scancode = ScancodeMapper.SC_ESC,
                xNorm = 0.10f,
                yNorm = 0.18f,
                sizeDp = 48,
            ),
            VirtualKeyItem(
                id = "arr_z",
                label = "Z",
                kind = VirtualKeyKind.Key,
                scancode = 0x2C,
                xNorm = 0.74f,
                yNorm = 0.78f,
                sizeDp = 56,
            ),
            VirtualKeyItem(
                id = "arr_x",
                label = "X",
                kind = VirtualKeyKind.Key,
                scancode = 0x2D,
                xNorm = 0.86f,
                yNorm = 0.64f,
                sizeDp = 56,
            ),
            VirtualKeyItem(
                id = "arr_space",
                label = "Space",
                kind = VirtualKeyKind.Key,
                scancode = ScancodeMapper.SC_SPACE,
                xNorm = 0.86f,
                yNorm = 0.82f,
                sizeDp = 58,
            ),
            VirtualKeyItem(
                id = "arr_enter",
                label = "Enter",
                kind = VirtualKeyKind.Key,
                scancode = ScancodeMapper.SC_ENTER,
                xNorm = 0.74f,
                yNorm = 0.58f,
                sizeDp = 54,
            ),
        ),
    ),
)

class SessionLayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_session_layout", Context.MODE_PRIVATE)

    private val _sidebarConfig = MutableStateFlow(loadSidebarConfig())
    val sidebarConfig: StateFlow<SidebarConfig> = _sidebarConfig.asStateFlow()

    private val _keymapState = MutableStateFlow(loadKeyMapState())
    val keymapState: StateFlow<KeyMapState> = _keymapState.asStateFlow()

    fun setLeftSlot(index: Int, action: QuickActionKind) {
        if (index !in 0..2) return
        val cur = _sidebarConfig.value
        val updated = cur.leftSlots.toMutableList()
        while (updated.size < 3) updated.add(QuickActionKind.Empty)
        updated[index] = action
        val next = cur.copy(leftSlots = updated.take(3))
        _sidebarConfig.value = next
        saveSidebarConfig(next)
    }

    fun setRightSlot(index: Int, action: QuickActionKind) {
        if (index !in 0..2) return
        val cur = _sidebarConfig.value
        val updated = cur.rightSlots.toMutableList()
        while (updated.size < 3) updated.add(QuickActionKind.Empty)
        updated[index] = action
        val next = cur.copy(rightSlots = updated.take(3))
        _sidebarConfig.value = next
        saveSidebarConfig(next)
    }

    fun setShowLandscapeSidebars(show: Boolean) {
        val next = _sidebarConfig.value.copy(showLandscapeSidebars = show)
        _sidebarConfig.value = next
        saveSidebarConfig(next)
    }

    fun markGestureGuideSeen() {
        if (_sidebarConfig.value.gestureGuideSeen) return
        val next = _sidebarConfig.value.copy(gestureGuideSeen = true)
        _sidebarConfig.value = next
        saveSidebarConfig(next)
    }

    fun setKeymapEnabled(enabled: Boolean) {
        updateKeymap { it.copy(enabled = enabled) }
    }

    fun setKeymapOpacity(opacity: Float) {
        updateKeymap { it.copy(opacity = opacity.coerceIn(0.25f, 0.85f)) }
    }

    fun selectProfile(profileId: String) {
        updateKeymap { state ->
            if (state.profiles.any { it.id == profileId }) state.copy(activeProfileId = profileId) else state
        }
    }

    fun moveItem(itemId: String, xNorm: Float, yNorm: Float) {
        updateActiveItems { items ->
            items.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        xNorm = xNorm.coerceIn(0.06f, 0.94f),
                        yNorm = yNorm.coerceIn(0.08f, 0.92f),
                    )
                } else {
                    item
                }
            }
        }
    }

    fun resizeItem(itemId: String, sizeDp: Int) {
        updateActiveItems { items ->
            items.map { item ->
                if (item.id == itemId) {
                    val maxSize = if (item.kind == VirtualKeyKind.DpadWasd || item.kind == VirtualKeyKind.DpadArrows) 168 else 84
                    val minSize = if (item.kind == VirtualKeyKind.DpadWasd || item.kind == VirtualKeyKind.DpadArrows) 92 else 42
                    item.copy(sizeDp = sizeDp.coerceIn(minSize, maxSize))
                } else {
                    item
                }
            }
        }
    }

    fun addItem(item: VirtualKeyItem) {
        updateActiveItems { items -> items + item }
    }

    fun removeItem(itemId: String) {
        updateActiveItems { items -> items.filterNot { it.id == itemId } }
    }

    fun resetActiveProfile() {
        val defaults = defaultKeyMapProfiles()
        updateKeymap { state ->
            val fallback = defaults.firstOrNull { it.id == state.activeProfileId } ?: defaults.first()
            state.copy(
                profiles = state.profiles.map { p ->
                    if (p.id == state.activeProfileId) fallback else p
                },
            )
        }
    }

    private fun updateActiveItems(transform: (List<VirtualKeyItem>) -> List<VirtualKeyItem>) {
        updateKeymap { state ->
            state.copy(
                profiles = state.profiles.map { p ->
                    if (p.id == state.activeProfileId) p.copy(items = transform(p.items)) else p
                },
            )
        }
    }

    private fun updateKeymap(transform: (KeyMapState) -> KeyMapState) {
        val next = transform(_keymapState.value)
        _keymapState.value = next
        saveKeyMapState(next)
    }

    private fun loadSidebarConfig(): SidebarConfig {
        val raw = prefs.getString("sidebar_config_v2", null) ?: return SidebarConfig()
        return runCatching {
            val obj = JSONObject(raw)
            val defaults = SidebarConfig()
            val leftArr = obj.optJSONArray("leftSlots") ?: JSONArray()
            val leftSlots = (0 until 3).map { i ->
                val name = leftArr.optString(i, "")
                QuickActionKind.entries.firstOrNull { it.name == name } ?: defaults.leftSlots[i]
            }
            val rightArr = obj.optJSONArray("rightSlots") ?: JSONArray()
            val rightSlots = (0 until 3).map { i ->
                val name = rightArr.optString(i, "")
                QuickActionKind.entries.firstOrNull { it.name == name } ?: defaults.rightSlots[i]
            }
            SidebarConfig(
                leftSlots = leftSlots,
                rightSlots = rightSlots,
                showLandscapeSidebars = obj.optBoolean("showLandscapeSidebars", true),
                gestureGuideSeen = obj.optBoolean("gestureGuideSeen", false),
            )
        }.getOrDefault(SidebarConfig())
    }

    private fun saveSidebarConfig(cfg: SidebarConfig) {
        val leftArr = JSONArray()
        cfg.leftSlots.take(3).forEach { leftArr.put(it.name) }
        val rightArr = JSONArray()
        cfg.rightSlots.take(3).forEach { rightArr.put(it.name) }
        val obj = JSONObject()
            .put("leftSlots", leftArr)
            .put("rightSlots", rightArr)
            .put("showLandscapeSidebars", cfg.showLandscapeSidebars)
            .put("gestureGuideSeen", cfg.gestureGuideSeen)
        prefs.edit().putString("sidebar_config_v2", obj.toString()).apply()
    }

    private fun loadKeyMapState(): KeyMapState {
        val raw = prefs.getString("keymap_state", null) ?: return KeyMapState()
        return runCatching {
            val obj = JSONObject(raw)
            val enabled = obj.optBoolean("enabled", false)
            val opacity = obj.optDouble("opacity", 0.62).toFloat().coerceIn(0.25f, 0.85f)
            val activeId = obj.optString("activeProfileId", "wasd")
            val profilesArr = obj.optJSONArray("profiles")
            val parsedProfiles = mutableListOf<KeyMapProfile>()
            if (profilesArr != null) {
                for (i in 0 until profilesArr.length()) {
                    val pObj = profilesArr.optJSONObject(i) ?: continue
                    val pId = pObj.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                    val pName = pObj.optString("name", pId)
                    val itemsArr = pObj.optJSONArray("items") ?: JSONArray()
                    val items = mutableListOf<VirtualKeyItem>()
                    for (j in 0 until itemsArr.length()) {
                        val itObj = itemsArr.optJSONObject(j) ?: continue
                        val kind = VirtualKeyKind.entries.firstOrNull { it.name == itObj.optString("kind") }
                            ?: continue
                        val actionName = itObj.optString("actionKind", "")
                        val actionKind = QuickActionKind.entries.firstOrNull { it.name == actionName }
                        items.add(
                            VirtualKeyItem(
                                id = itObj.optString("id", "k_${i}_$j"),
                                label = itObj.optString("label", "?"),
                                kind = kind,
                                scancode = itObj.optInt("scancode", 0),
                                modifiers = itObj.optInt("modifiers", 0),
                                mouseButton = itObj.optInt("mouseButton", 0),
                                wheelDelta = itObj.optInt("wheelDelta", 0),
                                actionKind = actionKind,
                                xNorm = itObj.optDouble("xNorm", 0.5).toFloat().coerceIn(0.06f, 0.94f),
                                yNorm = itObj.optDouble("yNorm", 0.5).toFloat().coerceIn(0.08f, 0.92f),
                                sizeDp = itObj.optInt("sizeDp", 54).coerceIn(42, 168),
                            ),
                        )
                    }
                    parsedProfiles.add(KeyMapProfile(id = pId, name = pName, items = items))
                }
            }
            val defaults = defaultKeyMapProfiles()
            val merged = defaults.map { def ->
                parsedProfiles.firstOrNull { it.id == def.id } ?: def
            } + parsedProfiles.filter { p -> defaults.none { it.id == p.id } }
            KeyMapState(
                enabled = enabled,
                opacity = opacity,
                activeProfileId = if (merged.any { it.id == activeId }) activeId else merged.first().id,
                profiles = merged,
            )
        }.getOrDefault(KeyMapState())
    }

    private fun saveKeyMapState(state: KeyMapState) {
        val profilesArr = JSONArray()
        state.profiles.forEach { profile ->
            val itemsArr = JSONArray()
            profile.items.forEach { item ->
                val itObj = JSONObject()
                    .put("id", item.id)
                    .put("label", item.label)
                    .put("kind", item.kind.name)
                    .put("scancode", item.scancode)
                    .put("modifiers", item.modifiers)
                    .put("mouseButton", item.mouseButton)
                    .put("wheelDelta", item.wheelDelta)
                    .put("actionKind", item.actionKind?.name ?: "")
                    .put("xNorm", item.xNorm.toDouble())
                    .put("yNorm", item.yNorm.toDouble())
                    .put("sizeDp", item.sizeDp)
                itemsArr.put(itObj)
            }
            profilesArr.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("items", itemsArr),
            )
        }
        val root = JSONObject()
            .put("enabled", state.enabled)
            .put("opacity", state.opacity.toDouble())
            .put("activeProfileId", state.activeProfileId)
            .put("profiles", profilesArr)
        prefs.edit().putString("keymap_state", root.toString()).apply()
    }
}
