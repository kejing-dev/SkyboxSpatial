package com.kejing.skyboxspatial.ui.stage

import android.graphics.Bitmap
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.SphereOption
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/**
 * A large latitude-longitude sphere around the viewer with the equirectangular panorama as an unlit
 * texture. The sphere is mirrored on X so the image reads correctly from the inside, and the
 * material is double-sided so the mirrored winding never culls it. Only the heading changes; there
 * is no positional parallax, which is what a static panorama supports.
 */
class SkySphereScene {
    val root: Entity = Entity()
    private var sphere: ModelEntity? = null
    private var shown: Bitmap? = null

    /** Replaces the panorama. Calling it again with the same bitmap is a no-op. */
    fun show(bitmap: Bitmap) {
        if (shown === bitmap) return
        sphere?.destroy(true)
        sphere = null
        val texture = TextureResource.create(bitmap)
        val material = UnlitMaterial.create().apply {
            setBaseColorTexture(texture)
            setCullingMode(MaterialCullingMode.NONE)
        }
        val mesh = MeshResource.createSphere(SphereOption(RADIUS_METERS))
        val entity = ModelEntity(mesh, material)
        entity.components[TransformComponent::class.java]?.setScaleVector(Vector3(-1f, 1f, 1f))
        root.addChild(entity)
        sphere = entity
        shown = bitmap
    }

    /** Rotates the whole panorama about the vertical axis, in degrees; 0 puts the image centre straight ahead. */
    fun setYaw(degrees: Float) {
        root.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(pitch = 0f, yaw = degrees + BASE_YAW_DEGREES, roll = 0f))
    }

    fun destroy() {
        root.destroy(true)
        sphere = null
        shown = null
    }

    private companion object {
        /** Far enough that the seams never come close, near enough to stay inside the far clip. */
        const val RADIUS_METERS = 30f

        /** The mirrored lat-long sphere shows u = 0.25 straight ahead; this turns u = 0.5 (the image centre) forward. */
        const val BASE_YAW_DEGREES = 90f
    }
}
