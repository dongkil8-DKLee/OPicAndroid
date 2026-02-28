# OPIc Android — PLAN SPEC V2

> 작성일: 2026-02-25
> 기반: 인터뷰 결정 사항 + PC 원본(test_screen.py) 분석

---

## 인터뷰 결정 사항 요약

| 항목 | 결정 |
|------|------|
| STT 엔진 | Android SpeechRecognizer (무료, 구글 서버, 실시간) |
| 시험 자동 진행 | PC 완전 동일 (재생→beep→자동녹음→5초 Stop활성→2분→자동 다음) |
| 오디오 컨트롤 UI | 재생버튼 아래 시크바 + 우측 [0.75x][1.0x] 칩 |
| 복습 삭제 UX | 즉시 완전 삭제 (다이얼로그 없음, Snackbar만) |
| 오디오 없는 문제 | Android TTS로 대체 재생 |
| STT 변환 시점 | 녹음 완료 즉시 자동 |

---

## 구현 기능 목록 (8개)

| # | 기능 | 복잡도 |
|---|------|--------|
| 1 | 단계 선택 화면 스크롤 수정 (6번 잘림) | 낮음 |
| 2 | 복습 목록 비어 있는 버그 수정 | 중간 |
| 3 | 복습 목록 스와이프 삭제 | 중간 |
| 4 | 오디오 시크바 + 슬로우 재생 | 중간 |
| 5 | 학습 화면 STT 자동 변환 | 높음 |
| 6 | 시험 화면 PC 원본 동일하게 전면 재작성 | 높음 |
| 7 | 단어장 데이터 200+150개 추가 | 낮음 |

---

## Feature 1: 단계 선택 화면 스크롤 수정

### 문제
`SelfAssessmentScreen.kt`에서 6개 난이도 카드가 Column 안에 고정되어 있어
하단 6번 카드가 하단 버튼에 가려지거나 화면 밖으로 나감.

### 수정 방향
```kotlin
// Before: Column 직접 사용
Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) { ... }

// After: verticalScroll 추가
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(bottom = 16.dp)
) { ... }
```

### 수정 파일
- `ui/test/SelfAssessmentScreen.kt` — Column에 `verticalScroll` 추가

---

## Feature 2: 복습 목록 비어 있는 버그 수정

### 원인 분석 (확인 필요)
`ReviewListScreen.kt`가 "아직 시험 기록이 없습니다"를 표시하는 이유:
1. `TestCompleteScreen`에서 세션 저장 후 `review_list`로 이동하지 않음
2. `ReviewViewModel.getAllSessions()` 쿼리가 올바른 DB를 바라보지 않음
3. 시험 완료 시 세션이 DB에 저장되지 않는 버그

### 조사 및 수정 순서
1. `TestCompleteScreen.kt` → 시험 완료 후 저장 로직 확인
2. `TestViewModel.kt` → `saveSession()` 호출 여부 확인
3. `TestRepository.kt` / `TestDao.kt` → INSERT 쿼리 확인
4. `ReviewViewModel.kt` → `getAllSessions()` Flow 수집 확인

### 수정 파일
- `ui/test/TestCompleteScreen.kt` — 저장 로직 검토
- `ui/test/TestViewModel.kt` — 세션 저장 호출 확인
- `ui/review/ReviewViewModel.kt` — Flow 수집 버그 있으면 수정

---

## Feature 3: 복습 목록 스와이프 삭제

### 구현 방식
Material3 `SwipeToDismissBox` 사용:
```kotlin
// ReviewListScreen.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteItem(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,   // 오른쪽 스와이프 비활성
        enableDismissFromEndToStart = true,    // 왼쪽 스와이프만
        backgroundContent = {
            // 빨간 삭제 배경
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Red).padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.White)
            }
        }
    ) {
        content()
    }
}
```

### 삭제 처리 (즉시 완전 삭제 + Snackbar)
```kotlin
// ReviewViewModel.kt 추가
fun deleteSession(sessionId: Int) {
    viewModelScope.launch {
        testRepository.deleteSession(sessionId)   // DB 삭제
        deleteRecordingFiles(sessionId)           // 녹음 파일 삭제
        loadSessions()                            // 목록 갱신
    }
}

private suspend fun deleteRecordingFiles(sessionId: Int) {
    val dir = File(context.filesDir, "test_recordings")
    dir.listFiles { f -> f.name.contains("_${sessionId}_") || ... }
        ?.forEach { it.delete() }
}
```

```kotlin
// ReviewListScreen.kt — Snackbar 표시
val snackbarHostState = remember { SnackbarHostState() }
LaunchedEffect(deleteEvent) {
    snackbarHostState.showSnackbar("시험 기록이 삭제되었습니다")
}
```

### DB 추가
```kotlin
// TestDao.kt 추가
@Query("DELETE FROM Test_Sessions WHERE session_id = :sessionId")
suspend fun deleteSession(sessionId: Int)

@Query("DELETE FROM Test_Results WHERE session_id = :sessionId")
suspend fun deleteResultsBySession(sessionId: Int)
```

### 수정/추가 파일
- `ui/review/ReviewListScreen.kt` — SwipeToDismissBox 적용
- `ui/review/ReviewViewModel.kt` — deleteSession(), deleteRecordingFiles()
- `data/local/dao/TestDao.kt` — deleteSession, deleteResultsBySession 쿼리
- `data/repository/TestRepository.kt` — deleteSession() 메서드

---

## Feature 4: 오디오 시크바 + 슬로우 재생

### AudioPlayerManager 확장
```kotlin
// AudioPlayerManager.kt 추가
private val _currentPositionMs = MutableStateFlow(0L)
val currentPositionMs: StateFlow<Long> = _currentPositionMs

private val _durationMs = MutableStateFlow(0L)
val durationMs: StateFlow<Long> = _durationMs

// 1초마다 위치 업데이트 (재생 중일 때만)
private val positionUpdateJob: Job? = null

fun startPositionUpdates() {
    positionUpdateJob = scope.launch {
        while (true) {
            if (_playbackState.value == PlaybackState.PLAYING) {
                _currentPositionMs.value = exoPlayer.currentPosition
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(200L)   // 200ms 간격 (시크바 부드러운 업데이트)
        }
    }
}

fun seekTo(positionMs: Long) {
    exoPlayer.seekTo(positionMs)
    _currentPositionMs.value = positionMs
}

fun setPlaybackSpeed(speed: Float) {
    // pitch=1.0f로 고정 → 음높이 유지 (어학 학습 핵심)
    exoPlayer.setPlaybackParameters(PlaybackParameters(speed, 1.0f))
}
```

### StudyDetailScreen UI
```kotlin
// 재생 버튼 영역 (기존 버튼 아래에 추가)
val currentPos by viewModel.audioCurrentPos.collectAsState()
val duration by viewModel.audioDuration.collectAsState()
val playbackSpeed by viewModel.playbackSpeed.collectAsState()

// 시크바
if (duration > 0) {
    Slider(
        value = currentPos.toFloat(),
        onValueChange = { viewModel.seekTo(it.toLong()) },
        valueRange = 0f..duration.toFloat(),
        modifier = Modifier.fillMaxWidth()
    )
    // 시간 표시
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(currentPos))
        Text(formatTime(duration))
    }
}

// 속도 칩
Row(horizontalArrangement = Arrangement.End) {
    FilterChip(
        selected = playbackSpeed == 0.75f,
        onClick = { viewModel.setPlaybackSpeed(0.75f) },
        label = { Text("0.75x") }
    )
    Spacer(Modifier.width(8.dp))
    FilterChip(
        selected = playbackSpeed == 1.0f,
        onClick = { viewModel.setPlaybackSpeed(1.0f) },
        label = { Text("1.0x") }
    )
}
```

### 슬로우 재생 음질 참고
- ExoPlayer `PlaybackParameters(speed=0.75f, pitch=1.0f)`:
  - `pitch=1.0f` = 원래 음높이 유지 (음이 낮아지지 않음)
  - 내부적으로 time-stretching 알고리즘 사용
  - 0.75x 정도에서는 음질 변형 최소. 0.5x 이하에서는 robotic 효과 발생 가능
  - 어학 학습용 0.75x는 충분히 자연스러움

### 수정 파일
- `util/AudioPlayerManager.kt` — 위치/시크/속도 API 추가
- `ui/study/StudyDetailScreen.kt` — 시크바 + 속도 칩 UI 추가
- `ui/study/StudyViewModel.kt` — audioCurrentPos, audioDuration, playbackSpeed StateFlow

---

## Feature 5: 학습 화면 STT 자동 변환

### 기술적 제약 및 해결책

**문제**: Android SpeechRecognizer는 실시간 마이크 입력만 지원. 녹음 파일(.m4a) 직접 인식 불가.

**채택 방식**: 녹음 중 AudioRecorderManager(파일 저장) + SpeechRecognizer(동시 인식)
```
녹음 시작
  ├─ AudioRecorderManager.startRecording() → 파일 저장
  └─ SpeechRecognizer.startListening()     → 실시간 텍스트
                                              ↓
                                    onResults(텍스트) → ViewModel STT StateFlow
```

**동시 마이크 접근 처리**:
```kotlin
fun startRecordingWithSTT(context: Context, outputPath: String) {
    // 1. 파일 녹음 시작
    audioRecorder.startRecording(outputPath)

    // 2. STT 동시 시작 (충돌 가능성 있음)
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 120_000L)  // 2분
    }
    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onPartialResults(bundle: Bundle) {
            val partial = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            _sttPartialResult.value = partial ?: ""
        }
        override fun onResults(bundle: Bundle) {
            val result = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            _sttResult.value = result ?: ""
        }
        override fun onError(error: Int) {
            _sttResult.value = "[인식 실패 - 네트워크 확인]"
        }
        // ... 나머지 빈 구현
    })
    speechRecognizer.startListening(intent)
}
```

**⚠️ 우려 사항**:
- Android 기기에 따라 AudioRecord + SpeechRecognizer 동시 마이크 접근 실패 가능
- 실패 시 Fallback: 파일 녹음은 유지, STT 결과 없이 "인식 불가" 메시지 표시
- Android 10+에서는 concurrent audio capture 지원으로 성공률 높음

### UI (StudyDetailScreen)
```kotlin
// 녹음 완료 후 STT 결과 영역
val sttResult by viewModel.sttResult.collectAsState()
val sttPartial by viewModel.sttPartialResult.collectAsState()

if (isRecording && sttPartial.isNotEmpty()) {
    Text(
        "인식 중: $sttPartial",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}

if (!isRecording && sttResult != null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("STT 결과", fontWeight = FontWeight.SemiBold)
            Text(sttResult!!, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

### 수정 파일
- `util/AudioRecorderManager.kt` — SpeechRecognizer 연동 (또는 별도 SttManager.kt 생성)
- `ui/study/StudyDetailScreen.kt` — STT 결과 표시 UI 추가
- `ui/study/StudyViewModel.kt` — sttResult, sttPartialResult StateFlow 추가

---

## Feature 6: 시험 화면 PC 원본 동일하게 전면 재작성

### PC 원본 분석 (test_screen.py)

**플로우 (정확한 순서)**:
```
문제 로드
  → Play 버튼 활성 (1회만), Record/Next 비활성
  → [Play 클릭]
  → 오디오 재생 (또는 TTS 대체)
  → 재생 완료 → beep.wav 재생
  → beep 완료 + 150ms → 자동 녹음 시작
  → 5초 후 Record(Stop) 버튼 활성화
  → 2분 카운트다운 (녹음 중)
      ├─ 60초 이상: 초록
      ├─ 30~60초: 주황
      └─ 30초 이하: 빨강
  → [Record 버튼 클릭 또는 타이머 만료] → 녹음 종료
  → Next 버튼 활성화
  → [Next 클릭] → 900ms 후 다음 문제 자동 Play
```

**UI 구조 (PC 원본 2패널)**:
```
┌─────────────────────────────────────────────────────┐
│  Question X of 15                                   │  ← 제목만 (텍스트 없음)
├─────────────────────┬───────────────────────────────┤
│  [EVA 이미지]       │  문항 진행:                   │
│  ═══ 타이머바 ══   │  ①  ②  ③  ④  ⑤            │
│  [▶ Play]          │  ⑥  ⑦  ⑧  ⑨  ⑩           │
│  [████ RECORD ████] │  ⑪  ⑫  ⑬  ⑭  ⑮           │
│  ║ 마이크레벨바     │                               │
├─────────────────────┴───────────────────────────────┤
│  [Home]                              [Next >]        │
└─────────────────────────────────────────────────────┘
```

### TestExecutionScreen.kt 전면 재작성

**상태 관리**:
```kotlin
data class TestExecutionUiState(
    val questions: List<QuestionEntity> = emptyList(),
    val currentIndex: Int = 0,
    val phase: TestPhase = TestPhase.IDLE,
    val countdownSeconds: Int = 120,
    val micLevel: Float = 0f,
    val sessionId: Int? = null,
    val isLoading: Boolean = true
)

enum class TestPhase {
    IDLE,        // 대기 (Play 버튼만 활성)
    PLAYING,     // 오디오 재생 중
    BEEPING,     // beep 재생 중
    RECORDING,   // 녹음 중 (5초 후 Stop 활성)
    RECORDED,    // 녹음 완료 (Next 활성)
    COMPLETE     // 모든 문제 완료
}
```

**타이머 구현**:
```kotlin
// TestViewModel.kt
private var countdownJob: Job? = null

fun startCountdown() {
    countdownJob = viewModelScope.launch {
        var seconds = 120
        while (seconds > 0 && _state.value.phase == TestPhase.RECORDING) {
            delay(1000L)
            seconds--
            _state.update { it.copy(countdownSeconds = seconds) }
        }
        if (seconds <= 0) {
            stopRecordingAndAdvance()  // 자동 다음 문제
        }
    }
}
```

**오디오 없는 문제 → TTS 대체**:
```kotlin
// TestViewModel.kt
private val tts = TextToSpeech(context) { status ->
    if (status == TextToSpeech.SUCCESS) {
        tts.language = Locale.US  // 영어 TTS
    }
}

fun playQuestion() {
    val question = currentQuestion ?: return
    val resolvedUri = audioFileResolver.resolve(question.questionAudio)

    if (resolvedUri != null) {
        audioPlayer.play(resolvedUri.toString())
    } else if (question.question.isNotBlank()) {
        // TTS 대체
        tts.speak(question.question, TextToSpeech.QUEUE_FLUSH, null, "Q_${currentIndex}")
        // TTS 완료 후 beep → 녹음 시작 (UtteranceProgressListener 사용)
    } else {
        onPlaybackComplete()  // 오디오도 텍스트도 없으면 바로 녹음
    }
}
```

**마이크 레벨 실시간 표시**:
```kotlin
// AudioRecorderManager.kt에 추가
var onMicLevelChanged: ((Float) -> Unit)? = null

// 녹음 루프에서:
val rms = sqrt(chunk.map { it * it }.average()).toFloat()
val normalized = (rms * 15f).coerceIn(0f, 1f)
onMicLevelChanged?.invoke(normalized)
```

**숫자 그리드 (문항 진행)**:
```kotlin
@Composable
fun QuestionProgressGrid(
    total: Int,           // 15
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("문항 진행:", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        (0 until total).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    val bgColor = when {
                        i < currentIndex -> Color(0xFFD3D3D3)   // 완료: 회색
                        i == currentIndex -> Color(0xFF333333)   // 현재: 어두운
                        else -> Color(0xFFF0F0F0)                // 미완: 밝은
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(bgColor, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${i + 1}",
                            color = if (i == currentIndex) Color.White else Color(0xFF555555),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

**beep 에셋**:
- `assets/beep.wav` 파일 필요 (없으면 300ms 빈 딜레이로 대체)
- `val beepPlayer = MediaPlayer.create(context, R.raw.beep)` 또는 assets에서 로드

### 수정/신규 파일
- `ui/test/TestExecutionScreen.kt` — 전면 재작성
- `ui/test/TestViewModel.kt` — TestPhase, countdown, TTS, 마이크 레벨 추가
- `util/AudioPlayerManager.kt` — 재생 완료 콜백 추가 (onPlaybackComplete)
- `util/AudioRecorderManager.kt` — 마이크 레벨 콜백 추가
- `app/src/main/res/raw/beep.wav` — beep 에셋 (신규, 없으면 생략)

### ⚠️ 트레이드오프
- **화면 잠금 문제**: 녹음 중 화면이 꺼지면 자동 진행 타이머가 멈출 수 있음
  → `WakeLock` 또는 `ForegroundService` 검토 필요
- **TTS 완료 감지**: `UtteranceProgressListener.onDone()` 콜백이 메인 스레드가 아닐 수 있음
  → `Handler(Looper.getMainLooper()).post { ... }` 필요

---

## Feature 7: 단어장 데이터 200+150개 추가

### 추가 방법
`OPicDatabase.kt`의 `MIGRATION_1_2` 내 `seedWords()` / `seedSentences()` 리스트 확장.
현재 30개 단어 + 20개 문장 → **200개 단어 + 150개 문장**으로 증량.

### 카테고리 구성 (단어 200개)

**일상/직장 (60개)**
manage, schedule, routine, responsibility, deadline, presentation, productive, efficient,
collaborate, negotiate, promote, achievement, supervisor, colleague, department,
meeting, project, budget, client, proposal, contract, performance, evaluation,
salary, benefit, workplace, career, professional, qualified, experience,
interview, opportunity, challenge, goal, strategy, decision, solution,
teamwork, leadership, communication, flexible, remote, overtime,
progress, confident, capable, motivate, dedicate, accomplish, commit,
adjust, adapt, contribute, organize, coordinate, prioritize, delegate, review

**여가/취미 (50개)**
pursue, participate, relax, leisure, passion, fascinating, refresh, entertaining,
interest, adventure, explore, travel, destination, itinerary, memorable,
sightseeing, photography, hiking, fitness, wellness, creative, artistic,
inspire, talent, skill, collection, exhibition, performance, concert, cultural,
outdoor, indoor, favorite, recommend, enjoyment, satisfaction, balanced,
appreciate, discover, experience, remarkable, unique, traditional, modern,
scenery, landscape, atmosphere, cuisine, culture, historic

**의견/사회 (50개)**
advantage, disadvantage, perspective, trend, impact, influence,
significant, challenge, opportunity, improvement, development,
community, education, healthcare, economy, policy, global, local,
comparison, contrast, benefit, consequence, evidence, research,
statistics, majority, minority, diversity, equality, responsibility,
awareness, innovation, sustainable, technology, environment,
substantial, considerable, remarkable, exceptional, fundamental,
inevitable, controversial, reasonable, practical, ambitious, determined,
comprehensive, effective, logical, rational

**자기소개/묘사 (40개)**
originally, currently, previously, recently, generally, typically,
personally, particularly, especially, specifically, definitely,
absolutely, certainly, actually, fortunately, essentially,
frequently, occasionally, gradually, immediately, eventually,
significantly, considerably, completely, naturally, obviously,
relatively, approximately, primarily, mainly, mostly, largely,
basically, honestly, sincerely, eagerly, willingly, proudly,
commonly, usually

### 카테고리 구성 (문장 150개)

**자기소개 (25개)**
- "Let me introduce myself briefly."
- "I have been working as a ... for ... years."
- "I am originally from ..., but I currently live in ..."
- "My main responsibilities include ..."
- "I am passionate about ..."
등

**경험 묘사 (25개)**
- "I first got interested in ... when ..."
- "One of the most memorable experiences I had was ..."
- "It was quite challenging because ..."
- "Looking back on it now, ..."
- "That experience taught me ..."
등

**의견 제시 (25개)**
- "In my opinion, ..."
- "From my perspective, ..."
- "I strongly believe that ..."
- "There are several reasons why I think ..."
- "On the other hand, ..."
등

**비교/대조 (25개)**
- "Compared to ..., I prefer ..."
- "While ... has its advantages, ..."
- "The main difference between ... and ... is ..."
- "Both ... and ... have their merits, but ..."
- "In contrast to the past, nowadays ..."
등

**일상/습관 (25개)**
- "On a typical day, I ..."
- "I have a habit of -ing ..."
- "I tend to ..."
- "Every morning I make it a point to ..."
- "I usually spend my weekends ..."
등

**고급 표현 (25개)**
- "What I find particularly interesting about ... is ..."
- "I would say that the most significant aspect is ..."
- "Not only does ... but it also ..."
- "The reason I feel this way is that ..."
- "It goes without saying that ..."
등

### 구현 방식
DB 마이그레이션이 이미 실행된 기기에서는 MIGRATION_1_2가 다시 실행되지 않으므로,
**MIGRATION_2_3** 또는 **별도 prepopulate 로직** 필요:

```kotlin
// 옵션 A: 새 마이그레이션 추가 (v2 → v3)
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 기존 카드 삭제 후 전체 재삽입
        db.execSQL("DELETE FROM Flashcards")
        seedWords(db)      // 200개
        seedSentences(db)  // 150개
    }
}
```

---

## 수정/신규 파일 목록

### 수정
| 파일 | 변경 내용 |
|------|----------|
| `ui/test/SelfAssessmentScreen.kt` | verticalScroll 추가 |
| `ui/test/TestExecutionScreen.kt` | 전면 재작성 (PC 원본 동일) |
| `ui/test/TestViewModel.kt` | TestPhase, 타이머, TTS, 마이크 레벨 |
| `ui/review/ReviewListScreen.kt` | SwipeToDismissBox 스와이프 삭제 |
| `ui/review/ReviewViewModel.kt` | deleteSession(), Flow 버그 수정 |
| `ui/study/StudyDetailScreen.kt` | STT 결과 표시, 시크바, 속도 칩 |
| `ui/study/StudyViewModel.kt` | sttResult, audioPos, speed StateFlow |
| `util/AudioPlayerManager.kt` | 시크바용 position/duration/seekTo/speed |
| `util/AudioRecorderManager.kt` | 마이크 레벨 콜백, STT 연동 |
| `data/local/dao/TestDao.kt` | deleteSession, deleteResultsBySession |
| `data/local/db/OPicDatabase.kt` | MIGRATION_2_3, 단어장 데이터 200+150 |
| `data/repository/TestRepository.kt` | deleteSession() |

### 신규
| 파일 | 내용 |
|------|------|
| `util/SttManager.kt` (선택) | SpeechRecognizer 래핑 유틸 |
| `app/src/main/res/raw/beep.wav` | 시험 비프음 에셋 |

---

## 구현 순서 (의존성 고려)

```
Step 1: SelfAssessmentScreen 스크롤 수정          (5분, 독립)
Step 2: 복습 버그 수정                            (디버깅, 우선)
Step 3: ReviewListScreen 스와이프 삭제 + TestDao  (2시간)
Step 4: AudioPlayerManager 시크바/속도 확장        (1시간)
Step 5: StudyDetailScreen 시크바 + 속도 UI        (30분)
Step 6: STT 연동 (SttManager + StudyViewModel)   (2시간)
Step 7: 단어장 MIGRATION_2_3 + 데이터 200+150     (1시간)
Step 8: TestExecutionScreen 전면 재작성            (3~4시간, 가장 복잡)
```

---

## 우려 사항 및 리스크

### 높음
- **시험 자동 진행 + 화면 잠금**: 모바일에서 2분 녹음 중 화면이 꺼지면 타이머/녹음이 백그라운드로 밀릴 수 있음. `WakeLock` 필요 여부 검토.
- **STT 동시 마이크 접근**: 기기/Android 버전에 따라 불가능할 수 있음. Fallback 처리 필수.

### 중간
- **TTS 완료 콜백 스레드 안전성**: `UtteranceProgressListener`는 백그라운드 스레드에서 호출될 수 있음. Main dispatcher 전환 필요.
- **복습 버그 원인 불명**: 코드 분석만으로는 정확한 원인 미확정. 런타임 디버깅 필요.

### 낮음
- **MIGRATION_2_3 기기 적용**: 이미 앱이 설치된 기기에서 v2 DB를 v3으로 업그레이드 시 단어장 데이터만 교체. 기존 FlashcardProgress(사용자 학습 진도)는 보존됨.
- **beep.wav 에셋**: 파일 없으면 300ms 딜레이로 graceful 대체 처리.

---

## 검증 체크리스트

- [ ] SelfAssessmentScreen: 6번 단계 카드 전체 표시 + 스크롤 동작
- [ ] 시험 복습: 시험 완료 후 목록에 기록 표시
- [ ] 복습 좌측 스와이프: 삭제 버튼 노출 → 탭 → 목록에서 사라짐 + Snackbar
- [ ] 오디오 시크바: 재생 중 진행 위치 업데이트 + 터치 시 해당 위치로 이동
- [ ] 슬로우 재생: 0.75x 칩 선택 시 음높이 유지된 상태로 느리게 재생
- [ ] STT: 녹음 중 실시간 텍스트 표시 → 완료 후 최종 결과 카드 표시
- [ ] 시험 화면: 문제 텍스트 없음 확인
- [ ] 시험 화면: Play→beep→자동녹음→5초 후 Stop활성→2분 카운트다운 색상 변화
- [ ] 시험 화면: 2분 만료 시 자동 다음 문제 이동
- [ ] 시험 화면: 오디오 없는 문제에서 TTS로 대체 재생
- [ ] 단어장: 200개 단어 + 150개 문장 표시 확인
