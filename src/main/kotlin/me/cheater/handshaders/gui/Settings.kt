package me.cheater.handshaders.gui

import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import imgui.type.ImString
import me.cheater.handshaders.config.CHandShaders
import me.cheater.handshaders.utils.HandShaderImageLoader
import net.fabricmc.loader.api.FabricLoader
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.kotlinProperty

object Settings : ImGuiHandler.RenderInterface("HandShadersSettings") {
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("handshaders/tabs")

    enum class Type {
        TInteger,
        TFloat,
        TBoolean,
        TText,
        TImmutableText,
        TColor,
        TButton;

        companion object {
            fun fromKType(prop: KProperty<*>, type: KType): Type {
                val classifier = type.classifier
                return when {
                    classifier == Int::class -> TInteger
                    classifier == Float::class -> TFloat
                    classifier == Boolean::class -> TBoolean
                    classifier == String::class -> if (prop is KMutableProperty<*>) TText else TImmutableText
                    classifier == Color::class -> TColor
                    classifier is KClass<*> && Function::class.java.isAssignableFrom(classifier.java) -> TButton
                    else -> throw IllegalArgumentException("invalid type of setting: $type")
                }
            }
        }
    }

    data class SettingImpl(val annotation: Setting, val type: Type, val owner: Any, val prop: KProperty<*>) {
        var additional: Any? = null

        fun update() {
            if (type == Type.TText) {
                additional = ImString(get<String>(), 256)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> get(): T =
            (prop as KProperty<T>).getter.call(owner)

        @Suppress("UNCHECKED_CAST")
        fun set(value: Any) {
            (prop as KMutableProperty<Any>).setter.call(owner, value)
        }
    }

    data class TabImpl(val name: String, val settings: List<SettingImpl>)

    var tabs: List<TabImpl>

    init {
        val reflections = Reflections("me.cheater.handshaders.config")
        tabs = reflections[Scanners.TypesAnnotated.with(Tab::class.java).asClass<Any>()]
            .map { tab ->
                val instance = tab.kotlin.objectInstance
                    ?: throw IllegalArgumentException("You can't have tabs that are not objects: ${tab.name}")

                TabImpl(
                    name = tab.getAnnotation(Tab::class.java).tabName,
                    settings = instance::class.java.declaredFields.mapNotNull { javaProp ->
                        val prop = javaProp.kotlinProperty ?: return@mapNotNull null
                        val annotation = prop.findAnnotation<Setting>() ?: return@mapNotNull null
                        SettingImpl(annotation, Type.fromKType(prop, prop.returnType), instance, prop)
                    }
                )
            }
            .sortedBy { it.name }

        loadData()
        tabs.forEach { tab -> tab.settings.forEach(SettingImpl::update) }
    }

    fun saveData() {
        tabs.forEach { tab ->
            val currentTabFile = configPath.resolve("${tab.name}.txt")
            if (!currentTabFile.exists()) {
                currentTabFile.createParentDirectories()
                currentTabFile.createFile()
            }

            Files.newBufferedWriter(currentTabFile).use { writer ->
                tab.settings.forEach { setting ->
                    when (setting.type) {
                        Type.TInteger -> writer.write("${setting.prop.name}:${setting.get<Int>()}\n")
                        Type.TFloat -> writer.write("${setting.prop.name}:${setting.get<Float>()}\n")
                        Type.TBoolean -> writer.write("${setting.prop.name}:${setting.get<Boolean>()}\n")
                        Type.TText -> writer.write("${setting.prop.name}:${setting.get<String>()}\n")
                        Type.TImmutableText -> {}
                        Type.TColor -> writer.write("${setting.prop.name}:${setting.get<Color>().rgb}\n")
                        Type.TButton -> {}
                    }
                }
            }
        }
    }

    fun loadData() {
        tabs.forEach { tab ->
            val currentTabFile = configPath.resolve("${tab.name}.txt")
            if (!currentTabFile.exists()) return@forEach

            Files.newBufferedReader(currentTabFile).use { reader ->
                while (true) {
                    try {
                        val line = reader.readLine() ?: break
                        val split = line.split(":", limit = 2)
                        val setting = tab.settings.find { it.prop.name == split[0] } ?: continue
                        if (setting.type == Type.TImmutableText) continue

                        val value: Any = when (setting.type) {
                            Type.TInteger -> split[1].toInt()
                            Type.TFloat -> split[1].toFloat()
                            Type.TBoolean -> split[1].toBoolean()
                            Type.TText -> split[1]
                            Type.TImmutableText -> error("immutable text is not settable")
                            Type.TColor -> Color(split[1].toInt(), true)
                            Type.TButton -> error("buttons are not persisted")
                        }
                        setting.set(value)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun validateImageSelection() {
        tabs.forEach { tab ->
            tab.settings.forEach { setting ->
                if (setting.prop.name == "imageSelection" && setting.type == Type.TText) {
                    val currentSelection = setting.get<String>()
                    if (currentSelection.isEmpty() || currentSelection == CHandShaders.NO_IMAGE) return@forEach
                    if (!HandShaderImageLoader.imageExists(currentSelection)) {
                        setting.set(CHandShaders.NO_IMAGE)
                    }
                }
            }
        }
    }

    override fun render(io: ImGuiIO) {
        ImGui.setNextWindowSize(980f, 700f, ImGuiCond.FirstUseEver)

        val flags = ImGuiWindowFlags.NoCollapse

        if (ImGui.begin("HandShaders", flags)) {
            if (ImGui.beginTabBar("bar")) {
                tabs.forEach { tab ->
                    if (ImGui.beginTabItem(tab.name)) {
                        tab.settings.forEach { setting ->
                            if (setting.annotation.sameLineBefore) {
                                ImGui.sameLine()
                            }

                            if (setting.annotation.lineBefore) {
                                ImGui.separator()
                            }

                            val name = if (setting.annotation.dontRenderName) {
                                "##${setting.prop.name}"
                            } else if (setting.annotation.renderNameLeft) {
                                ImGui.text(setting.annotation.name)
                                ImGui.sameLine()
                                "##${setting.prop.name}"
                            } else {
                                "${setting.annotation.name}##${setting.prop.name}"
                            }

                            when (setting.type) {
                                Type.TBoolean -> if (ImGui.checkbox(name, setting.get<Boolean>())) {
                                    setting.set(!setting.get<Boolean>())
                                }

                                Type.TInteger -> {
                                    val value = setting.get<Int>()
                                    if (setting.annotation.combo.isNotEmpty()) {
                                        val selected = ImInt(value)
                                        if (ImGui.combo(name, selected, setting.annotation.combo)) {
                                            setting.set(selected.get())
                                        }
                                    } else {
                                        val mutable = IntArray(1) { value }
                                        if (ImGui.sliderInt(name, mutable, setting.annotation.min.toInt(), setting.annotation.max.toInt())) {
                                            setting.set(mutable[0])
                                        }
                                    }
                                }

                                Type.TFloat -> {
                                    val mutable = floatArrayOf(setting.get())
                                    if (ImGui.sliderFloat(name, mutable, setting.annotation.min.toFloat(), setting.annotation.max.toFloat())) {
                                        setting.set(mutable[0])
                                    }
                                }

                                Type.TColor -> {
                                    val components = setting.get<Color>().getRGBComponents(null)
                                    if (ImGui.colorEdit4(name, components)) {
                                        setting.set(
                                            Color(
                                                components[0].coerceIn(0f, 1f),
                                                components[1].coerceIn(0f, 1f),
                                                components[2].coerceIn(0f, 1f),
                                                components[3].coerceIn(0f, 1f)
                                            )
                                        )
                                    }
                                }

                                Type.TText -> {
                                    if (setting.prop.name == "imageSelection") {
                                        val imageNames = HandShaderImageLoader.getImageNames()
                                        val currentSelection = setting.get<String>()
                                        val currentIndex = imageNames.indexOf(currentSelection).coerceAtLeast(0)
                                        val selected = ImInt(currentIndex)
                                        if (ImGui.combo(name, selected, imageNames, imageNames.size)) {
                                            val selectedIndex = selected.get()
                                            if (selectedIndex in imageNames.indices) {
                                                setting.set(imageNames[selectedIndex])
                                            }
                                        }
                                    } else {
                                        val mutable = setting.additional as ImString
                                        if (ImGui.inputText(name, mutable, ImGuiInputTextFlags.CallbackResize)) {
                                            setting.set(mutable.get())
                                        }
                                    }
                                }

                                Type.TButton -> if (ImGui.button(name)) {
                                    setting.get<() -> Unit>().invoke()
                                }

                                Type.TImmutableText -> ImGui.text(setting.get<String>())
                            }

                            if (setting.annotation.sameLineAfter) {
                                ImGui.sameLine()
                            }

                            if (setting.annotation.lineAfter) {
                                ImGui.separator()
                            }
                        }

                        ImGui.endTabItem()
                    }
                }
                ImGui.endTabBar()
            }
        }

        ImGui.end()
    }

    override fun onClose() {
        saveData()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
