package com.programminghut.realtime_object.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.os.*
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

    private val obstacleLabels = listOf("person", "car", "truck", "motorcycle", "bicycle")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Initialisation OSMDroid
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = ctx.packageName

        setContentView(R.layout.activity_navigation_with_detection)

        // ✅ Initialisation UI views
        map = findViewById(R.id.mapView)
        textureView = findViewById(R.id.textureView)
        imageView = findViewById(R.id.imageView)

        // ✅ Langue & synthèse vocale
        val selectedLang = intent.getStringExtra("lang") ?: "fr"
        ttsHelper = TTSHelper(this)
        if (selectedLang == "darija") {
            ttsHelper.setLanguage("ar", "MA")
        } else {
            ttsHelper.setLanguage("fr", "FR")
        }
        announcer = ObstacleAnnouncer(this, ttsHelper, selectedLang)

        // ✅ Chargement modèle IA
        labels = FileUtil.loadLabels(this, "labels.txt")
        model = SsdMobilenetV11Metadata1.newInstance(this)
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        paint = Paint().apply {
            color = Color.RED
            strokeWidth = 6f
            style = Paint.Style.STROKE
            textSize = 50f
        }

        setupMap()
        setupCamera()
        setupButtons()

        Thread { navigationLoop() }.start()
    }

    private fun setupMap() {
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)

        locationOverlay.runOnFirstFix {
            runOnUiThread {
                val user = GeoPoint(locationOverlay.myLocation.latitude, locationOverlay.myLocation.longitude)
                map.controller.animateTo(user)
            }
        }

        map.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (locationOverlay.myLocation == null) {
                    Toast.makeText(this, "Position GPS indisponible. Veuillez patienter...", Toast.LENGTH_SHORT).show()
                } else {
                    val point = map.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                    handleDestinationSelected(point)
                }
            }
            false
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

        val start = locationOverlay.myLocation
        if (start == null) {
            Toast.makeText(this, "Position actuelle inconnue.", Toast.LENGTH_SHORT).show()
            return
        }

        val startPoint = GeoPoint(start.latitude, start.longitude)
        fetchRoute(startPoint, destination)
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

                val geometry = JSONObject(json)
                    .getJSONArray("routes").getJSONObject(0)
                    .getJSONObject("geometry").getJSONArray("coordinates")

                val route = mutableListOf<GeoPoint>()
                for (i in 0 until geometry.length()) {
                    val coord = geometry.getJSONArray(i)
                    route.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                }

                currentRoute = route
                runOnUiThread { drawRoute(route) }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun drawRoute(points: List<GeoPoint>) {
        currentPolyline?.let { map.overlays.remove(it) }

        val polyline = Polyline().apply {
            setPoints(points)
            color = Color.BLUE
            width = 10f
        }
        currentPolyline = polyline
        map.overlays.add(polyline)
        map.invalidate()
    }

    private fun navigationLoop() {
        while (true) {
            val loc = locationOverlay.myLocation
            if (loc != null && currentRoute.isNotEmpty()) {
                val user = GeoPoint(loc.latitude, loc.longitude)

                var closestIndex = 0
                var minDist = Double.MAX_VALUE
                for (i in currentRoute.indices) {
                    val dist = user.distanceToAsDouble(currentRoute[i])
                    if (dist < minDist) {
                        minDist = dist
                        closestIndex = i
                    }
                }

                if (closestIndex > lastInstructionIndex) {
                    val message = when {
                        closestIndex == currentRoute.size - 1 -> "Vous êtes arrivé"
                        closestIndex < currentRoute.size - 1 -> "Continuez 50 mètres"
                        else -> null
                    }

                    message?.let {
                        ttsHelper.speak(it)
                        lastInstructionIndex = closestIndex
                    }
                }
            }
            Thread.sleep(4000)
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnCenter).setOnClickListener {
            val loc = locationOverlay.myLocation
            if (loc != null) map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
        }

        findViewById<Button>(R.id.btnRecalculate).setOnClickListener {
            val dest = destinationMarker?.position
            val loc = locationOverlay.myLocation
            if (loc != null && dest != null) {
                fetchRoute(GeoPoint(loc.latitude, loc.longitude), dest)
            }
        }
    }

    private fun setupCamera() {
        val handlerThread = HandlerThread("VideoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                detectObstacles()
            }
        }
    }

    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            return
        }

        val cameraId = cameraManager.cameraIdList[0]
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
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

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {}
            override fun onError(camera: CameraDevice, error: Int) {}
        }, handler)
    }

    private fun detectObstacles() {
        val bitmap = textureView.bitmap ?: return
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val tensor = imageProcessor.process(TensorImage.fromBitmap(bitmap))
        val outputs = model.process(tensor)

        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray

        for (i in scores.indices) {
            if (scores[i] > 0.5f) {
                val labelIndex = classes[i].toInt()
                val label = labels.getOrElse(labelIndex) { "?" }

                if (obstacleLabels.contains(label)) {
                    val x = i * 4
                    val top = locations[x] * bitmap.height
                    val left = locations[x + 1] * bitmap.width
                    val bottom = locations[x + 2] * bitmap.height
                    val right = locations[x + 3] * bitmap.width

                    canvas.drawRect(left, top, right, bottom, paint)
                    announcer.announce(label)
                }
            }
        }

        imageView.setImageBitmap(mutable)
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper.shutdown()
        model.close()
    }
}
