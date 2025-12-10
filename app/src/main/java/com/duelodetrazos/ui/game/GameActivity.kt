package com.duelodetrazos.ui.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.duelodetrazos.databinding.ActivityGameBinding
import com.duelodetrazos.network.LiveQueryManager
import com.duelodetrazos.ui.canvas.DrawingView
import com.duelodetrazos.ui.room.RoomActivity
import com.google.android.material.snackbar.Snackbar
import com.parse.ParseException
import com.parse.ParseObject
import com.parse.ParseQuery
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    companion object {
        private const val POLLING_INTERVAL_MS = 100L
        private const val SPAWN_DELAY_MS = 500L
        private const val CONNECTION_TIMEOUT_MS = 15000L // 15 segundos
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var drawingView: DrawingView
    private lateinit var objectiveView: ObjectiveCircleView
    private lateinit var explosionView: ParticleExplosionView

    private var roomId: String = ""
    private var isPlayer1 = false
    private var player1Name: String = "Jugador 1"
    private var player2Name: String = "Jugador 2"

    private var currentObjectiveId: String? = null
    @Volatile private var isLocalHitSent = false
    private var player1Score = 0
    private var player2Score = 0
    @Volatile private var isGameRunning = true
    @Volatile private var isGamePaused = false
    @Volatile private var wasPausedByNetwork = false // Flag para gestionar pausas por red

    private val eventPollingHandler = Handler(Looper.getMainLooper())
    private var spawnPollingRunnable: Runnable? = null
    private var hitRequestPollingRunnable: Runnable? = null
    private var scoreUpdatePollingRunnable: Runnable? = null
    private var player2JoinedPollingRunnable: Runnable? = null
    private var gameStatePollingRunnable: Runnable? = null

    private var lastSeenSpawnId: String? = null
    private var lastSeenHitRequestId: String? = null
    private var lastSeenScoreUpdateId: String? = null
    private var lastSeenGameStateId: String? = null

    private val scoredObjectiveIds = mutableSetOf<String>()
    private lateinit var connectivityReceiver: ConnectivityReceiver
    private val disconnectHandler = Handler(Looper.getMainLooper())
    private var disconnectRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!getIntentData()) {
            finish()
            return
        }

        setupViews()
        setupGameInfo()
        updateScoreDisplay()
        startGameWhenReady()
        setupButtons()

        connectivityReceiver = ConnectivityReceiver()
        registerReceiver(connectivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    private fun getIntentData(): Boolean {
        roomId = intent.getStringExtra("roomId") ?: ""
        isPlayer1 = intent.getBooleanExtra("isPlayer1", false)
        player1Name = intent.getStringExtra("player1Name") ?: "Jugador 1"
        player2Name = intent.getStringExtra("player2Name") ?: "Jugador 2"
        if (roomId.isEmpty()) {
            Toast.makeText(this, "Error: ID de sala no recibido.", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun setupViews() {
        drawingView = DrawingView(this)
        binding.containerCanvas.addView(drawingView)
        objectiveView = ObjectiveCircleView(this)
        binding.containerCanvas.addView(objectiveView)
        explosionView = ParticleExplosionView(this)
        binding.containerCanvas.addView(explosionView)
        objectiveView.onHit = { registerHitRequest() }
    }

    private fun setupGameInfo() {
        binding.tvRoomCode.text = roomId
        binding.tvPlayer1Name.text = player1Name
        binding.tvPlayer2Name.text = if (isPlayer1 && player2Name == "Jugador 2") "(Esperando...)" else player2Name
    }

    private fun updateScoreDisplay() {
        binding.tvPlayer1Score.text = player1Score.toString()
        binding.tvPlayer2Score.text = player2Score.toString()
    }

    private fun startGameWhenReady() {
        binding.containerCanvas.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.containerCanvas.viewTreeObserver.removeOnGlobalLayoutListener(this)
                drawingView.onTouchPoint = { x, y -> if (!isGamePaused) objectiveView.checkHit(x, y) }

                startPollingForScoreUpdates()
                startPollingForGameStateChanges()

                if (isPlayer1) {
                    pollForPlayer2Join()
                } else {
                    startPollingForSpawns()
                }
            }
        })
    }

    private fun startPollingForGameStateChanges() {
        gameStatePollingRunnable = Runnable {
            val query = ParseQuery.getQuery<ParseObject>("GameEvent").whereEqualTo("roomCode", roomId).whereNotEqualTo("type", "SPAWN").whereNotEqualTo("type", "HIT_REQUEST").whereNotEqualTo("type", "SCORE_UPDATE").orderByDescending("createdAt")
            query.getFirstInBackground { obj, e ->
                if (e == null && obj != null && obj.objectId != lastSeenGameStateId) {
                    lastSeenGameStateId = obj.objectId
                    when (obj.getString("type")) {
                        "PAUSE" -> handlePause(true, isNetworkInduced = false)
                        "RESUME" -> handlePause(false, isNetworkInduced = false)
                        "ABANDON" -> handleAbandon(obj.getJSONObject("payload")?.getString("abandoningPlayer"))
                    }
                } 
                if (isGameRunning) gameStatePollingRunnable?.let { eventPollingHandler.postDelayed(it, POLLING_INTERVAL_MS) }
            }
        }
        gameStatePollingRunnable?.let { eventPollingHandler.post(it) }
    }

    private fun handlePause(isPaused: Boolean, isNetworkInduced: Boolean) {
        this.isGamePaused = isPaused
        if(isNetworkInduced) {
            wasPausedByNetwork = isPaused
        }

        runOnUiThread {
            if (isPaused) {
                if(isNetworkInduced){
                    binding.tvConnectionStatus.visibility = View.VISIBLE
                    binding.pauseOverlay.visibility = View.GONE
                } else {
                    binding.tvConnectionStatus.visibility = View.GONE
                    binding.pauseOverlay.visibility = View.VISIBLE
                }
            } else {
                binding.tvConnectionStatus.visibility = View.GONE
                binding.pauseOverlay.visibility = View.GONE
            }
            objectiveView.visibility = if (isPaused) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun handleAbandon(abandoningPlayer: String?) {
        if (!isGameRunning) return

        val (winnerText, winnerName) = if (abandoningPlayer == "player1") {
            Pair("$player1Name ha abandonado. $player2Name Gana!", player2Name)
        } else {
            Pair("$player2Name ha abandonado. $player1Name Gana!", player1Name)
        }

        endGame(winnerText, winnerName)
    }

    private fun pollForPlayer2Join() {
        player2JoinedPollingRunnable = Runnable {
            val query = ParseQuery.getQuery<ParseObject>("Room").whereEqualTo("code", roomId)
            query.getFirstInBackground { room, e ->
                if (e == null && room != null && room.getString("status") == "ready") {
                    player2Name = room.getString("player2Name") ?: "Jugador 2"
                    runOnUiThread {
                        binding.tvPlayer2Name.text = player2Name
                        Toast.makeText(this, "$player2Name se ha unido. Comienza el juego!", Toast.LENGTH_SHORT).show()
                    }
                    startPollingForHitRequests()
                    spawnNewObjective()
                } else if (e == null && isGameRunning) {
                    player2JoinedPollingRunnable?.let { eventPollingHandler.postDelayed(it, 2000) }
                }
            }
        }
        player2JoinedPollingRunnable?.let { eventPollingHandler.post(it) }
    }

    private fun startPollingForSpawns() {
        spawnPollingRunnable = Runnable {
            if (!isGameRunning || isGamePaused) {
                if (isGameRunning) spawnPollingRunnable?.let { eventPollingHandler.postDelayed(it, POLLING_INTERVAL_MS) }
                return@Runnable
            }
            val query = ParseQuery.getQuery<ParseObject>("GameEvent").whereEqualTo("roomCode", roomId).whereEqualTo("type", "SPAWN").orderByDescending("createdAt")
            query.getFirstInBackground { obj, e ->
                if (e == null && obj != null && obj.objectId != lastSeenSpawnId) {
                    lastSeenSpawnId = obj.objectId
                    val payload = obj.getJSONObject("payload")!!
                    currentObjectiveId = payload.getString("objectiveId")
                    objectiveView.setPosition(payload.getDouble("x").toFloat(), payload.getDouble("y").toFloat())
                    isLocalHitSent = false
                }
                if (isGameRunning) spawnPollingRunnable?.let { eventPollingHandler.postDelayed(it, POLLING_INTERVAL_MS) }
            }
        }
        spawnPollingRunnable?.let { eventPollingHandler.post(it) }
    }

    private fun startPollingForHitRequests() {
        hitRequestPollingRunnable = Runnable {
            if (!isGameRunning || isGamePaused) {
                if (isGameRunning) hitRequestPollingRunnable?.let { eventPollingHandler.postDelayed(it, POLLING_INTERVAL_MS) }
                return@Runnable
            }
            val query = ParseQuery.getQuery<ParseObject>("GameEvent").whereEqualTo("roomCode", roomId).whereEqualTo("type", "HIT_REQUEST").orderByDescending("createdAt")
            query.getFirstInBackground { obj, e ->
                if (e == null && obj != null && obj.objectId != lastSeenHitRequestId) {
                    lastSeenHitRequestId = obj.objectId
                    val payload = obj.getJSONObject("payload")!!
                    val objectiveId = payload.getString("objectiveId")
                    if (!scoredObjectiveIds.contains(objectiveId)) {
                        scoredObjectiveIds.add(objectiveId)
                        val scoringPlayer = payload.getString("playerId")
                        if (scoringPlayer == "player1") player1Score++ else player2Score++
                        val confirmationData = JSONObject().apply {
                            put("p1Score", player1Score)
                            put("p2Score", player2Score)
                        }
                        LiveQueryManager.sendEvent(roomId, "SCORE_UPDATE", confirmationData)
                    }
                }
                if (isGameRunning) hitRequestPollingRunnable?.let { eventPollingHandler.postDelayed(it, POLLING_INTERVAL_MS) }
            }
        }
        hitRequestPollingRunnable?.let { eventPollingHandler.post(it) }
    }

    private fun startPollingForScoreUpdates() {
        scoreUpdatePollingRunnable = Runnable {
            if (!isGameRunning) return@Runnable
            val query = ParseQuery.getQuery<ParseObject>("GameEvent").whereEqualTo("roomCode", roomId).whereEqualTo("type", "SCORE_UPDATE").orderByDescending("createdAt")
            query.getFirstInBackground { obj, e ->
                if (e == null && obj != null && obj.objectId != lastSeenScoreUpdateId) {
                    lastSeenScoreUpdateId = obj.objectId
                    val payload = obj.getJSONObject("payload")!!
                    if (isGameRunning) {
                        player1Score = payload.getInt("p1Score")
                        player2Score = payload.getInt("p2Score")
                        updateScoreDisplay()
                        if (isGameRunning && ((player1Score == 9 && player2Score < 9) || (player2Score == 9 && player1Score < 9))) {
                            val matchPointPlayer = if (player1Score == 9) player1Name else player2Name
                            Toast.makeText(this, "Punto de partido para $matchPointPlayer!", Toast.LENGTH_LONG).show()
                        }
                        val hitX = objectiveView.getCircleX()
                        val hitY = objectiveView.getCircleY()
                        objectiveView.setPosition(-200f, -200f)
                        explosionView.startExplosion(hitX, hitY, objectiveView.getCircleColor())
                        if (player1Score >= 10 || player2Score >= 10) {
                            endGame()
                        } else {
                            if (isPlayer1 && !isGamePaused) {
                                eventPollingHandler.postDelayed({ spawnNewObjective() }, SPAWN_DELAY_MS)
                            }
                        }
                    }
                }
                if (isGameRunning) scoreUpdatePollingRunnable?.let { eventPollingHandler.postDelayed(it, POLLING_INTERVAL_MS) }
            }
        }
        scoreUpdatePollingRunnable?.let { eventPollingHandler.post(it) }
    }

    private fun endGame(winnerText: String, winnerName: String) {
        if (!isGameRunning) return
        isGameRunning = false
        runOnUiThread {
            binding.tvGameOver.text = winnerText
            binding.tvGameOver.visibility = View.VISIBLE
            binding.btnExitToMenu.visibility = View.VISIBLE
            binding.layoutButtons.visibility = View.GONE
            drawingView.onTouchPoint = { _, _ -> }
            objectiveView.visibility = View.GONE
        }
        if (isPlayer1) {
            saveMatchResult(winnerName)
        }
        stopAllPolling()
    }

    private fun endGame() {
        if (player1Score >= 10 || player2Score >= 10) {
            val winner = if (player1Score >= 10) player1Name else player2Name
            endGame("$winner Gana!", winner)
        }
    }

    private fun saveMatchResult(winnerName: String) {
        val matchResult = ParseObject("MatchResult").apply {
            put("player1Name", player1Name)
            put("player2Name", player2Name)
            put("player1Score", player1Score)
            put("player2Score", player2Score)
            put("winnerName", winnerName)
        }
        matchResult.saveInBackground()
    }

    private fun spawnNewObjective() {
        if (!isGameRunning || isGamePaused) return
        val width = binding.containerCanvas.width
        val height = binding.containerCanvas.height
        if (width == 0 || height == 0) return
        val newObjectiveId = UUID.randomUUID().toString()
        currentObjectiveId = newObjectiveId
        isLocalHitSent = false
        val x = Random.nextInt((width * 0.2f).toInt(), (width * 0.8f).toInt()).toFloat()
        val y = Random.nextInt((height * 0.2f).toInt(), (height * 0.8f).toInt()).toFloat()
        objectiveView.setPosition(x, y)
        val data = JSONObject().apply {
            put("x", x)
            put("y", y)
            put("objectiveId", newObjectiveId)
        }
        LiveQueryManager.sendEvent(roomId, "SPAWN", data)
    }

    private fun registerHitRequest() {
        if (isLocalHitSent || currentObjectiveId == null || !isGameRunning || isGamePaused) return
        isLocalHitSent = true
        val playerId = if (isPlayer1) "player1" else "player2"
        val data = JSONObject().apply {
            put("playerId", playerId)
            put("objectiveId", currentObjectiveId)
        }
        LiveQueryManager.sendEvent(roomId, "HIT_REQUEST", data)
    }

    private fun setupButtons() {
        binding.btnPause.setOnClickListener {
            if (isGameRunning && !isGamePaused) {
                handlePause(true, isNetworkInduced = false)
                LiveQueryManager.sendEvent(roomId, "PAUSE", JSONObject())
            }
        }
        binding.btnResume.setOnClickListener {
            if (isGameRunning && isGamePaused && !wasPausedByNetwork) {
                handlePause(false, isNetworkInduced = false)
                LiveQueryManager.sendEvent(roomId, "RESUME", JSONObject())
            }
        }
        binding.btnAbandon.setOnClickListener {
            if (isGameRunning) {
                showAbandonConfirmationDialog()
            }
        }
        binding.btnExitToMenu.setOnClickListener {
            val intent = Intent(this, RoomActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showAbandonConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Abandonar Partida")
            .setMessage("Estas seguro de que quieres abandonar? Tu oponente ganara la partida.")
            .setPositiveButton("Si, abandonar") { _, _ ->
                forceAbandon()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun forceAbandon() {
        if (!isGameRunning) return
        val data = JSONObject().apply {
            put("abandoningPlayer", if (isPlayer1) "player1" else "player2")
        }
        LiveQueryManager.sendEvent(roomId, "ABANDON", data)
        handleAbandon(if (isPlayer1) "player1" else "player2")
    }

    private fun stopAllPolling() {
        spawnPollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        hitRequestPollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        scoreUpdatePollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        player2JoinedPollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        gameStatePollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        disconnectHandler.removeCallbacksAndMessages(null)
    }

    inner class ConnectivityReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isNetworkConnected()) {
                if (isGameRunning && !isGamePaused) {
                    handlePause(true, isNetworkInduced = true)
                    disconnectRunnable = Runnable { forceAbandonAfterDisconnect() }
                    disconnectHandler.postDelayed(disconnectRunnable!!, CONNECTION_TIMEOUT_MS)
                }
            } else {
                disconnectHandler.removeCallbacks(disconnectRunnable ?: return@onReceive)
                if (wasPausedByNetwork) {
                    handlePause(false, isNetworkInduced = true)
                }
            }
        }
    }

    private fun forceAbandonAfterDisconnect() {
        if (!isNetworkConnected()) {
            runOnUiThread { Toast.makeText(this, "Has sido desconectado por inactividad.", Toast.LENGTH_LONG).show() }
            forceAbandon()
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo?.isConnectedOrConnecting == true
    }

    override fun onStop() {
        super.onStop()
        if (isGameRunning && !isGamePaused) {
            handlePause(true, isNetworkInduced = false)
            LiveQueryManager.sendEvent(roomId, "PAUSE", JSONObject())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectivityReceiver)
        if (isGameRunning) {
            forceAbandon()
        }
        isGameRunning = false
        stopAllPolling()
    }
}
