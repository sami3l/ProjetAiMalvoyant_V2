package com.programminghut.realtime_object.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.programminghut.realtime_object.R
import com.programminghut.realtime_object.helpers.ObstacleAnnouncer
import com.programminghut.realtime_object.helpers.TTSHelper
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.net.HttpURLConnection
import java.net.URL

class NavigationWithDetectionActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var textureView: TextureView
    private lateinit var imageView: ImageView
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handler: Handler
    private lateinit var model: SsdMobilenetV11Metadata1
    private lateinit var paint: Paint
    private lateinit var labels: List<String>
    private lateinit var imageProcessor: ImageProcessor

    private lateinit var ttsHelper: TTSHelper
    private lateinit var announcer: ObstacleAnnouncer
    private lateinit var locationOverlay: MyLocationNewOverlay

    private var currentPolyline: Polyline? = null
    private var currentRoute: List<GeoPoint> = emptyList()
    private var lastInstructionIndex = -1
    private var destinationMarker: Marker? = null

    // Configuration de détection
    private val REAL_OBSTACLE_SIZES = mapOf(
        "person" to Size(0.5f, 1.7f),    // width x height en mètres
        "car" to Size(2.0f, 1.5f),
        "truck" to Size(2.5f, 2.5f),
        "motorcycle" to Size(0.8f, 1.1f),
        "bicycle" to Size(0.6f, 1.0f),
        "bus" to Size(2.5f, 3.0f),
        "train" to Size(3.0f, 3.5f),
        "bench" to Size(1.5f, 0.5f),
        "traffic light" to Size(0.3f, 0.8f),
        "stop sign" to Size(0.6f, 0.6f),
        "fire hydrant" to Size(0.3f, 0.8f),
        "parking meter" to Size(0.3f, 1.0f)
    )
    private val FOCAL_LENGTH = 1200f  // À calibrer pour votre appareil
    private val MAX_DETECTION_DISTANCE = 10.0f  // Détection jusqu'à 10 mètres

    data class Size(val width: Float, val height: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialisation OSMDroid
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = ctx.packageName

        setContentView(R.layout.activity_navigation_with_detection)

        // Gestion des permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            ), 101)
        }

        // Initialisation UI
        map = findViewById(R.id.mapView)
        textureView = findViewById(R.id.textureView)
        imageView = findViewById(R.id.imageView)

        // Configuration TTS
        val selectedLang = intent.getStringExtra("lang") ?: "fr"
        ttsHelper = TTSHelper(this)
        ttsHelper.setLanguage(if (selectedLang == "darija") "ar" else "fr", if (selectedLang == "darija") "MA" else "FR")
        announcer = ObstacleAnnouncer(this, ttsHelper, selectedLang)

        // Chargement du modèle TensorFlow Lite
        labels = FileUtil.loadLabels(this, "labels.txt")
        model = SsdMobilenetV11Metadata1.newInstance(this)
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()

        paint = Paint().apply {
            color = Color.RED
            strokeWidth = 6f
            style = Paint.Style.STROKE
            textSize = 50f
        }

        setupMap()
        setupCamera()
        setupButtons()

        // Lancement de la boucle de navigation
        Thread { navigationLoop() }.start()
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)

        // Fallback si GPS non disponible
        val fallback = getLastKnownLocation()
        fallback?.let {
            map.controller.animateTo(it)
            Log.d("GPS", "Fallback to $it")
        }

        locationOverlay.runOnFirstFix {
            runOnUiThread {
                locationOverlay.myLocation?.let {
                    map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    Log.i("GPS", "GPS fix: $it")
                } ?: Log.e("GPS", "Location is null")
            }
        }

        map.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                locationOverlay.myLocation?.let { userLoc ->
                    val point = map.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                    handleDestinationSelected(point)
                } ?: Toast.makeText(this, "Position GPS indisponible", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private fun getLastKnownLocation(): GeoPoint? {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.getProviders(true).firstNotNullOfOrNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)?.let {
                    GeoPoint(it.latitude, it.longitude)
                }
            } catch (e: SecurityException) {
                null
            }
        }
    }

    private fun handleDestinationSelected(destination: GeoPoint) {
        destinationMarker?.let { map.overlays.remove(it) }
        destinationMarker = Marker(map).apply {
            position = destination
            title = "Destination"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(destinationMarker)

        val start = locationOverlay.myLocation ?: getLastKnownLocation()
        if (start != null) {
            fetchRoute(GeoPoint(start.latitude, start.longitude), destination)
        } else {
            Toast.makeText(this, "Position actuelle inconnue", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchRoute(start: GeoPoint, end: GeoPoint) {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
                "${start.longitude},${start.latitude};${end.longitude},${end.latitude}" +
                "?overview=full&geometries=geojson"

        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connect()
                val json = connection.inputStream.bufferedReader().use { it.readText() }

                val route = JSONObject(json)
                    .getJSONArray("routes").getJSONObject(0)
                    .getJSONObject("geometry").getJSONArray("coordinates")
                    .let { coords ->
                        List(coords.length()) { i ->
                            val coord = coords.getJSONArray(i)
                            GeoPoint(coord.getDouble(1), coord.getDouble(0))
                        }
                    }

                runOnUiThread {
                    currentRoute = route
                    drawRoute(route)
                }
            } catch (e: Exception) {
                Log.e("ROUTE", "Error fetching route", e)
            }
        }.start()
    }

    private fun drawRoute(points: List<GeoPoint>) {
        currentPolyline?.let { map.overlays.remove(it) }
        currentPolyline = Polyline().apply {
            setPoints(points)
            color = Color.BLUE
            width = 10f
        }.also {
            map.overlays.add(it)
            map.invalidate()
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCenter).setOnClickListener {
            locationOverlay.myLocation?.let {
                map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            }
        }
        findViewById<Button>(R.id.btnRecalculate).setOnClickListener {
            val dest = destinationMarker?.position
            val loc = locationOverlay.myLocation ?: getLastKnownLocation()
            if (loc != null && dest != null) {
                fetchRoute(GeoPoint(loc.latitude, loc.longitude), dest)
            }
        }
    }

    private fun setupCamera() {
        val handlerThread = HandlerThread("VideoThread").apply { start() }
        handler = Handler(handlerThread.looper)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = false
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = detectObstacles()
        }
    }

    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            return
        }

        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                val surface = Surface(textureView.surfaceTexture)
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                }
                device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(request.build(), null, handler)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("CAMERA", "Configuration failed")
                    }
                }, handler)
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                Log.e("CAMERA", "Error code: $error")
            }
        }, handler)
    }

    private fun detectObstacles() {
        val bitmap = textureView.bitmap ?: return
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 45f
            style = Paint.Style.FILL
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
        }

        val outputs = model.process(imageProcessor.process(TensorImage.fromBitmap(bitmap)))
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray

        // Traiter chaque détection
        for (i in scores.indices) {
            if (scores[i] > 0.5f) {
                val labelIndex = classes[i].toInt()
                val label = labels.getOrElse(labelIndex) { "unknown" }

                // Si cette étiquette est définie dans notre dictionnaire de tailles réelles
                REAL_OBSTACLE_SIZES[label]?.let { realSize ->
                    val x = i * 4
                    val left = locations[x + 1] * bitmap.width
                    val top = locations[x] * bitmap.height
                    val right = locations[x + 3] * bitmap.width
                    val bottom = locations[x + 2] * bitmap.height

                    // Calcul de la largeur de l'objet en pixels
                    val widthPx = right - left
                    val heightPx = bottom - top

                    // Calcul de la distance en utilisant la largeur ET la hauteur, puis moyenne
                    val distanceByWidth = if (widthPx > 0) (realSize.width * FOCAL_LENGTH) / widthPx else Float.MAX_VALUE
                    val distanceByHeight = if (heightPx > 0) (realSize.height * FOCAL_LENGTH) / heightPx else Float.MAX_VALUE

                    // Utiliser la méthode qui donne la distance la plus fiable (généralement la plus petite)
                    var distance = kotlin.math.min(distanceByWidth, distanceByHeight)

                    // Limiter à la distance maximale de détection
                    if (distance <= MAX_DETECTION_DISTANCE) {
                        // Dessiner le rectangle de détection
                        canvas.drawRect(left, top, right, bottom, paint)

                        // Afficher la distance sur l'écran
                        val distanceFormatted = "%.1f m".format(distance)
                        canvas.drawText("$label: $distanceFormatted", left, top - 10, textPaint)

                        // Annoncer l'obstacle via l'ObstacleAnnouncer
                        announcer.announce(label, distance)

                        Log.d("DETECTION", "$label détecté à $distanceFormatted")
                    }
                }
            }
        }

        // Mettre à jour l'imageView avec les détections
        imageView.setImageBitmap(mutable)
    }

    private fun navigationLoop() {
        while (true) {
            val loc = locationOverlay.myLocation
            if (loc != null && currentRoute.isNotEmpty()) {
                val user = GeoPoint(loc.latitude, loc.longitude)
                val (closestIndex, _) = currentRoute.withIndex().minByOrNull {
                    user.distanceToAsDouble(it.value)
                } ?: continue

                if (closestIndex > lastInstructionIndex) {
                    val message = when {
                        closestIndex == currentRoute.size - 1 -> "Vous êtes arrivé"
                        else -> "Continuez 50 mètres"
                    }
                    ttsHelper.speak(message)
                    lastInstructionIndex = closestIndex
                }
            }
            Thread.sleep(4000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.looper.quitSafely()
        ttsHelper.shutdown()
        model.close()
        cameraDevice.close()
    }
}