# OPIC Android 프로젝트 구조 설계서

## 확정 스택

| 항목 | 선택 |
|------|------|
| 언어 | Kotlin |
| UI | Jetpack Compose |
| DB | Room (createFromAsset) |
| 빌드 | Version Catalog (libs.versions.toml) |
| minSdk | 26 (Android 8.0) |
| 비동기 | Kotlin Coroutines + Flow |
| 네비게이션 | Navigation Compose |
| 오디오 녹음 | AudioRecord (PCM 16-bit) |
| 오디오 재생 | MediaPlayer |
| TTS | android.speech.tts.TextToSpeech |
| 설정 저장 | SharedPreferences (DataStore 불필요 — 단순 key-value) |

---

## 1. 모듈 구조 (단일 모듈)

> 1차 MVP는 단일 app 모듈. 멀티모듈은 기능 안정 후 고려.

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── assets/
│   │   ├── opic.db                    ← pre-populated DB
│   │   └── Sound/                     ← 질문/답변 오디오 (mp3/wav)
│   ├── res/
│   │   ├── drawable/                  ← a1~a10.png, eva.png, 아이콘 등
│   │   ├── raw/
│   │   │   └── beep.wav              ← 녹음 시작 비프음
│   │   └── values/
│   │       ├── strings.xml
│   │       └── themes.xml
│   └── kotlin/com/example/opic/
│       │
│       ├── OPicApplication.kt         ← Application 클래스 (DB 초기화, study_decay)
│       ├── MainActivity.kt            ← 단일 Activity (Compose NavHost)
│       │
│       ├── data/                       ★ 데이터 레이어
│       │   ├── db/
│       │   │   ├── OPicDatabase.kt    ← @Database (7 entities, version=1)
│       │   │   ├── entity/
│       │   │   │   ├── User.kt
│       │   │   │   ├── Question.kt
│       │   │   │   ├── QuestionSlot.kt
│       │   │   │   ├── UserStudyProgress.kt
│       │   │   │   ├── TestSession.kt
│       │   │   │   ├── TestResult.kt
│       │   │   │   └── ApiKey.kt
│       │   │   └── dao/
│       │   │       ├── QuestionDao.kt
│       │   │       ├── StudyProgressDao.kt
│       │   │       ├── TestDao.kt
│       │   │       └── ApiKeyDao.kt
│       │   ├── repository/
│       │   │   ├── QuestionRepository.kt
│       │   │   ├── StudyRepository.kt
│       │   │   ├── TestRepository.kt
│       │   │   └── ExportImportRepository.kt
│       │   └── prefs/
│       │       └── StudyPreferences.kt  ← SharedPreferences 래퍼 (Config.ini 대체)
│       │
│       ├── audio/                      ★ 오디오 레이어
│       │   ├── AudioPlayer.kt          ← MediaPlayer 래퍼 (재생+완료콜백)
│       │   ├── AudioRecorder.kt        ← AudioRecord 래퍼 (WAV 녹음+RMS)
│       │   ├── TtsManager.kt           ← TextToSpeech 래퍼 (캐시 포함)
│       │   └── AudioFileResolver.kt    ← find_audio_file() 이식 (assets 검색)
│       │
│       ├── domain/                     ★ 비즈니스 로직
│       │   ├── QuestionGenerator.kt    ← generate_test_set() 1:1 이식
│       │   ├── LevelCalculator.kt      ← _calculate_user_level() 이식
│       │   └── StudyDecay.kt           ← apply_study_decay() 이식
│       │
│       ├── ui/                         ★ UI 레이어 (Compose)
│       │   ├── navigation/
│       │   │   └── OPicNavGraph.kt     ← NavHost + 라우트 정의
│       │   ├── theme/
│       │   │   └── OPicTheme.kt        ← 다크/라이트 테마
│       │   ├── start/
│       │   │   ├── StartScreen.kt      ← 홈 화면 UI
│       │   │   └── StartViewModel.kt
│       │   ├── survey/
│       │   │   ├── SurveyScreen.kt     ← 4파트 설문 UI
│       │   │   └── SurveyViewModel.kt
│       │   ├── assessment/
│       │   │   └── SelfAssessmentScreen.kt  ← 난이도 선택 (ViewModel 불필요)
│       │   ├── begintest/
│       │   │   └── BeginTestScreen.kt  ← 시험 시작 확인 (단순 이미지)
│       │   ├── test/
│       │   │   ├── TestScreen.kt       ← 시험 실행 UI
│       │   │   └── TestViewModel.kt    ← 상태 머신 + 녹음/재생 제어
│       │   ├── review/
│       │   │   ├── ReviewScreen.kt     ← 결과 리뷰 UI
│       │   │   └── ReviewViewModel.kt
│       │   ├── study/
│       │   │   ├── StudyScreen.kt      ← 학습 모드 UI
│       │   │   └── StudyViewModel.kt   ← 필터 체인 + 그룹재생 + 편집
│       │   └── components/             ← 공용 Composable
│       │       ├── ScriptEditor.kt     ← 읽기전용↔편집 토글 텍스트
│       │       ├── AudioControlBar.kt  ← [▶ Play][■ Stop] 버튼 바
│       │       ├── MicLevelBar.kt      ← 마이크 레벨 프로그레스 바
│       │       ├── CountdownTimer.kt   ← 120초 타이머 (색상 변경)
│       │       └── QuestionGrid.kt     ← [1]~[15] 번호 그리드
│       │
│       └── util/
│           ├── WavWriter.kt            ← PCM → WAV 헤더 작성
│           └── FileUtils.kt            ← 경로 헬퍼
│
└── gradle/
    └── libs.versions.toml              ← Version Catalog
```

---

## 2. 데이터 레이어 설계

### 2.1 Room Database

```kotlin
// OPicDatabase.kt
@Database(
    entities = [
        User::class,
        Question::class,
        QuestionSlot::class,
        UserStudyProgress::class,
        TestSession::class,
        TestResult::class,
        ApiKey::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OPicDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun studyProgressDao(): StudyProgressDao
    abstract fun testDao(): TestDao
    abstract fun apiKeyDao(): ApiKeyDao

    companion object {
        fun create(context: Context): OPicDatabase =
            Room.databaseBuilder(context, OPicDatabase::class.java, "opic.db")
                .createFromAsset("opic.db")   // pre-populated DB 복사
                .build()
    }
}
```

### 2.2 Entity 정의 (Python 테이블 1:1)

```kotlin
// entity/Question.kt
@Entity(tableName = "Questions")
data class Question(
    @PrimaryKey
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "\"set\"") val set: String?,        // SQL 예약어 → 큰따옴표
    @ColumnInfo(name = "type") val type: String?,
    @ColumnInfo(name = "combo") val combo: String?,
    @ColumnInfo(name = "question_text") val questionText: String?,
    @ColumnInfo(name = "answer_script") val answerScript: String?,
    @ColumnInfo(name = "question_audio") val questionAudio: String?,
    @ColumnInfo(name = "answer_audio") val answerAudio: String?,
    @ColumnInfo(name = "user_script") val userScript: String?
)

// entity/UserStudyProgress.kt
@Entity(
    tableName = "User_Study_Progress",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["user_id"], childColumns = ["user_id"]),
        ForeignKey(entity = Question::class, parentColumns = ["question_id"], childColumns = ["question_id"])
    ],
    indices = [Index(value = ["user_id", "question_id"], unique = true)]
)
data class UserStudyProgress(
    @PrimaryKey
    @ColumnInfo(name = "progress_id") val progressId: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "study_count") val studyCount: Int = 0,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Int = 0,
    @ColumnInfo(name = "stt_text") val sttText: String? = null,
    @ColumnInfo(name = "analysis_result") val analysisResult: String? = null
)

// entity/TestSession.kt
@Entity(
    tableName = "Test_Sessions",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["user_id"], childColumns = ["user_id"])
    ]
)
data class TestSession(
    @PrimaryKey
    @ColumnInfo(name = "session_id") val sessionId: Int? = null,
    @ColumnInfo(name = "user_id") val userId: Int?,
    @ColumnInfo(name = "timestamp") val timestamp: String? = null
)

// entity/TestResult.kt
@Entity(
    tableName = "Test_Results",
    foreignKeys = [
        ForeignKey(entity = TestSession::class, parentColumns = ["session_id"], childColumns = ["session_id"]),
        ForeignKey(entity = Question::class, parentColumns = ["question_id"], childColumns = ["question_id"])
    ]
)
data class TestResult(
    @PrimaryKey
    @ColumnInfo(name = "result_id") val resultId: Int? = null,
    @ColumnInfo(name = "session_id") val sessionId: Int?,
    @ColumnInfo(name = "question_id") val questionId: Int?,
    @ColumnInfo(name = "question_number") val questionNumber: Int?,
    @ColumnInfo(name = "user_audio_path") val userAudioPath: String? = null,
    @ColumnInfo(name = "similarity_score") val similarityScore: Double? = null,
    @ColumnInfo(name = "stt_result") val sttResult: String? = null
)

// entity/User.kt
@Entity(tableName = "Users")
data class User(
    @PrimaryKey
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "username") val username: String
)

// entity/QuestionSlot.kt
@Entity(
    tableName = "Question_Slots",
    foreignKeys = [
        ForeignKey(entity = Question::class, parentColumns = ["question_id"], childColumns = ["question_id"])
    ]
)
data class QuestionSlot(
    @PrimaryKey
    @ColumnInfo(name = "slot_id") val slotId: Int,
    @ColumnInfo(name = "question_id") val questionId: Int?,
    @ColumnInfo(name = "slot_number") val slotNumber: Int?
)

// entity/ApiKey.kt
@Entity(tableName = "Api_Keys")
data class ApiKey(
    @PrimaryKey
    @ColumnInfo(name = "service_name") val serviceName: String,
    @ColumnInfo(name = "api_key") val apiKey: String
)
```

### 2.3 DAO 인터페이스

```kotlin
// dao/QuestionDao.kt
@Dao
interface QuestionDao {
    // Study 필터용
    @Query("SELECT DISTINCT \"set\" FROM Questions WHERE \"set\" IS NOT NULL")
    suspend fun getAllSets(): List<String>

    @Query("SELECT DISTINCT type FROM Questions WHERE type IS NOT NULL")
    suspend fun getAllTypes(): List<String>

    @Query("SELECT * FROM Questions WHERE \"set\" = :set AND type = :type")
    suspend fun getBySetAndType(set: String, type: String): List<Question>

    @Query("SELECT * FROM Questions WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): Question?

    // QuestionGenerator용
    @Query("""
        SELECT * FROM Questions
        WHERE \"set\" IS NOT NULL AND type IS NOT NULL AND combo IS NOT NULL
        AND title != 'Self Introduction'
    """)
    suspend fun getAllValidQuestions(): List<Question>

    @Query("SELECT * FROM Questions WHERE title = 'Self Introduction' LIMIT 1")
    suspend fun getSelfIntroduction(): Question?

    // CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: Question): Long

    @Update
    suspend fun update(question: Question)

    @Query("UPDATE Questions SET question_text = :text WHERE question_id = :id")
    suspend fun updateQuestionText(id: Int, text: String)

    @Query("UPDATE Questions SET answer_script = :script WHERE question_id = :id")
    suspend fun updateAnswerScript(id: Int, script: String)

    @Query("UPDATE Questions SET user_script = :script WHERE question_id = :id")
    suspend fun updateUserScript(id: Int, script: String)

    @Delete
    suspend fun delete(question: Question)
}

// dao/StudyProgressDao.kt
@Dao
interface StudyProgressDao {
    @Query("SELECT * FROM User_Study_Progress WHERE user_id = :userId")
    suspend fun getAllByUser(userId: Int): List<UserStudyProgress>

    @Query("SELECT * FROM User_Study_Progress WHERE user_id = :userId AND question_id = :questionId")
    suspend fun getByUserAndQuestion(userId: Int, questionId: Int): UserStudyProgress?

    @Query("""
        UPDATE User_Study_Progress
        SET study_count = study_count + 1, last_modified = datetime('now')
        WHERE user_id = :userId AND question_id = :questionId
    """)
    suspend fun incrementStudyCount(userId: Int, questionId: Int)

    @Query("""
        UPDATE User_Study_Progress
        SET is_favorite = CASE WHEN is_favorite = 1 THEN 0 ELSE 1 END
        WHERE user_id = :userId AND question_id = :questionId
    """)
    suspend fun toggleFavorite(userId: Int, questionId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: UserStudyProgress)

    // study_decay용
    @Query("""
        SELECT * FROM User_Study_Progress
        WHERE study_count > 0 AND last_modified IS NOT NULL
    """)
    suspend fun getDecayCandidates(): List<UserStudyProgress>

    @Query("UPDATE User_Study_Progress SET study_count = :count WHERE progress_id = :id")
    suspend fun updateStudyCount(id: Int, count: Int)
}

// dao/TestDao.kt
@Dao
interface TestDao {
    @Insert
    suspend fun insertSession(session: TestSession): Long

    @Insert
    suspend fun insertResult(result: TestResult): Long

    @Query("UPDATE Test_Results SET user_audio_path = :path WHERE session_id = :sessionId AND question_id = :questionId")
    suspend fun updateAudioPath(sessionId: Int, questionId: Int, path: String)

    // 마지막 세션 결과 (ReviewScreen용)
    @Query("""
        SELECT tr.*, q.title, q.question_text, q.answer_script,
               q.question_audio, q.answer_audio, q.user_script,
               q."set" as q_set, q.type as q_type
        FROM Test_Results tr
        JOIN Questions q ON tr.question_id = q.question_id
        WHERE tr.session_id = (SELECT MAX(session_id) FROM Test_Sessions)
        ORDER BY tr.question_number
    """)
    suspend fun getLastSessionResults(): List<TestResultWithQuestion>
}

// 조인 결과용 POJO
data class TestResultWithQuestion(
    @ColumnInfo(name = "result_id") val resultId: Int,
    @ColumnInfo(name = "session_id") val sessionId: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "question_number") val questionNumber: Int,
    @ColumnInfo(name = "user_audio_path") val userAudioPath: String?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "question_text") val questionText: String?,
    @ColumnInfo(name = "answer_script") val answerScript: String?,
    @ColumnInfo(name = "question_audio") val questionAudio: String?,
    @ColumnInfo(name = "answer_audio") val answerAudio: String?,
    @ColumnInfo(name = "user_script") val userScript: String?,
    @ColumnInfo(name = "q_set") val set: String?,
    @ColumnInfo(name = "q_type") val type: String?
)
```

### 2.4 Repository 패턴

```kotlin
// repository/TestRepository.kt
class TestRepository(private val testDao: TestDao) {

    suspend fun createSession(userId: Int): Long {
        return testDao.insertSession(
            TestSession(sessionId = null, userId = userId)
        )
    }

    suspend fun logQuestionSet(sessionId: Int, questions: List<QuestionData>) {
        questions.forEachIndexed { index, q ->
            testDao.insertResult(
                TestResult(
                    sessionId = sessionId.toInt(),
                    questionId = q.questionId,
                    questionNumber = index + 1
                )
            )
        }
    }

    suspend fun updateRecordingPath(sessionId: Int, questionId: Int, path: String) {
        testDao.updateAudioPath(sessionId, questionId, path)
    }

    suspend fun getLastSessionResults(): List<TestResultWithQuestion> {
        return testDao.getLastSessionResults()
    }
}
```

### 2.5 SharedPreferences 래퍼 (Config.ini 대체)

```kotlin
// prefs/StudyPreferences.kt
class StudyPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("opic_config", Context.MODE_PRIVATE)

    // Python Config.ini 키와 1:1 대응
    var fontSize: Int
        get() = prefs.getInt("font_size", 18)
        set(v) = prefs.edit().putInt("font_size", v).apply()

    var repeatCount: Int
        get() = prefs.getInt("repeat", 1)
        set(v) = prefs.edit().putInt("repeat", v).apply()

    var sortMode: String
        get() = prefs.getString("sort", "주제 순서") ?: "주제 순서"
        set(v) = prefs.edit().putString("sort", v).apply()

    var studyFilter: String
        get() = prefs.getString("study_filter", "전체") ?: "전체"
        set(v) = prefs.edit().putString("study_filter", v).apply()

    var groupPlayMode: String
        get() = prefs.getString("group_play_mode", "목록 재생") ?: "목록 재생"
        set(v) = prefs.edit().putString("group_play_mode", v).apply()

    var slotFilter: String
        get() = prefs.getString("slot_filter", "Select") ?: "Select"
        set(v) = prefs.edit().putString("slot_filter", v).apply()

    var selectedSet: String
        get() = prefs.getString("set", "") ?: ""
        set(v) = prefs.edit().putString("set", v).apply()

    var selectedType: String
        get() = prefs.getString("type", "") ?: ""
        set(v) = prefs.edit().putString("type", v).apply()

    var selectedTitle: String
        get() = prefs.getString("title", "") ?: ""
        set(v) = prefs.edit().putString("title", v).apply()

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(v) = prefs.edit().putBoolean("dark_theme", v).apply()
}
```

---

## 3. 네비게이션 설계

```kotlin
// ui/navigation/OPicNavGraph.kt

// 라우트 정의 — Python show_frame() 이름과 1:1 대응
sealed class Screen(val route: String) {
    object Start : Screen("StartPage")
    object Survey : Screen("SurveyPage")
    object SelfAssessment : Screen("SelfAssessmentPage")
    object BeginTest : Screen("BeginTestPage")
    object Test : Screen("TestScreen")
    object Review : Screen("ReviewScreen")
    object Study : Screen("StudyScreen")
}

@Composable
fun OPicNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Start.route) {
        composable(Screen.Start.route) {
            StartScreen(
                onStudy = { navController.navigate(Screen.Study.route) },
                onNext  = { navController.navigate(Screen.Survey.route) },
                onReview = { navController.navigate(Screen.Review.route) }
            )
        }
        composable(Screen.Survey.route) {
            SurveyScreen(
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Screen.SelfAssessment.route) }
            )
        }
        composable(Screen.SelfAssessment.route) {
            SelfAssessmentScreen(
                onBack = { navController.popBackStack() },
                onNext = { difficulty ->
                    navController.navigate(Screen.BeginTest.route)
                }
            )
        }
        composable(Screen.BeginTest.route) {
            BeginTestScreen(
                onBack = { navController.popBackStack() },
                onStartTest = { navController.navigate(Screen.Test.route) }
            )
        }
        composable(Screen.Test.route) {
            TestScreen(
                onHome = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                },
                onFinish = {
                    navController.navigate(Screen.Review.route) {
                        popUpTo(Screen.Start.route)
                    }
                }
            )
        }
        composable(Screen.Review.route) {
            ReviewScreen(
                onHome = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                }
            )
        }
        composable(Screen.Study.route) {
            StudyScreen(
                onHome = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                }
            )
        }
    }
}
```

---

## 4. Python → Android 클래스 매핑 표

| Python | Android | 역할 |
|--------|---------|------|
| `Main.py` | `OPicApplication.kt` + `MainActivity.kt` | 초기화 + 단일 Activity |
| `Config` 상수 | `res/`, `assets/`, `Context.*Dir()` | 경로 |
| `Config.ini` | `StudyPreferences.kt` (SharedPreferences) | 상태 유지 |
| `setup_database()` | `Room.createFromAsset()` | DB 초기화 |
| `apply_study_decay()` | `StudyDecay.kt` | 학습 감쇄 |
| `QuestionGenerator` | `QuestionGenerator.kt` | 문제 세트 생성 |
| `_calculate_user_level()` | `LevelCalculator.kt` | 레벨 계산 |
| `Worker + QThread` | `Coroutine (viewModelScope)` | 비동기 |
| `pyqtSignal/slot` | `StateFlow / callback lambda` | 이벤트 |
| `pygame.mixer` | `AudioPlayer.kt (MediaPlayer)` | 재생 |
| `sounddevice` | `AudioRecorder.kt (AudioRecord)` | 녹음 |
| `pyttsx3` | `TtsManager.kt (TextToSpeech)` | TTS |
| `find_audio_file()` | `AudioFileResolver.kt` | 파일 검색 |
| `OPIcApp.show_frame()` | `NavController.navigate()` | 화면 전환 |
| `OPIcApp.log_question_set()` | `TestRepository.logQuestionSet()` | 시험 기록 |
| `export/import_all_tables` | `ExportImportRepository.kt` | 데이터 관리 |

---

## 5. 최소 동작 MVP 로드맵

> 각 단계는 **독립 실행+테스트 가능** 단위. 이전 단계가 통과해야 다음 진행.

### Phase 0: 프로젝트 뼈대 (1일)

**작업 내용:**
- Android Studio 프로젝트 생성 (Empty Compose Activity)
- `libs.versions.toml`에 의존성 선언 (Room, Navigation Compose, Coroutines)
- `build.gradle.kts` 설정 (minSdk 26, Room kapt/ksp)
- `assets/opic.db` 배치
- `assets/Sound/` 오디오 파일 배치
- `res/drawable/` 이미지 파일 배치

**실행/테스트:**
```
1. Android Studio → Build → 에뮬레이터 실행
2. 빈 화면 + "Hello" 텍스트 표시 확인
3. Logcat에서 빌드 에러 없음 확인
```

---

### Phase 1: DB + Entity + DAO (1일)

**작업 내용:**
- 7개 Entity 클래스 작성
- 4개 DAO 인터페이스 작성
- `OPicDatabase.kt` (createFromAsset)
- `OPicApplication.kt`에서 DB 인스턴스 생성

**실행/테스트:**
```kotlin
// OPicApplication.onCreate()에서 임시 검증 코드:
lifecycleScope.launch {
    val db = OPicDatabase.create(this@OPicApplication)
    val questions = db.questionDao().getAllValidQuestions()
    Log.d("DB_TEST", "Questions count: ${questions.size}")
    // 예상: Python DB와 동일한 행 수

    val intro = db.questionDao().getSelfIntroduction()
    Log.d("DB_TEST", "Self Intro: ${intro?.title}")
    // 예상: "Self Introduction"
}
```
```
검증: Logcat에서 Questions count > 0, Self Intro 존재 확인
```

---

### Phase 2: 네비게이션 + StartPage (1일)

**작업 내용:**
- `OPicNavGraph.kt` 라우트 정의
- `StartScreen.kt` + `StartViewModel.kt`
- `LevelCalculator.kt` 이식
- `StudyDecay.kt` 이식 (Application.onCreate에서 호출)
- 레벨 아바타 + 게이지 바 표시

**실행/테스트:**
```
1. 앱 실행 → StartPage 표시
2. 레벨 아바타 + "Level N" + 게이지 바 표시 확인
3. [Study] 버튼 → (빈 화면) Study 라우트 진입 확인
4. [Next >] 버튼 → (빈 화면) Survey 라우트 진입 확인
5. Back 키 → StartPage 복귀 확인
```

---

### Phase 3: Survey + SelfAssessment + BeginTest (2일)

**작업 내용:**
- `SurveyScreen.kt` + `SurveyViewModel.kt` (4파트 설문)
- `SelfAssessmentScreen.kt` (난이도 1~6 Radio)
- `BeginTestScreen.kt` (안내 이미지)
- 상태 전달: `selected_difficulty`를 SavedStateHandle 또는 shared ViewModel로 전달

**실행/테스트:**
```
1. Start → Next → Survey 4파트 진행 확인
2. Part 4 기본 12개 체크 확인
3. Survey → SelfAssessment → 미선택 Next → 경고 다이얼로그
4. 난이도 3 선택 → Next → BeginTest 이미지 표시
5. Back 키로 각 화면 역순 복귀 확인
```

---

### Phase 4: 오디오 엔진 (재생 + 녹음) (2일)

**작업 내용:**
- `AudioPlayer.kt`: MediaPlayer 래퍼, assets/파일 재생, OnCompletionListener
- `AudioRecorder.kt`: AudioRecord, WAV 작성, RMS 콜백, 120초 자동종료
- `WavWriter.kt`: PCM → WAV 헤더
- `AudioFileResolver.kt`: assets/Sound에서 mp3/wav 검색
- `RECORD_AUDIO` 런타임 퍼미션

**실행/테스트:**
```kotlin
// 임시 테스트 화면에서:
// 1. 재생 테스트
audioPlayer.playFromAssets("Sound/AL_01_Q_00.mp3") {
    Log.d("AUDIO", "Playback finished")
}
// 2. 녹음 테스트
audioRecorder.start("test_recording.wav") { rmsLevel ->
    Log.d("AUDIO", "RMS: $rmsLevel")
}
// 10초 후
audioRecorder.stop()
// 3. 녹음 파일 재생
audioPlayer.playFromFile("test_recording.wav") { ... }
```
```
검증:
- assets mp3 재생 소리 확인
- 녹음 후 파일 크기 확인 (10초 ≈ 882KB)
- 녹음 파일 재생 소리 확인
- 120초 자동종료 확인
```

---

### Phase 5: QuestionGenerator + TestScreen (3일)

**작업 내용:**
- `QuestionGenerator.kt`: 난이도별 세트 생성 로직 1:1 이식
- `TestViewModel.kt`: 상태 머신 (Play→Beep→녹음→완료→Next)
- `TestScreen.kt`: EVA/타이머/번호그리드/Play/Record/Next/Home
- `TestRepository.kt`: 세션 생성 + 결과 저장
- `TtsManager.kt`: TTS 폴백 (오디오 없을 때)

**실행/테스트:**
```
1. 난이도 1 → 12문항 생성 확인 (Logcat)
2. 난이도 3 → 15문항 생성, Q1=Self Introduction 확인
3. 난이도 5 → 15문항, 돌발×2 확인
4. TestScreen: Play → 소리 → Beep → 자동 녹음 → 타이머 카운트다운
5. Record Stop → Next 활성 → Q2 진행
6. Play 재클릭 → 무반응 (1회 제한)
7. Home → 확인 다이얼로그 → Yes → StartPage
8. Q15 완료 → ReviewScreen 자동 전환
9. DB 확인: Test_Sessions 1행 + Test_Results 15행
```

---

### Phase 6: ReviewScreen (1일)

**작업 내용:**
- `ReviewViewModel.kt`: 마지막 세션 결과 로드
- `ReviewScreen.kt`: Q/A 스크립트 + User Audio 재생 + 편집/저장
- 공용 `ScriptEditor.kt` 컴포넌트

**실행/테스트:**
```
1. 시험 완료 후 → ReviewScreen 진입
2. 15문제 번호 네비게이션 확인
3. [▶ Play User Answer] → 녹음 재생
4. [▶ Play] Q Script → 오디오 또는 TTS
5. [Edit] → 텍스트 편집 → [Save] → 확인 다이얼로그
6. 앱 종료 → 재실행 → Review → 편집한 텍스트 유지 확인
```

---

### Phase 7: StudyScreen 기본 (2일)

**작업 내용:**
- `StudyViewModel.kt`: 필터 체인 (Set→Type→Title), 문제 로드, 학습완료, 즐겨찾기
- `StudyScreen.kt`: 상단 필터 + 우측 스크립트 + 재생
- `StudyPreferences.kt` 연동 (필터 상태 유지)

**실행/테스트:**
```
1. Study 진입 → Set 드롭다운 DB에서 로드
2. Set 변경 → Type 갱신 → Title 갱신 확인
3. Title 선택 → Q/A Script 표시
4. [학습완료] 클릭 → study_count 증가 확인
5. [📌] 토글 → is_favorite 변경 확인
6. 앱 재시작 → 필터 상태 복원 확인
7. 스크립트 편집 → 저장 → DB 반영 확인
```

---

### Phase 8: StudyScreen 녹음 + 그룹재생 (2일)

**작업 내용:**
- StudyScreen 녹음 기능 (AudioRecorder 연동)
- 그룹 재생 3모드 (목록/질문/답변)
- 반복 횟수 (1~10)
- 테마 전환 (다크/라이트)
- 폰트 크기 조절

**실행/테스트:**
```
1. [● Record] → 녹음 → [▶ Play User Audio] → 재생 확인
2. 5문제 필터 → 그룹재생(목록) → 순차 재생 완료
3. 반복 2회 설정 → 총 10회 재생 확인
4. 테마 전환 → UI 정상 표시
5. 폰트 크기 변경 → 텍스트 크기 변경 확인
```

---

### 전체 Phase 요약

| Phase | 내용 | 기간 | 누적 |
|-------|------|------|------|
| 0 | 프로젝트 뼈대 | 1일 | 1일 |
| 1 | DB + Entity + DAO | 1일 | 2일 |
| 2 | 네비게이션 + StartPage | 1일 | 3일 |
| 3 | Survey + Assessment + BeginTest | 2일 | 5일 |
| 4 | 오디오 엔진 (재생+녹음) | 2일 | 7일 |
| 5 | QuestionGenerator + TestScreen | 3일 | 10일 |
| 6 | ReviewScreen | 1일 | 11일 |
| 7 | StudyScreen 기본 | 2일 | 13일 |
| 8 | StudyScreen 녹음+그룹재생 | 2일 | **15일** |

> Phase 5 완료 시점 = **핵심 플로우 E2E 동작** (시험 볼 수 있음)
> Phase 7 완료 시점 = **1차 MVP 완성** (학습까지 가능)
> Phase 8 완료 시점 = **기능 동일성 달성**

---

## 6. libs.versions.toml 초안

```toml
[versions]
kotlin = "1.9.22"
agp = "8.2.2"
compose-bom = "2024.02.00"
room = "2.6.1"
navigation = "2.7.7"
coroutines = "1.8.0"
lifecycle = "2.7.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Lifecycle
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Core
core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.12.0" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.8.2" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "1.9.22-1.0.17" }
```
