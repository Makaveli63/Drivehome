package app.drivehome

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import java.io.File
import java.util.Locale

/** Pièces mobiles d'un modèle 3D. */
enum class CarPart(val key: String, val label: String) {
    FL("fl", "Porte avant gauche"),
    FR("fr", "Porte avant droite"),
    RL("rl", "Porte arrière gauche"),
    RR("rr", "Porte arrière droite"),
    TRUNK("trunk", "Coffre"),
    HOOD("hood", "Capot"),
}

object Car3D {
    fun file(ctx: Context) = File(ctx.filesDir, "car_model.glb")

    fun exists(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() > 20 }

    /** Copie le fichier choisi par l'utilisateur ; vérifie l'en-tête « glTF ». */
    fun import(ctx: Context, uri: Uri): String? = try {
        val tmp = File(ctx.filesDir, "car_model.tmp")
        ctx.contentResolver.openInputStream(uri)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            ?: return "Fichier illisible"
        val head = tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        if (String(head, Charsets.US_ASCII) != "glTF") {
            tmp.delete()
            "Ce n'est pas un fichier .glb (glTF binaire)"
        } else {
            val dest = file(ctx)
            dest.delete()
            tmp.renameTo(dest)
            null
        }
    } catch (e: Exception) {
        "Import impossible : ${e.message}"
    }

    fun delete(ctx: Context) {
        file(ctx).delete()
    }

    /** Mots-clés usuels des modèles pour deviner chaque pièce. */
    fun guess(part: CarPart, names: List<String>): String? {
        fun n(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")
        val keys: List<List<String>> = when (part) {
            CarPart.FL -> listOf(listOf("door", "fl"), listOf("door", "lf"), listOf("door", "frontleft"), listOf("door", "leftfront"), listOf("porte", "avg"))
            CarPart.FR -> listOf(listOf("door", "fr"), listOf("door", "rf"), listOf("door", "frontright"), listOf("door", "rightfront"), listOf("porte", "avd"))
            CarPart.RL -> listOf(listOf("door", "rl"), listOf("door", "lr"), listOf("door", "rearleft"), listOf("door", "leftrear"), listOf("door", "backleft"))
            CarPart.RR -> listOf(listOf("door", "rr"), listOf("door", "rearright"), listOf("door", "rightrear"), listOf("door", "backright"))
            CarPart.TRUNK -> listOf(listOf("trunk"), listOf("boot"), listOf("tailgate"), listOf("hatch"), listOf("coffre"))
            CarPart.HOOD -> listOf(listOf("hood"), listOf("bonnet"), listOf("capot"))
        }
        return names.firstOrNull { name -> keys.any { ks -> ks.all { n(name).contains(it) } } }
    }
}

/**
 * Affiche le vrai modèle 3D importé (.glb). Rotation et zoom au doigt.
 * Les pièces associées (portes, coffre, capot) pivotent quand elles s'ouvrent.
 */
@Composable
fun Car3DView(
    doors: Doors,
    prefs: Prefs,
    modelVersion: Long,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onPartsListed: (List<String>) -> Unit = {},
    onError: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val centerNode = rememberNode(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 2.4f, y = 1.3f, z = 3.2f)
        lookAt(centerNode)
    }

    val modelNode: ModelNode? = remember(modelVersion) {
        try {
            ModelNode(
                modelInstance = modelLoader.createModelInstance(Car3D.file(ctx)),
                scaleToUnits = 2.4f,
                centerOrigin = Position(x = 0f, y = 0f, z = 0f),
            )
        } catch (e: Throwable) {
            onError("Modèle 3D illisible : ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }
    DisposableEffect(modelNode) {
        onDispose { modelNode?.destroy() }
    }

    // Rotation d'origine de chaque pièce nommée
    val parts: Map<String, Pair<Node, Rotation>> = remember(modelNode) {
        modelNode?.nodes?.mapNotNull { nd -> nd.name?.let { it to (nd to nd.rotation) } }?.toMap() ?: emptyMap()
    }
    LaunchedEffect(parts) { onPartsListed(parts.keys.sorted()) }

    val spec = tween<Float>(700)
    val fl by animateFloatAsState(if (doors.fl) 1f else 0f, spec, label = "fl3d")
    val fr by animateFloatAsState(if (doors.fr) 1f else 0f, spec, label = "fr3d")
    val rl by animateFloatAsState(if (doors.rl) 1f else 0f, spec, label = "rl3d")
    val rr by animateFloatAsState(if (doors.rr) 1f else 0f, spec, label = "rr3d")
    val tr by animateFloatAsState(if (doors.trunk) 1f else 0f, spec, label = "tr3d")
    val hd by animateFloatAsState(if (doors.hood) 1f else 0f, spec, label = "hd3d")

    fun apply(part: CarPart, t: Float) {
        val name = prefs.partNode(part).ifEmpty { Car3D.guess(part, parts.keys.toList()) ?: "" }
        val (node, base) = parts[name] ?: return
        val sign = if (prefs.partInvert(part)) -1f else 1f
        val delta = when (part) {
            CarPart.FL, CarPart.RL -> Rotation(y = -60f * t * sign)
            CarPart.FR, CarPart.RR -> Rotation(y = 60f * t * sign)
            CarPart.TRUNK -> Rotation(x = -70f * t * sign)
            CarPart.HOOD -> Rotation(x = 50f * t * sign)
        }
        node.rotation = base + delta
    }
    apply(CarPart.FL, fl)
    apply(CarPart.FR, fr)
    apply(CarPart.RL, rl)
    apply(CarPart.RR, rr)
    apply(CarPart.TRUNK, tr)
    apply(CarPart.HOOD, hd)

    val manipulator = rememberCameraManipulator(
        orbitHomePosition = cameraNode.worldPosition,
        targetPosition = centerNode.worldPosition,
    )
    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        cameraManipulator = if (interactive) manipulator else null,
        childNodes = listOfNotNull(centerNode, modelNode),
        isOpaque = false,
    )
}
