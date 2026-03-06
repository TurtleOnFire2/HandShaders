package me.cheater.handshaders.gui

import me.cheater.handshaders.config.HandShaderConfig
import me.cheater.handshaders.utils.HandShaderImageLoader
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.math.roundToInt

class HandShaderConfigScreen(private val parent: Screen?) : Screen(Component.literal("HandShaders")) {
    companion object {
        private const val SV_STEPS = 24
        private const val HUE_STEPS = 32
    }

    private lateinit var enabledButton: Button
    private lateinit var imageNameBox: EditBox

    private var hue = 0f
    private var saturation = 1f
    private var brightness = 1f
    private var fillAlphaValue = 0
    private var imageAlphaValue = 0

    private var draggingSv = false
    private var draggingHue = false
    private var draggingFillAlpha = false
    private var draggingImageAlpha = false

    override fun init() {
        super.init()
        clearWidgets()

        val initial = HandShaderConfig.outlineColor()
        val hsb = Color.RGBtoHSB(initial.red, initial.green, initial.blue, null)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
        fillAlphaValue = HandShaderConfig.fillAlpha
        imageAlphaValue = HandShaderConfig.imageAlpha

        val panelX = panelX()
        val panelY = panelY()
        val rightCol = panelX + 158

        enabledButton = addRenderableWidget(
            Button.builder(enabledText()) {
                HandShaderConfig.enabled = !HandShaderConfig.enabled
                it.message = enabledText()
                persistState()
            }.bounds(rightCol, panelY + 16, 112, 20).build()
        )

        imageNameBox = addRenderableWidget(
            EditBox(font, rightCol, panelY + 42, 112, 18, Component.literal("Image Name"))
        ).apply {
            setMaxLength(128)
            value = HandShaderConfig.imageName
            setResponder {
                HandShaderConfig.imageName = it.trim()
            }
        }

        addRenderableWidget(
            Button.builder(Component.literal("Reload")) {
                HandShaderImageLoader.reload()
            }.bounds(rightCol, panelY + 66, 54, 18).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Folder")) {
                Util.getPlatform().openPath(HandShaderImageLoader.imagesPath)
            }.bounds(rightCol + 58, panelY + 66, 54, 18).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val panelX = panelX()
        val panelY = panelY()
        val panelW = 280
        val panelH = 196
        val svX = panelX + 10
        val svY = panelY + 16
        val svSize = 128
        val hueX = svX + svSize + 8
        val hueY = svY
        val hueW = 10
        val hueH = svSize

        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF01A1D22.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF3A4048.toInt())
        guiGraphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF3A4048.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF3A4048.toInt())
        guiGraphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF3A4048.toInt())

        guiGraphics.drawString(font, title, panelX + 10, panelY + 6, Color.WHITE.rgb)

        drawSvSquare(guiGraphics, svX, svY, svSize)
        drawHueStrip(guiGraphics, hueX - 3, hueY, hueW, hueH)

        drawCrosshair(guiGraphics, svX, svY, svSize)
        drawHueMarker(guiGraphics, hueX - 3, hueY, hueW, hueH)

        drawValueSlider(
            guiGraphics = guiGraphics,
            x = svX,
            y = panelY + 132,
            width = 142,
            label = "Fill Alpha",
            description = "Solid tint inside the hand/item",
            value = fillAlphaValue,
            valuePrefix = "Fill"
        )
        drawValueSlider(
            guiGraphics = guiGraphics,
            x = svX,
            y = panelY + 156,
            width = 142,
            label = "Image Alpha",
            description = "Opacity of the selected PNG overlay",
            value = imageAlphaValue,
            valuePrefix = "Image"
        )

        val rightCol = panelX + 158

        drawPreview(guiGraphics, rightCol, panelY + 91, 112, 53)

        guiGraphics.drawString(font, "Fill Alpha", rightCol, panelY + 150, Color.WHITE.rgb)
        guiGraphics.drawString(font, "Image Alpha", rightCol, panelY + 174, Color.WHITE.rgb)


        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val handled = super.mouseClicked(click, doubled)
        if (click.button() != 0) return handled

        updateDragState(click.x(), click.y(), true)
        return handled || draggingSv || draggingHue || draggingFillAlpha || draggingImageAlpha
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        if (click.button() == 0 && (draggingSv || draggingHue || draggingFillAlpha || draggingImageAlpha)) {
            updateActiveDrag(click.x(), click.y())
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        draggingSv = false
        draggingHue = false
        draggingFillAlpha = false
        draggingImageAlpha = false
        return super.mouseReleased(click)
    }

    private fun updateDragState(mouseX: Double, mouseY: Double, apply: Boolean) {
        val sv = svBounds()
        val hueBounds = hueBounds()
        val fillBounds = fillSliderBounds()
        val imageBounds = imageSliderBounds()

        draggingSv = contains(mouseX, mouseY, sv[0], sv[1], sv[2], sv[3])
        draggingHue = !draggingSv && contains(mouseX, mouseY, hueBounds[0], hueBounds[1], hueBounds[2], hueBounds[3])
        draggingFillAlpha = !draggingSv && !draggingHue && contains(mouseX, mouseY, fillBounds[0], fillBounds[1], fillBounds[2], fillBounds[3])
        draggingImageAlpha = !draggingSv && !draggingHue && !draggingFillAlpha && contains(mouseX, mouseY, imageBounds[0], imageBounds[1], imageBounds[2], imageBounds[3])

        if (apply) {
            updateActiveDrag(mouseX, mouseY)
        }
    }

    private fun updateActiveDrag(mouseX: Double, mouseY: Double) {
        val sv = svBounds()
        val hueB = hueBounds()
        val fillB = fillSliderBounds()
        val imageB = imageSliderBounds()

        if (draggingSv) {
            saturation = ((mouseX - sv[0]) / (sv[2] - sv[0])).toFloat().coerceIn(0f, 1f)
            brightness = (1.0 - (mouseY - sv[1]) / (sv[3] - sv[1])).toFloat().coerceIn(0f, 1f)
            persistState()
        } else if (draggingHue) {
            hue = ((mouseY - hueB[1]) / (hueB[3] - hueB[1])).toFloat().coerceIn(0f, 1f)
            persistState()
        } else if (draggingFillAlpha) {
            fillAlphaValue = sliderValue(mouseX, fillB[0], fillB[2])
            persistState()
        } else if (draggingImageAlpha) {
            imageAlphaValue = sliderValue(mouseX, imageB[0], imageB[2])
            persistState()
        }
    }

    private fun drawSvSquare(guiGraphics: GuiGraphics, x: Int, y: Int, size: Int) {
        for (ix in 0 until SV_STEPS) {
            val left = x + ix * size / SV_STEPS
            val right = x + (ix + 1) * size / SV_STEPS
            val s = ix / (SV_STEPS - 1f)
            for (iy in 0 until SV_STEPS) {
                val top = y + iy * size / SV_STEPS
                val bottom = y + (iy + 1) * size / SV_STEPS
                val b = 1f - iy / (SV_STEPS - 1f)
                guiGraphics.fill(left, top, right, bottom, hsbToArgb(hue, s, b, 1f))
            }
        }
    }

    private fun drawHueStrip(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        for (iy in 0 until HUE_STEPS) {
            val top = y + iy * height / HUE_STEPS
            val bottom = y + (iy + 1) * height / HUE_STEPS
            val h = iy / (HUE_STEPS - 1f)
            guiGraphics.fill(x, top, x + width, bottom, hsbToArgb(h, 1f, 1f, 1f))
        }
    }

    private fun drawPreview(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        guiGraphics.fill(x, y, x + width, y + height, currentColor().rgb)
        guiGraphics.fill(x, y, x + width, y + 1, 0xFFFFFFFF.toInt())
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF.toInt())
        guiGraphics.fill(x, y, x + 1, y + height, 0xFFFFFFFF.toInt())
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFFFFFFFF.toInt())
    }

    private fun drawValueSlider(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        label: String,
        description: String,
        value: Int,
        valuePrefix: String
    ) {
        guiGraphics.drawString(font, label, x, y, 0xFFFFFF)
        guiGraphics.drawString(font, description, x, y + 9, 0x9AA3AD)

        val barY = y + 18
        guiGraphics.fill(x, barY, x + width, barY + 8, 0xFF22272E.toInt())
        val filled = (value.coerceIn(0, 255) / 255f * width).roundToInt()
        guiGraphics.fill(x, barY, x + filled, barY + 8, 0xFF59C3C3.toInt())
        val knobX = x + filled.coerceIn(0, width)
        guiGraphics.fill(knobX - 1, barY - 2, knobX + 1, barY + 10, 0xFFFFFFFF.toInt())
        val valueText = "$valuePrefix: $value"
        guiGraphics.drawString(font, valueText, x + width - font.width(valueText), y, 0xFFFFFF)
    }

    private fun drawCrosshair(guiGraphics: GuiGraphics, x: Int, y: Int, size: Int) {
        val cx = x + (saturation * (size - 1)).roundToInt()
        val cy = y + ((1f - brightness) * (size - 1)).roundToInt()
        guiGraphics.fill(cx - 2, cy, cx + 3, cy + 1, 0xFF000000.toInt())
        guiGraphics.fill(cx, cy - 2, cx + 1, cy + 3, 0xFF000000.toInt())
        guiGraphics.fill(cx - 1, cy, cx + 2, cy + 1, 0xFFFFFFFF.toInt())
        guiGraphics.fill(cx, cy - 1, cx + 1, cy + 2, 0xFFFFFFFF.toInt())
    }

    private fun drawHueMarker(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        val markerY = y + (hue * (height - 1)).roundToInt()
        guiGraphics.fill(x - 2, markerY - 1, x + width + 2, markerY + 1, 0xFFFFFFFF.toInt())
    }

    private fun panelX(): Int = width / 2 - 140
    private fun panelY(): Int = height / 2 - 116

    private fun svBounds(): IntArray = intArrayOf(panelX() + 10, panelY() + 16, panelX() + 138, panelY() + 144)
    private fun hueBounds(): IntArray = intArrayOf(panelX() + 143, panelY() + 16, panelX() + 153, panelY() + 144)
    private fun fillSliderBounds(): IntArray = intArrayOf(panelX() + 10, panelY() + 146, panelX() + 156, panelY() + 154)
    private fun imageSliderBounds(): IntArray = intArrayOf(panelX() + 10, panelY() + 170, panelX() + 156, panelY() + 178)

    private fun contains(mouseX: Double, mouseY: Double, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom
    }

    private fun sliderValue(mouseX: Double, left: Int, right: Int): Int {
        return (((mouseX - left) / (right - left)).toFloat().coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun currentColor(): Color {
        val rgb = Color.HSBtoRGB(hue, saturation, brightness)
        return Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, 255)
    }

    private fun hsbToArgb(h: Float, s: Float, b: Float, a: Float): Int {
        val rgb = Color.HSBtoRGB(h, s, b)
        return ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (rgb and 0x00FFFFFF)
    }

    private fun rgbaText(): String {
        val color = currentColor()
        return "Outline ${color.red}, ${color.green}, ${color.blue}"
    }

    private fun enabledText(): Component =
        Component.literal("Enabled: ${if (HandShaderConfig.enabled) "ON" else "OFF"}")

    private fun persistState() {
        HandShaderConfig.setOutlineColor(currentColor())
        HandShaderConfig.fillAlpha = fillAlphaValue
        HandShaderConfig.imageName = imageNameBox.value.trim()
        HandShaderConfig.imageAlpha = imageAlphaValue
    }

    override fun onClose() {
        persistState()
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
