package com.duelodetrazos.ui.game

import android.content.Intent
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
import com.parse.ParseObject
import com.parse.ParseQuery
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

// GameActivity es la pantalla principal donde se desarrolla la partida.
class GameActivity : AppCompatActivity() {

    companion object {
        // Intervalo para las consultas de sondeo (polling) al servidor.
        private const val POLLING_INTERVAL_MS = 100L
        // Retraso para la aparicion de un nuevo objetivo despues de un acierto.
        private const val SPAWN_DELAY_MS = 500L
    }

    private lateinit var binding: ActivityGameBinding

    // Vistas personalizadas para el juego.
    private lateinit var drawingView: DrawingView
    private lateinit var objectiveView: ObjectiveCircleView
    private lateinit var explosionView: ParticleExplosionView

    // -- DATOS DE LA PARTIDA --
    private var roomId: String = ""
    private var isPlayer1 = false
    private var player1Name: String = "Jugador 1"
    private var player2Name: String = "Jugador 2"

    // -- ESTADO DEL JUEGO --
    private var currentObjectiveId: String? = null
    @Volatile private var isLocalHitSent = false
    private var player1Score = 0
    private var player2Score = 0
    @Volatile private var isGameRunning = true
    @Volatile private var isGamePaused = false

    // -- MANEJO DE EVENTOS Y SONDEO (POLLING) --
    private val eventPollingHandler = Handler(Looper.getMainLooper())
    private var spawnPollingRunnable: Runnable? = null
    private var hitRequestPollingRunnable: Runnable? = null
    private var scoreUpdatePollingRunnable: Runnable? = null
    private var player2JoinedPollingRunnable: Runnable? = null
    private var gameStatePollingRunnable: Runnable? = null // Para Pausa, Reanudar, Abandonar

    // IDs de los ultimos eventos vistos para evitar procesarlos multiples veces.
    private var lastSeenSpawnId: String? = null
    private var lastSeenHitRequestId: String? = null
    private var lastSeenScoreUpdateId: String? = null
    private var lastSeenGameStateId: String? = null

    // Almacena los IDs de objetivos que ya han sido puntuados para evitar dobles puntos.
    private val scoredObjectiveIds = mutableSetOf<String>()

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
    }

    // Extrae los datos necesarios (ID de sala, nombres, etc.) del Intent.
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

    // Inicializa las vistas personalizadas y las anade al contenedor principal.
    private fun setupViews() {
        drawingView = DrawingView(this)
        binding.containerCanvas.addView(drawingView)
        objectiveView = ObjectiveCircleView(this)
        binding.containerCanvas.addView(objectiveView)
        explosionView = ParticleExplosionView(this)
        binding.containerCanvas.addView(explosionView)
        objectiveView.onHit = { registerHitRequest() }
    }

    // Configura la informacion inicial en la interfaz de usuario.
    private fun setupGameInfo() {
        binding.tvRoomCode.text = roomId
        binding.tvPlayer1Name.text = player1Name
        binding.tvPlayer2Name.text = if (isPlayer1 && player2Name == "Jugador 2") "(Esperando...)" else player2Name
    }

    // Actualiza los marcadores en la pantalla.
    private fun updateScoreDisplay() {
        binding.tvPlayer1Score.text = player1Score.toString()
        binding.tvPlayer2Score.text = player2Score.toString()
    }

    // Espera a que el layout este listo para obtener las dimensiones y luego inicia el juego.
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

    // Inicia un sondeo para detectar cambios de estado globales (Pausa, Reanudar, Abandono).
    private fun startPollingForGameStateChanges() {
        gameStatePollingRunnable = Runnable {
            val query = ParseQuery.getQuery<ParseObject>("GameEvent").whereEqualTo("roomCode", roomId).whereNotEqualTo("type", "SPAWN").whereNotEqualTo("type", "HIT_REQUEST").whereNotEqualTo("type", "SCORE_UPDATE").orderByDescending("createdAt")
            query.getFirstInBackground { obj, e ->
                if (e == null && obj != null && obj.objectId != lastSeenGameStateId) {
                    lastSeenGameStateId = obj.objectId
                    when (obj.getString("type")) {
                        "PAUSE" -> handlePause(true)
                        "RESUME" -> handlePause(false)
                        "ABANDON" -> handleAbandon(obj.getJSONObject("payload")?.getString("abandoningPlayer"))
                    }
                }
                if (isGameRunning) gameStatePollingRunnable?.let { eventPollingHandler.postDelayed(it, POLLING_INTERVAL_MS) }
            }
        }
        gameStatePollingRunnable?.let { eventPollingHandler.post(it) }
    }

    // Gestiona la logica de UI para pausar o reanudar el juego.
    private fun handlePause(isPaused: Boolean) {
        this.isGamePaused = isPaused
        runOnUiThread {
            binding.pauseOverlay.visibility = if (isPaused) View.VISIBLE else View.GONE
            objectiveView.visibility = if (isPaused) View.INVISIBLE else View.VISIBLE
        }
    }

    // Gestiona el evento de abandono de un jugador.
    private fun handleAbandon(abandoningPlayer: String?) {
        if (!isGameRunning) return

        val winner = if (abandoningPlayer == "player1") player2Name else player1Name
        val loser = if (abandoningPlayer == "player1") player1Name else player2Name
        val winnerText = "$loser ha abandonado. $winner Gana!"

        endGame(winnerText, winner)
    }

    // El jugador 1 espera a que el jugador 2 se una para obtener su nombre y empezar la partida.
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
                } else if (isGameRunning) {
                    player2JoinedPollingRunnable?.let { eventPollingHandler.postDelayed(it, 2000) }
                }
            }
        }
        player2JoinedPollingRunnable?.let { eventPollingHandler.post(it) }
    }

    // El jugador 2 escucha la aparicion de nuevos objetivos creados por el jugador 1.
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

    // El jugador 1 (juez) escucha las peticiones de acierto de ambos jugadores.
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

    // Ambos jugadores escuchan las actualizaciones de puntuacion enviadas por el juez.
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

    // Finaliza el juego, muestra el resultado y guarda la partida.
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

        // Solo el juez (P1) guarda el resultado para evitar duplicados.
        if (isPlayer1) {
            saveMatchResult(winnerName)
        }
        stopAllPolling()
    }
    
    // Sobrecarga de endGame para manejar la victoria por puntuacion.
    private fun endGame() {
        if (player1Score >= 10 || player2Score >= 10) {
            val winner = if (player1Score >= 10) player1Name else player2Name
            endGame("$winner Gana!", winner)
        }
    }

    // Guarda el objeto MatchResult en el servidor de Back4App.
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

    // Crea un nuevo objetivo en una posicion aleatoria y lo notifica.
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

    // Envia una peticion de acierto al servidor cuando el jugador toca el objetivo.
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

    // Configura los listeners para todos los botones de la actividad.
    private fun setupButtons() {
        binding.btnPause.setOnClickListener {
            if(isGameRunning && !isGamePaused) {
                handlePause(true)
                LiveQueryManager.sendEvent(roomId, "PAUSE", JSONObject())
            }
        }
        binding.btnResume.setOnClickListener {
            if(isGameRunning && isGamePaused) {
                handlePause(false)
                LiveQueryManager.sendEvent(roomId, "RESUME", JSONObject())
            }
        }
        binding.btnAbandon.setOnClickListener {
            if(isGameRunning) {
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

    // Muestra un dialogo de confirmacion antes de abandonar la partida.
    private fun showAbandonConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Abandonar Partida")
            .setMessage("Estas seguro de que quieres abandonar? Tu oponente ganara la partida.")
            .setPositiveButton("Si, abandonar") { _, _ ->
                val data = JSONObject().apply {
                    put("abandoningPlayer", if (isPlayer1) "player1" else "player2")
                }
                LiveQueryManager.sendEvent(roomId, "ABANDON", data)
            }
            .setNegativeButton("No", null)
            .show()
    }

    // Detiene todos los bucles de sondeo activos.
    private fun stopAllPolling() {
        spawnPollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        hitRequestPollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        scoreUpdatePollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        player2JoinedPollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
        gameStatePollingRunnable?.let { eventPollingHandler.removeCallbacks(it) }
    }

    // Limpieza final al destruir la actividad.
    override fun onDestroy() {
        super.onDestroy()
        if (isGameRunning) {
            val data = JSONObject().apply {
                put("abandoningPlayer", if (isPlayer1) "player1" else "player2")
            }
            LiveQueryManager.sendEvent(roomId, "ABANDON", data)
        }
        isGameRunning = false
        stopAllPolling()
    }
}
