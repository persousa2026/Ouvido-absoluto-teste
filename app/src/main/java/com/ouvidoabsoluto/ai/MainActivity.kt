package com.ouvidoabsoluto.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ouvidoabsoluto.ai.audio.KeyPlayback
import com.ouvidoabsoluto.ai.audio.MicrophoneRecorder
import com.ouvidoabsoluto.ai.audio.VocalPitchProcessor
import com.ouvidoabsoluto.ai.data.TonalityRepository
import com.ouvidoabsoluto.ai.music.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OuvidoAbsolutoApplication
        val vm = ViewModelProvider(this, TonalityViewModel.factory(app.repository))[TonalityViewModel::class.java]
        setContent { MaterialTheme { OuvidoAbsolutoApp(vm) } }
    }
}

data class TonalityUiState(
    val listening: Boolean = false,
    val currentPitch: DetectedPitch? = null,
    val bestKey: MusicalKey? = null,
    val alternatives: List<KeyDetector.Candidate> = emptyList(),
    val relativeCandidate: KeyDetector.Candidate? = null,
    val relativeAmbiguous: Boolean = false,
    val confidence: Double = 0.0,
    val stable: Boolean = false,
    val voiceTimeMs: Long = 0,
    val noteEvents: Int = 0,
    val phrases: Int = 0,
    val uniqueNotes: Int = 0,
    val sampleQuality: SampleQuality = SampleQuality.INSUFFICIENT,
    val message: String = "Pronto para analisar"
)

class TonalityViewModel(private val repository: TonalityRepository) : ViewModel() {
    private val recorder = MicrophoneRecorder()
    private val vocalProcessor = VocalPitchProcessor(recorder.sampleRate)
    private val tonalityEngine = TonalityEngine()
    private val playback = KeyPlayback()
    private val _state = MutableStateFlow(TonalityUiState())
    val state = _state.asStateFlow()
    val history = repository.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun start() {
        if (_state.value.listening) return
        vocalProcessor.reset()
        tonalityEngine.reset()
        _state.value = TonalityUiState(listening = true, message = "Ouvindo...")

        recorder.start { frame ->
            val now = System.currentTimeMillis()
            val frameMs = (frame.size.toDouble() / recorder.sampleRate * 1000.0).toLong()
            val pitch = vocalProcessor.process(frame)

            if (pitch == null) {
                updateEstimate(tonalityEngine.onSilence(now), null, 0L)
                return@start
            }

            updateEstimate(tonalityEngine.addPitch(pitch, now), pitch, frameMs)
        }
    }

    private fun updateEstimate(estimate: KeyEstimate, pitch: DetectedPitch?, voiceIncrementMs: Long) {
        _state.update { current ->
            val nextVoice = current.voiceTimeMs + voiceIncrementMs
            val evidenceCap = when {
                nextVoice < 2500 -> 0.40
                nextVoice < 6000 -> 0.65
                nextVoice < 12000 -> 0.85
                else -> 0.98
            }
            val confidence = estimate.confidence.coerceAtMost(evidenceCap)
            val message = when {
                nextVoice < 2500 -> "Captando notas..."
                estimate.best == null -> "Analisando tonalidade..."
                estimate.sampleQuality == SampleQuality.LOW_VARIETY -> "Cante uma frase com mais notas diferentes"
                estimate.relativeAmbiguous -> "Maior/menor relativo ainda ambíguo"
                estimate.stable -> "Tonalidade estabilizada"
                else -> "Refinando tonalidade..."
            }
            current.copy(
                currentPitch = pitch ?: current.currentPitch,
                bestKey = estimate.best?.key ?: current.bestKey,
                alternatives = estimate.alternatives,
                relativeCandidate = estimate.relativeCandidate,
                relativeAmbiguous = estimate.relativeAmbiguous,
                confidence = confidence,
                stable = estimate.stable && nextVoice >= 4000,
                voiceTimeMs = nextVoice,
                noteEvents = estimate.events,
                phrases = estimate.phrases,
                uniqueNotes = estimate.uniqueNotes,
                sampleQuality = estimate.sampleQuality,
                message = message
            )
        }
    }

    fun stop() {
        if (!_state.value.listening) return
        recorder.stop()
        updateEstimate(tonalityEngine.finish(System.currentTimeMillis()), null, 0L)
        val snapshot = _state.value
        _state.update {
            it.copy(
                listening = false,
                message = when {
                    snapshot.bestKey == null || snapshot.voiceTimeMs < 2500 -> "Análise inconclusiva"
                    snapshot.sampleQuality != SampleQuality.GOOD -> "Amostra insuficiente — cante uma frase completa"
                    snapshot.relativeAmbiguous -> "Resultado provável — modo ainda ambíguo"
                    else -> "Análise concluída"
                }
            )
        }
        val key = snapshot.bestKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.save(key.tonic.name, key.mode.name, snapshot.confidence, snapshot.voiceTimeMs)
        }
    }

    fun playTonic() = _state.value.bestKey?.let(playback::playTonic)
    fun playChord() = _state.value.bestKey?.let(playback::playChord)
    fun playScale() = _state.value.bestKey?.let(playback::playScale)

    override fun onCleared() { recorder.stop() }

    companion object {
        fun factory(repo: TonalityRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TonalityViewModel(repo) as T
        }
    }
}

@Composable
fun OuvidoAbsolutoApp(viewModel: TonalityViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("🎤") }, label = { Text("Detectar") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📜") }, label = { Text("Histórico") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == 0) DetectorScreen(viewModel) else HistoryScreen(viewModel)
        }
    }
}

@Composable
fun DetectorScreen(viewModel: TonalityViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ouvido Absoluto AI", style = MaterialTheme.typography.headlineMedium)
                Text("Descubra o tom principal da música que você está cantando.")
            }
        }

        if (!granted) {
            item {
                Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Permitir microfone") }
            }
            return@LazyColumn
        }

        item { Text(state.message) }

        if (state.listening) {
            item {
                val progress = (state.voiceTimeMs / 12000f).coerceIn(0f, 1f)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Qualidade da amostra", style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            when (state.sampleQuality) {
                                SampleQuality.INSUFFICIENT -> "Continue cantando por pelo menos 10–12 segundos."
                                SampleQuality.LOW_VARIETY -> "Use uma frase completa, com notas graves e agudas."
                                SampleQuality.GOOD -> "Boa amostra. Finalize a frase para confirmar o tom."
                            }
                        )
                    }
                }
            }
        }

        state.currentPitch?.let { pitch ->
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nota atual: ${pitch.note.pt}${pitch.octave}", style = MaterialTheme.typography.headlineSmall)
                    Text("${"%.1f".format(pitch.frequency)} Hz · ${"%+.0f".format(pitch.cents)} cents")
                }
            }
        }

        state.bestKey?.let { key ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (state.stable) "✓ Tonalidade provável" else "Tom provável", style = MaterialTheme.typography.titleMedium)
                        Text(key.displayName, style = MaterialTheme.typography.displaySmall)
                        Text(key.symbol, style = MaterialTheme.typography.headlineMedium)
                        LinearProgressIndicator(progress = { state.confidence.toFloat() }, modifier = Modifier.fillMaxWidth())
                        Text("Confiança: ${(state.confidence * 100).toInt()}%")

                        if (state.relativeAmbiguous && state.relativeCandidate != null) {
                            HorizontalDivider()
                            Text("Maior/menor relativo ainda próximo", style = MaterialTheme.typography.titleSmall)
                            Text("${key.displayName} × ${state.relativeCandidate!!.key.displayName}")
                            Text("Continue cantando, de preferência até o fim de uma frase musical.")
                        } else {
                            Text("Relativa: ${key.relative.displayName}")
                        }

                        Text("Escala: ${key.scaleNotes.joinToString(" ") { it.symbol }}")
                        Text("Acorde: ${key.chordNotes.joinToString(" ") { it.symbol }}")
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::playTonic, modifier = Modifier.weight(1f), enabled = !state.listening) { Text("Tônica") }
                    OutlinedButton(onClick = viewModel::playChord, modifier = Modifier.weight(1f), enabled = !state.listening) { Text("Acorde") }
                    OutlinedButton(onClick = viewModel::playScale, modifier = Modifier.weight(1f), enabled = !state.listening) { Text("Escala") }
                }
            }

            if (state.alternatives.isNotEmpty()) {
                item {
                    Text(
                        "Alternativa: ${state.alternatives.first().key.displayName} · ${(state.alternatives.first().score * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Text("Voz analisada: ${"%.1f".format(state.voiceTimeMs / 1000.0)} s · ${state.noteEvents} eventos · ${state.uniqueNotes} notas diferentes · ${state.phrases} frases")
        }

        item {
            Button(
                onClick = { if (state.listening) viewModel.stop() else viewModel.start() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.listening) "Finalizar análise" else "Começar a cantar")
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: TonalityViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Histórico", style = MaterialTheme.typography.headlineMedium) }
        if (history.isEmpty()) item { Text("Nenhuma análise salva ainda.") }
        items(history, key = { it.id }) { item ->
            val key = runCatching { MusicalKey(NoteName.valueOf(item.tonic), KeyMode.valueOf(item.mode)) }.getOrNull()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(key?.displayName ?: "Resultado")
                    key?.let { Text("${it.symbol} · relativa ${it.relative.symbol}") }
                    Text("${(item.confidence * 100).toInt()}% · ${"%.1f".format(item.voiceTimeMs / 1000.0)} s de voz")
                }
            }
        }
    }
}
