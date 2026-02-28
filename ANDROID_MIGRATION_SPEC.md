# OPIC 안드로이드 이식 명세서 v1.0

> 목표: 파이썬(PyQt5) OPIC 학습앱의 **기능 동일 이식** (1차 MVP)
> 기능 변경·UI 리디자인·리팩토링 금지. "동작 동일성 100%" 우선.

---

## 1. 화면별 기능 명세서

### 1.1 StartPage (홈 화면)

| 항목 | 상세 |
|------|------|
| **진입 조건** | 앱 실행 시 / Home 버튼 |
| **표시 요소** | 상단 배너(OPIC_TOP), 레벨 아바타(a1~a10.png, 550×550), 레벨 텍스트("Level N"), 게이지 바(0-100%) |
| **레벨 계산** | `total_xp = Σ(study_count×(study_count+1)/2)`, `mastery = total_xp / (question_count×28)`, `level = floor(sqrt(mastery)×10)+1` (1~10 클램프) |
| **게이지 계산** | `level_start = ((level-1)/10)²`, `level_end = (level/10)²`, `gauge = (mastery-level_start)/(level_end-level_start)` |
| **버튼** | **Study** → StudyScreen, **Next >** → SurveyPage, **Review** → ReviewScreen |
| **갱신 시점** | 화면 진입 시 `_update_start_page_level()` 재계산 |

### 1.2 SurveyPage (배경 설문 - 4파트)

| 파트 | 입력 형태 | 옵션 |
|------|-----------|------|
| Part 1: 직업 | Radio (단일 선택) | 사업/회사, 재택근무, 교사, 일 경험 없음 + 조건부 하위질문 |
| Part 2: 학생 여부 | Radio | 예(현재 수강과목) / 아니요(과거 수강과목) |
| Part 3: 주거 | Radio (단일 선택) | 5가지 주거 유형 |
| Part 4: 취미/활동 | Checkbox (복수 선택) | 4그룹(여가 30+, 취미 12, 스포츠 16, 여행 5) |

| 항목 | 상세 |
|------|------|
| **기본 선택** | Part 4에 DEFAULT_SELECTIONS 12개 항목 프리셋 |
| **상태 저장** | `controller.selected_topics`에 저장 (현재 generate_test_set에 미사용) |
| **버튼** | **< Back** → StartPage, **Next >** → 다음 파트 (마지막 파트에서 SelfAssessmentPage) |

### 1.3 SelfAssessmentPage (난이도 선택)

| 항목 | 상세 |
|------|------|
| **입력** | Radio 6개 (난이도 1~6) |
| **옵션** | 1: 10단어 이하, 2: 기본 물건/색/숫자, 3: 자신/직장/일상, 4: 친숙한 주제 대화, 5: 친숙+미래형 대화, 6: 업무/사회 주제 토론 |
| **[Sample Audio]** | 각 옵션에 있으나 현재 미구현 (Android도 동일하게 미구현) |
| **Validation** | 미선택 시 경고 다이얼로그 |
| **출력** | `controller.selected_difficulty` (1~6) |
| **버튼** | **< Back** → SurveyPage, **Next >** → BeginTestPage |

### 1.4 BeginTestPage (시험 시작 확인)

| 항목 | 상세 |
|------|------|
| **표시** | 안내 이미지(OPIC_STEP5_1_1.png) 단일 |
| **버튼** | **< Back** → SelfAssessmentPage, **Start Test >** → 문제 생성 + TestScreen |

### 1.5 TestScreen (시험 실행) ★핵심

#### 1.5.1 문제 구성

| 난이도 | Q1 | Q2~Q3 | Q4 | Q5~Q7 | Q8~Q15 | 총 문항 |
|--------|-----|-------|-----|-------|--------|---------|
| 1-2 | 자기소개 | 선택×2 | 돌발×1 | RP×1(2문) | - | 12 |
| 3-4 | 자기소개 | 선택×2 | 돌발×1 | RP×1(3문) | Ad×1 | 15 |
| 5-6 | 자기소개 | 선택×1 | 돌발×2 | RP×1(3문) | Ad×1 | 15 |

#### 1.5.2 UI 레이아웃

```
┌─── 상단 ──────────────────────────────────┐
│ "Question X of 15"                        │
├─── 좌측(EVA) ─────┬─── 우측(문제 번호) ───┤
│ EVA 이미지(280×280)│  [1][2][3][4][5]      │
│ 타이머 바(120초)   │  [6][7][8][9][10]     │
│ [▶ Play]           │  [11][12][13][14][15]  │
│ [● Record/■ Stop]  │                       │
│ 마이크 레벨 바     │                       │
├─── 하단 ──────────────────────────────────┤
│ [Home]                          [Next >]   │
└────────────────────────────────────────────┘
```

#### 1.5.3 상태 머신

```
[문제 로드] → Play 활성 / Record 비활성 / Next 비활성
     │
     ▼ Play 클릭 (1회만 허용)
[음성 재생 중] → Play 비활성
     │
     ▼ 재생 완료
[Beep 재생] → 5초 대기
     │
     ▼ 자동 녹음 시작
[녹음 중] → Record(Stop 아이콘) 활성 / 타이머 카운트다운(120초)
     │        마이크 레벨 실시간 표시(100ms 간격)
     │        색상: >60s 초록, 30~60s 주황, <30s 빨강
     ▼ Stop 클릭 또는 120초 만료
[녹음 완료] → WAV 저장(TestRec_{q_id}_{timestamp}.wav)
             → DB 업데이트(Test_Results.user_audio_path)
             → Next 활성
```

#### 1.5.4 녹음 스펙

| 파라미터 | 값 |
|----------|-----|
| 샘플레이트 | 44,100 Hz |
| 채널 | 1 (모노) |
| 비트 | 16-bit signed PCM |
| 포맷 | WAV |
| 최대 시간 | 120초 (자동 종료) |
| 파일명 | `TestRec_{question_id}_{YYYYMMDD_HHMMSS}.wav` |
| 마이크 레벨 | RMS 기반 0~100% (100ms 폴링) |

#### 1.5.5 Play 버튼 동작

1. `question_audio` 파일 존재 → 해당 mp3/wav 재생
2. 파일 없음 + `question_text` 존재 → TTS 생성 후 재생
3. 둘 다 없음 → 알림 표시, 바로 녹음 진행

#### 1.5.6 Home 버튼

- 녹음 중: "Test in progress. Exit?" 확인 다이얼로그
- Yes → 녹음 중단 + 스레드 정리 + StartPage 이동
- No → 복귀

### 1.6 ReviewScreen (결과 리뷰)

| 항목 | 상세 |
|------|------|
| **데이터** | 마지막 Test_Sessions의 Test_Results + Questions JOIN |
| **좌측** | "Question X of 15", EVA 이미지, [▶ Play User Answer], [■ Stop] |
| **우측 상단** | Q Script: [▶ Play][■ Stop][Edit][💾 Save] + 텍스트 영역 |
| **우측 하단** | A Script: [▶ Play][■ Stop][Edit][💾 Save] + 텍스트 영역 |
| **편집 모드** | Edit 클릭 → 텍스트 편집 가능 + Save 버튼 표시, Save → DB 업데이트(Questions.question_text / answer_script), 확인 다이얼로그 |
| **네비게이션** | [< Back], [Home], [1~15 번호], [Next >] |
| **재생** | User Audio: 녹음 WAV 재생, Q/A Audio: 음성 파일 또는 TTS 폴백 |

### 1.7 StudyScreen (학습 모드) ★핵심

#### 1.7.1 상단 컨트롤 패널

| 컨트롤 | 타입 | 동작 |
|--------|------|------|
| Group Play | Button | 선택 목록 순차 재생 시작/정지 |
| Group Mode | Dropdown | 목록 재생 / 질문 재생 / 답변 재생 |
| Problem Filter | Dropdown | Select / 1,2_Level / 3,4_Level / 5,6_Level / Last_Test |
| Set | Dropdown | DB에서 동적 로드, 주제별 필터 |
| Type | Dropdown | 선택 / 돌발 / RP / Ad |
| Sort | Dropdown | 주제 순서 / 오래된 순 |
| Study Filter | Dropdown | 전체 / 📌 / 0~7 (study_count별) |
| 학습완료 | Button | study_count +1, last_modified 갱신 |
| Study Count | Label | 현재 학습 횟수 표시 |
| 📌 Favorite | Toggle | is_favorite 토글 |
| Repeat | Dropdown | 1~10 (반복 횟수) |
| Size | Dropdown | 10~34 (폰트 크기) |
| Title | Dropdown | 필터된 문제 목록 |
| 💫 Reset | Button | 필터 초기화 |
| 전체 화면 | Button | 풀스크린 오버레이 |
| ■ Theme | Button | 다크/라이트 테마 전환 |

#### 1.7.2 좌측 패널

| 기능 | 동작 |
|------|------|
| 데이터 입력 (Set/Type/Combo/Title/Q_Audio/A_Audio) | 텍스트 입력 |
| 삭제 | 현재 문제 DB에서 삭제 |
| 💾 데이터 저장 | 새 문제 DB 삽입 |
| 📥 Import | Excel → DB (전체 테이블) |
| 📤 Export | DB → Excel (전체 테이블) |
| ● Record | 마이크 녹음 시작/정지 |
| YouGlish 검색 | 웹 검색 |
| 번역 | Papago API 번역 |

#### 1.7.3 우측 패널

| 영역 | 기능 |
|------|------|
| Q Script | [▶ Play][■ Stop][Edit][💾 Save] + 텍스트(읽기전용↔편집) |
| A Script | [▶ Play][■ Stop][Edit][💾 Save] + 텍스트(읽기전용↔편집) |
| User Answer | [▶ Play][■ Stop] (녹음한 파일 재생) |
| User Script | [▶ Play][■ Stop][Edit][💾 Save] + STT Load (Questions.user_script) |
| 분석 실행 | Dropdown(STT/Script비교/발음/GPT-4/Gemini) + 결과(HTML) |

#### 1.7.4 상태 유지

| 키 | 저장소 | 값 |
|----|--------|-----|
| font_size | Config.ini → SharedPreferences | 10~34 |
| repeat | Config.ini → SharedPreferences | 1~10 |
| sort | Config.ini → SharedPreferences | 정렬 모드 |
| study_filter | Config.ini → SharedPreferences | 학습 필터 |
| group_play_mode | Config.ini → SharedPreferences | 재생 모드 |
| slot_filter | Config.ini → SharedPreferences | 난이도 슬롯 |
| set, type, title | Config.ini → SharedPreferences | 현재 선택 |

#### 1.7.5 필터 연쇄

```
Problem Filter 변경
  → Set Dropdown 갱신
    → Type Dropdown 갱신
      → Title Dropdown 갱신
        → 현재 문제 로드
```

### 1.8 SpeechScreen (구간 편집)

| 항목 | 상세 |
|------|------|
| **진입** | StudyScreen에서 접근 |
| **테이블 컬럼** | #, Text(O), ●Rec, ▶U, ▶Dual, Start(ms), End(ms), Dur(ms), Text(U) |
| **기능** | 오디오 묵음 기반 자동 분할, 수동 구간 생성, 구간별 녹음+STT 비교, 이중 재생(원본+사용자) |

---

## 2. DB 스키마 이식 계획

### 2.1 테이블 정의 (SQLite → Android Room 또는 직접 SQLite)

#### Users
```sql
CREATE TABLE Users (
  user_id    INTEGER PRIMARY KEY,
  username   TEXT UNIQUE NOT NULL
);
-- 초기 데이터: (1, 'default_user')
```

#### Questions ★
```sql
CREATE TABLE Questions (
  question_id    INTEGER PRIMARY KEY,
  title          TEXT UNIQUE NOT NULL,
  "set"          TEXT,            -- 주제 (Music, Movie 등)
  type           TEXT,            -- 선택/돌발/RP/Ad
  combo          TEXT,            -- 콤보 ID (01, 02 등)
  question_text  TEXT,            -- 질문 텍스트
  answer_script  TEXT,            -- 모범 답안 스크립트
  question_audio TEXT,            -- 질문 오디오 링크명
  answer_audio   TEXT,            -- 답변 오디오 링크명
  user_script    TEXT             -- 사용자 커스텀 스크립트
);
```

#### Question_Slots
```sql
CREATE TABLE Question_Slots (
  slot_id      INTEGER PRIMARY KEY,
  question_id  INTEGER,
  slot_number  INTEGER,
  FOREIGN KEY(question_id) REFERENCES Questions(question_id)
);
```

#### User_Study_Progress ★
```sql
CREATE TABLE User_Study_Progress (
  progress_id     INTEGER PRIMARY KEY,
  user_id         INTEGER NOT NULL,
  question_id     INTEGER NOT NULL,
  study_count     INTEGER DEFAULT 0,   -- 0~7 (7일마다 -1 감소)
  last_modified   DATETIME,
  is_favorite     INTEGER DEFAULT 0,   -- 0/1
  stt_text        TEXT,                -- STT 결과
  analysis_result TEXT,                -- AI 분석 결과(HTML)
  FOREIGN KEY(user_id) REFERENCES Users(user_id),
  FOREIGN KEY(question_id) REFERENCES Questions(question_id),
  UNIQUE(user_id, question_id)
);
```

#### Test_Sessions
```sql
CREATE TABLE Test_Sessions (
  session_id  INTEGER PRIMARY KEY,
  user_id     INTEGER,
  timestamp   DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES Users(user_id)
);
```

#### Test_Results ★
```sql
CREATE TABLE Test_Results (
  result_id        INTEGER PRIMARY KEY,
  session_id       INTEGER,
  question_id      INTEGER,
  question_number  INTEGER,          -- 1~15
  user_audio_path  TEXT,             -- 녹음 WAV 경로
  similarity_score REAL,             -- 미사용 (placeholder)
  stt_result       TEXT,             -- STT 결과 (미사용)
  FOREIGN KEY(session_id) REFERENCES Test_Sessions(session_id),
  FOREIGN KEY(question_id) REFERENCES Questions(question_id)
);
```

#### Api_Keys
```sql
CREATE TABLE Api_Keys (
  service_name TEXT PRIMARY KEY,
  api_key      TEXT NOT NULL
);
-- 서비스: google_speech_key_path, openai_gpt, gemini_api
```

### 2.2 마이그레이션 전략

| 단계 | 작업 | 방법 |
|------|------|------|
| M1 | 스키마 생성 | SQLiteOpenHelper.onCreate() 또는 Room @Database |
| M2 | 초기 데이터 | assets/opic.db를 앱 내부 저장소에 복사 (pre-populated DB) |
| M3 | 오디오 에셋 | Sound/*.mp3를 assets/Sound/로 번들 |
| M4 | 이미지 에셋 | Image/*.png를 drawable 또는 assets/Image/로 번들 |
| M5 | 런타임 생성 | Recording/ 폴더는 Context.getExternalFilesDir() 하위에 동적 생성 |

### 2.3 Android 경로 매핑

| Python 경로 | Android 경로 |
|-------------|-------------|
| `./opic.db` | `Context.getDatabasePath("opic.db")` |
| `./Image/` | `assets/Image/` 또는 `res/drawable/` |
| `./Sound/` | `assets/Sound/` |
| `./Recording/` | `Context.getExternalFilesDir("Recording")` |
| `./Recording/temp/` | `Context.getCacheDir()` |
| `Config.ini` | `SharedPreferences("opic_config")` |

### 2.4 Room Entity 매핑 (선택: 직접 SQLite도 가능)

```
@Entity(tableName = "Questions")
data class Question(
  @PrimaryKey val question_id: Int,
  @ColumnInfo(name = "title") val title: String,
  @ColumnInfo(name = "set") val set: String?,
  ...
)
```

> **주의**: `"set"` 컬럼은 SQL 예약어 충돌 가능 → Room 사용 시 @ColumnInfo(name = "\"set\"") 또는 backtick 처리 필요. 직접 SQLite라면 이미 큰따옴표로 처리됨.

---

## 3. 오디오 파이프라인 설계

### 3.1 녹음 (Recording)

#### Python 현재
```
sounddevice.InputStream → float32 chunks → int16 변환 → scipy WAV 저장
```

#### Android 이식

| 항목 | 구현 |
|------|------|
| **API** | `AudioRecord` (저수준, PCM 직접 제어) |
| **샘플레이트** | 44,100 Hz |
| **채널** | `AudioFormat.CHANNEL_IN_MONO` |
| **인코딩** | `AudioFormat.ENCODING_PCM_16BIT` |
| **저장 포맷** | WAV (PCM 데이터 + WAV 헤더 직접 작성) |
| **RMS 계산** | 100ms 버퍼마다 RMS 산출 → UI 레벨바 갱신 |
| **스레드** | `HandlerThread` 또는 Kotlin Coroutine (IO dispatcher) |
| **타이머** | `CountDownTimer(120000, 1000)` → 자동 종료 |
| **퍼미션** | `RECORD_AUDIO` (런타임 퍼미션 필수) |

```kotlin
// 의사 코드
val bufferSize = AudioRecord.getMinBufferSize(44100, MONO, PCM_16BIT)
val recorder = AudioRecord(MIC, 44100, MONO, PCM_16BIT, bufferSize)
recorder.startRecording()

// 코루틴에서
while (isRecording) {
    val read = recorder.read(buffer, 0, buffer.size)
    outputStream.write(buffer, 0, read)
    val rms = calculateRMS(buffer, read)
    withContext(Dispatchers.Main) { micLevelBar.progress = rms }
}
recorder.stop()
prependWavHeader(outputFile)
```

### 3.2 재생 (Playback)

#### Python 현재
```
pygame.mixer.music.load() → play() → 100ms 폴링(get_busy) → unload()
```

#### Android 이식

| 항목 | 구현 |
|------|------|
| **API** | `MediaPlayer` |
| **assets 재생** | `AssetFileDescriptor` → `MediaPlayer.setDataSource(afd)` |
| **WAV 재생** | `MediaPlayer.setDataSource(filePath)` |
| **완료 콜백** | `MediaPlayer.OnCompletionListener` (폴링 불필요) |
| **연쇄 재생** | 완료 리스너에서 다음 액션 트리거 (Beep → 녹음 시작) |

```kotlin
val player = MediaPlayer()
player.setDataSource(path)
player.prepare()
player.setOnCompletionListener {
    player.release()
    onPlaybackFinished()  // Beep → 자동 녹음 트리거
}
player.start()
```

#### 오디오 파일 검색 로직 (find_audio_file 이식)

```kotlin
fun findAudioFile(linkName: String): AssetFileDescriptor? {
    for (ext in listOf(".mp3", ".wav")) {
        try {
            return assets.openFd("Sound/$linkName$ext")
        } catch (e: IOException) { continue }
    }
    return null
}
```

### 3.3 TTS (Text-to-Speech)

#### Python 현재
```
pyttsx3 (오프라인) → WAV 파일 생성 → pygame 재생
```

#### Android 이식

| 항목 | 구현 |
|------|------|
| **API** | `android.speech.tts.TextToSpeech` |
| **언어** | `Locale.US` |
| **파일 저장** | `synthesizeToFile(text, params, file)` → 캐시 디렉토리 |
| **직접 재생** | `speak(text, QUEUE_FLUSH, params, utteranceId)` |
| **캐시** | SHA1(text) 해시 기반 파일명 → 기존 파일 있으면 재생만 |
| **콜백** | `UtteranceProgressListener.onDone()` |

```kotlin
tts = TextToSpeech(context) { status ->
    if (status == SUCCESS) {
        tts.language = Locale.US
        tts.setSpeechRate(0.9f)  // Python rate=150 ≈ 0.9x
    }
}

// 캐시 기반 재생
val hash = text.sha1()
val cacheFile = File(cacheDir, "tts_$hash.wav")
if (cacheFile.exists()) {
    playWithMediaPlayer(cacheFile)
} else {
    tts.synthesizeToFile(text, null, cacheFile, hash)
    // UtteranceProgressListener.onDone → playWithMediaPlayer(cacheFile)
}
```

### 3.4 STT (Speech-to-Text)

#### Python 현재
```
SpeechRecognition → Google Speech API (recognize_google, en-US)
```

#### Android 이식

| 항목 | 구현 |
|------|------|
| **방법 A** | `SpeechRecognizer` (Android 내장, 실시간) |
| **방법 B** | Google Cloud Speech-to-Text REST API (파일 기반, Python과 동일) |
| **추천** | 방법 B (파일 기반 → Python 동작과 동일) |
| **언어** | `en-US` |
| **입력** | 녹음된 WAV 파일 |
| **출력** | 텍스트 → User_Study_Progress.stt_text |

```kotlin
// 방법 B: 파일 기반 STT (Python과 동일 동작)
suspend fun runSTT(audioFile: File): String {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    // ... 또는 Google Cloud Speech API HTTP 호출
    // 결과를 DB에 저장
    return sttText
}
```

### 3.5 재생 흐름 요약 (TestScreen)

```
Play 클릭
  ├─ 오디오 파일 존재 → MediaPlayer 재생
  ├─ 파일 없음 + 텍스트 있음 → TTS 생성 → 재생
  └─ 둘 다 없음 → 알림 + 바로 녹음
          │
          ▼ OnCompletionListener
     Beep 재생 (beep.wav)
          │
          ▼ OnCompletionListener
     5초 대기 (Handler.postDelayed)
          │
          ▼
     자동 녹음 시작 (AudioRecord)
          │
          ▼ 120초 만료 또는 Stop 클릭
     녹음 종료 → WAV 저장 → DB 업데이트 → Next 활성
```

---

## 4. Export/Import 설계

### 4.1 Export (DB → Excel/JSON)

#### Python 현재
```python
export_all_tables_to_excel(filepath)
# 모든 테이블 → pandas DataFrame → 멀티시트 xlsx
```

#### Android 이식

| 항목 | 상세 |
|------|------|
| **포맷** | JSON (1차 MVP, 라이브러리 의존성 없음) + Excel 옵션(Apache POI) |
| **범위** | 7개 전체 테이블 |
| **저장 위치** | `Environment.getExternalStoragePublicDirectory(DOWNLOADS)` 또는 SAF |
| **파일명** | `opic_backup_{yyyyMMdd_HHmmss}.json` |
| **구조** | 테이블명을 key, 행 배열을 value로 하는 JSON |

```json
{
  "Users": [{"user_id":1, "username":"default_user"}],
  "Questions": [{...}, {...}],
  "User_Study_Progress": [{...}],
  "Test_Sessions": [{...}],
  "Test_Results": [{...}],
  "Api_Keys": [{...}],
  "Question_Slots": [{...}]
}
```

#### Excel 옵션 (Python 호환)
```
Apache POI 라이브러리 사용 → .xlsx 생성
시트별 테이블 매핑 (Python과 동일 구조)
→ Python 앱과 양방향 Excel 호환 가능
```

### 4.2 Import (Excel/JSON → DB)

#### Python 현재
```python
import_all_tables_from_excel(filepath)
# 각 시트 → 기존 데이터 DELETE → INSERT
# Questions 중복 title → "ov_" prefix 처리
```

#### Android 이식

| 단계 | 작업 |
|------|------|
| 1 | 파일 선택 (SAF `ACTION_OPEN_DOCUMENT`) |
| 2 | JSON/Excel 파싱 |
| 3 | 트랜잭션 시작 |
| 4 | 각 테이블: DELETE → INSERT (Python 동일) |
| 5 | Questions 중복 해결: `ov_`, `ov_2_`, `ov_3_` prefix |
| 6 | 커밋 또는 롤백 |
| 7 | 결과 요약 다이얼로그 표시 |

### 4.3 초보용 안전안

| 위험 | 대응 |
|------|------|
| Import 중 기존 데이터 전체 삭제 | ⚠️ "현재 데이터가 모두 삭제됩니다. 계속하시겠습니까?" 확인 |
| Import 실패 시 데이터 손실 | **자동 백업**: Import 전 현재 DB를 `opic_pre_import_{timestamp}.db`로 복사 |
| 잘못된 파일 선택 | 파일 확장자 + 내용 유효성 검사 (JSON 키 / Excel 시트명 확인) |
| 중간 실패 | 트랜잭션 롤백 → "Import 실패. 데이터가 복원되었습니다." |
| 녹음 파일 유실 | Export에 Recording 폴더 포함 옵션 (ZIP 번들) |

### 4.4 Import 안전 흐름

```
[Import 클릭]
  → "⚠️ 기존 데이터가 삭제됩니다. 먼저 백업하시겠습니까?"
     ├─ [백업 후 Import] → 자동 Export 실행 → Import 진행
     ├─ [바로 Import] → Import 진행
     └─ [취소] → 복귀

[Import 진행]
  → 자동 백업 (opic_pre_import.db 복사)
  → BEGIN TRANSACTION
  → 파싱 + DELETE + INSERT
  → 성공: COMMIT + 결과 요약
  → 실패: ROLLBACK + 오류 메시지 + "백업에서 복원하시겠습니까?"
```

---

## 5. 개발 티켓 (18개, 우선순위 포함)

### 범례
- **P0**: 블로커 (이것 없이 앱 불가)
- **P1**: 핵심 (기본 흐름 필수)
- **P2**: 주요 기능
- **P3**: 부가 기능

---

#### T-01 [P0] 프로젝트 초기 셋업

| 항목 | 상세 |
|------|------|
| **설명** | Android Studio 프로젝트 생성 (Kotlin, minSdk 26), Gradle 의존성(Room/Coroutines/MediaPlayer), 폴더 구조, assets(Sound/Image/opic.db) 번들링 |
| **완료 기준** | 빌드 성공 + 에뮬레이터에서 빈 화면 앱 실행 + assets 접근 확인 |
| **테스트** | `adb shell ls /data/data/{pkg}/databases/` → opic.db 존재 |

---

#### T-02 [P0] DB 레이어 구현

| 항목 | 상세 |
|------|------|
| **설명** | 7개 테이블 스키마 생성 (SQLiteOpenHelper 또는 Room), pre-populated DB 복사 로직, `apply_study_decay()` 구현, DB 헬퍼 함수(CRUD) |
| **완료 기준** | 앱 최초 실행 시 opic.db 복사 완료, Questions 테이블 SELECT 정상, study_decay 7일 규칙 동작 |
| **테스트** | 단위 테스트: study_count=5 + last_modified=14일 전 → decay 후 3 확인 |

---

#### T-03 [P0] 네비게이션 프레임워크

| 항목 | 상세 |
|------|------|
| **설명** | Navigation Component 또는 FragmentManager 기반 화면 전환, 8개 화면(Fragment) 스켈레톤, Back 버튼 처리, show_frame() 상당 로직 |
| **완료 기준** | StartPage → Survey → SelfAssessment → BeginTest → Test → Review 순방향/역방향 전환 성공 |
| **테스트** | 모든 화면 진입/이탈 시 크래시 없음, Back 키 동작 확인 |

---

#### T-04 [P0] StartPage 구현

| 항목 | 상세 |
|------|------|
| **설명** | 배너 이미지, 레벨 아바타(a1~a10), 레벨 텍스트, 게이지 바, 3개 버튼(Study/Next/Review), 레벨 계산 로직 이식 |
| **완료 기준** | DB 기반 레벨(1~10) 정확 계산 + 표시, 게이지 % 정확, 3버튼 네비게이션 |
| **테스트** | study_count 조합 5가지 → 예상 레벨/게이지 일치 확인 |

---

#### T-05 [P1] SurveyPage 구현

| 항목 | 상세 |
|------|------|
| **설명** | 4파트 설문 UI (Radio + Checkbox), 파트 간 이동(Back/Next), Part 4 DEFAULT_SELECTIONS 프리셋, 조건부 하위질문 표시 |
| **완료 기준** | 4파트 모두 정상 표시, 선택 상태 유지, 조건부 UI 동작, selected_topics 저장 |
| **테스트** | Part 1 "교사" 선택 → 하위질문 표시 확인, Part 4 기본 12개 체크 확인 |

---

#### T-06 [P1] SelfAssessmentPage + BeginTestPage 구현

| 항목 | 상세 |
|------|------|
| **설명** | 난이도 1~6 Radio 선택, 미선택 Validation, BeginTest 안내 이미지, selected_difficulty 저장 |
| **완료 기준** | 미선택 시 경고, 선택 후 Next → BeginTest → Start Test 흐름 완성 |
| **테스트** | 미선택 Next 클릭 → 경고 표시, 선택 후 → 정상 진행 |

---

#### T-07 [P0] QuestionGenerator 이식

| 항목 | 상세 |
|------|------|
| **설명** | 난이도(1~6)별 문제 세트 생성 로직 100% 이식: 선택/돌발/RP/Ad 배분, 콤보 랜덤 선택, used_sets/used_combo_keys 중복 방지, Self Introduction 항상 Q1, Validation 실패 처리 |
| **완료 기준** | 난이도 1→12문항, 3→15문항, 5→15문항 정확 생성, 중복 콤보 없음 |
| **테스트** | 단위 테스트 6개(난이도별): 문항수, 타입 비율, Self-Intro Q1 위치, 중복 없음 확인 |

---

#### T-08 [P0] 오디오 재생 엔진

| 항목 | 상세 |
|------|------|
| **설명** | MediaPlayer 래퍼 클래스, assets/Sound 재생, WAV 파일 재생, OnCompletionListener, 오디오 파일 검색(mp3/wav 폴백), 재생 중 UI 상태 제어 |
| **완료 기준** | mp3(assets) 재생 성공, wav(Recording/) 재생 성공, 재생 완료 콜백 정상 |
| **테스트** | 3개 파일 재생 → 완료 콜백 확인, 없는 파일 → 에러 핸들링 확인 |

---

#### T-09 [P0] 녹음 엔진

| 항목 | 상세 |
|------|------|
| **설명** | AudioRecord 래퍼 (44.1kHz, mono, PCM16), WAV 헤더 작성, RMS 마이크 레벨(100ms), 120초 자동종료, `RECORD_AUDIO` 런타임 퍼미션 |
| **완료 기준** | 녹음 → WAV 저장 → 재생 확인, 120초 자동종료, RMS 0~100% UI 반영 |
| **테스트** | 10초 녹음 → 파일 크기 ≈ 882KB(44100×2×10) 확인, 120초 만료 테스트 |

---

#### T-10 [P0] TestScreen 구현

| 항목 | 상세 |
|------|------|
| **설명** | 전체 UI 레이아웃(EVA/타이머/Play/Record/번호/Next/Home), 상태 머신(Play→Beep→녹음→완료), Play 1회 제한, 120초 카운트다운(색상 변경), 번호 점프, Home 확인 다이얼로그, 문제 데이터 로드, 녹음 DB 저장 |
| **완료 기준** | 15문제 순차 진행 + 녹음 15개 저장 + ReviewScreen 자동 전환 |
| **테스트** | 전체 플로우 E2E: 난이도 3 선택 → 15문제 → 각 녹음 → Review 진입 확인 |

---

#### T-11 [P1] ReviewScreen 구현

| 항목 | 상세 |
|------|------|
| **설명** | 마지막 세션 결과 로드(Test_Sessions + Test_Results + Questions JOIN), User Audio 재생, Q/A Script 재생(음성 또는 TTS), 스크립트 편집/저장(Questions 테이블), 번호 네비게이션 |
| **완료 기준** | 15문제 결과 표시, User Audio 재생, 스크립트 편집 → DB 반영 확인 |
| **테스트** | 편집 후 앱 재시작 → 저장된 스크립트 유지 확인 |

---

#### T-12 [P1] TTS 엔진

| 항목 | 상세 |
|------|------|
| **설명** | Android TextToSpeech 래퍼, Locale.US, SHA1 캐시, synthesizeToFile + 재생, UtteranceProgressListener, TTS 폴백 로직(음성파일 없을 때) |
| **완료 기준** | 텍스트 → 음성 재생, 캐시 히트 시 재생성 안 함, 오디오 없는 문제에서 TTS 폴백 |
| **테스트** | 같은 텍스트 2회 → 캐시 파일 재사용 확인, TTS 폴백 테스트 |

---

#### T-13 [P1] StudyScreen 구현 - 기본

| 항목 | 상세 |
|------|------|
| **설명** | 필터 체인(Problem/Set/Type/Sort/Study→Title), 문제 로드 + Q/A Script 표시, 재생(Q Audio/A Audio/User Audio), 학습완료(study_count+1), 즐겨찾기 토글, 상태 유지(SharedPreferences) |
| **완료 기준** | 필터 변경 → Title 갱신, 학습완료 → DB 반영, 앱 재시작 후 필터 복원 |
| **테스트** | 필터 조합 5가지 → 예상 목록 일치, 학습완료 3회 → study_count=3 확인 |

---

#### T-14 [P2] StudyScreen 구현 - 편집/녹음

| 항목 | 상세 |
|------|------|
| **설명** | Q/A/User Script 편집 모드(읽기전용↔편집), Save → DB, 녹음(AudioRecord) + 재생, User Script(Questions.user_script) 저장, 테마 전환(다크/라이트), 폰트 크기 조절 |
| **완료 기준** | 스크립트 편집 → 저장 → 재로드 유지, 녹음 + 재생, 테마/폰트 전환 |
| **테스트** | 편집→저장→화면 이탈→복귀 → 저장 유지, 다크 테마 전환 시 UI 깨짐 없음 |

---

#### T-15 [P2] StudyScreen 구현 - Group Play

| 항목 | 상세 |
|------|------|
| **설명** | 그룹 재생 3모드(목록/질문/답변), 반복 횟수(1~10), 순차 재생 제어(시작/정지), 현재 재생 문제 하이라이트, 전체 화면 오버레이 |
| **완료 기준** | 목록 재생 → 모든 문제 순차 재생 완료, 반복 3회 설정 시 3회 반복 |
| **테스트** | 5문제 목록 × 반복 2 → 총 10회 재생 확인, 중간 정지 후 재개 |

---

#### T-16 [P2] StudyScreen 구현 - 데이터 관리 (CRUD)

| 항목 | 상세 |
|------|------|
| **설명** | 좌측 패널 데이터 입력(Set/Type/Combo/Title/Q_Audio/A_Audio), 새 문제 DB 삽입(💾), 문제 삭제(삭제 버튼), YouGlish 검색(웹뷰/외부 브라우저), 번역(Papago API) |
| **완료 기준** | 새 문제 추가 → DB 반영 → 필터에 표시, 삭제 → DB 제거 |
| **테스트** | 추가 → 삭제 → 재추가 사이클 정상, 중복 title 방지 확인 |

---

#### T-17 [P2] Export/Import 구현

| 항목 | 상세 |
|------|------|
| **설명** | Export: 7테이블 → JSON (+ Excel 옵션), Import: JSON/Excel → DB, 중복 title 처리(ov_ prefix), 안전안(자동 백업 + 확인 다이얼로그 + 트랜잭션 롤백), 결과 요약 표시 |
| **완료 기준** | Export → Import → 데이터 동일 확인, 중복 title ov_ 처리, 실패 시 롤백 |
| **테스트** | 라운드트립: Export → DB 삭제 → Import → 행 수 일치, 고의 오류 → 롤백 확인 |

---

#### T-18 [P2] STT + 분석 기능

| 항목 | 상세 |
|------|------|
| **설명** | STT(Google Speech API, en-US), Script 비교(difflib 이식: 삽입=녹색/삭제=빨강/대치=노랑), STT Load(→ user_script), 분석 결과 HTML 표시, analysis_result DB 저장 |
| **완료 기준** | 녹음 → STT → 텍스트 정확, Script 비교 HTML 색상 정확 |
| **테스트** | 알려진 문장 녹음 → STT 결과 확인, 비교 결과 색상 확인 |

---

#### T-19 [P3] GPT-4 / Gemini AI 분석

| 항목 | 상세 |
|------|------|
| **설명** | GPT-4 Grammar 교정(OpenAI API), Gemini Script 생성(Gemini API), API Key 관리(Api_Keys 테이블), 네트워크 오류 처리 |
| **완료 기준** | API Key 설정 → 분석 요청 → 결과 표시, 키 미설정 시 안내 메시지 |
| **테스트** | 유효 키 → 결과 반환, 무효 키 → 에러 메시지, 네트워크 없음 → 타임아웃 처리 |

---

#### T-20 [P3] SpeechScreen 구간 편집

| 항목 | 상세 |
|------|------|
| **설명** | 묵음 기반 자동 구간 분할, 구간별 테이블(#/Text(O)/●Rec/▶U/▶Dual/Start/End/Dur/Text(U)), 구간 녹음 + STT 비교, 이중 재생(원본+사용자) |
| **완료 기준** | 오디오 → 자동 분할 → 구간 표시, 구간 녹음 → 이중 재생 |
| **테스트** | 30초 오디오 → 최소 3개 구간 분할, 구간 녹음 → 재생 확인 |

---

### 티켓 의존성 맵

```
T-01 ──→ T-02 ──→ T-07
  │         │
  │         ├──→ T-04
  │         │
  ├──→ T-03 ├──→ T-05
  │    │    │
  │    │    ├──→ T-06
  │    │
  │    ├──→ T-10 ──→ T-11
  │
  ├──→ T-08 ──→ T-12
  │
  ├──→ T-09
  │
  │    T-08+T-09+T-07 ──→ T-10
  │
  │    T-10 ──→ T-11
  │
  │    T-08+T-09+T-02 ──→ T-13 ──→ T-14 ──→ T-15
  │                                   │
  │                                   ├──→ T-16
  │                                   │
  │                        T-02 ──→ T-17
  │
  │    T-09+T-13 ──→ T-18 ──→ T-19
  │
  │    T-08+T-09+T-18 ──→ T-20
```

### 개발 순서 요약 (스프린트 제안)

| 스프린트 | 티켓 | 목표 |
|----------|------|------|
| Sprint 1 (2주) | T-01, T-02, T-03, T-04 | 앱 뼈대 + DB + 홈 화면 |
| Sprint 2 (2주) | T-08, T-09, T-07, T-06 | 오디오 엔진 + 문제 생성 |
| Sprint 3 (2주) | T-05, T-10 | 설문 + 시험 화면 (핵심 플로우) |
| Sprint 4 (2주) | T-11, T-12, T-13 | 리뷰 + TTS + 학습 기본 |
| Sprint 5 (2주) | T-14, T-15, T-16 | 학습 완성 |
| Sprint 6 (2주) | T-17, T-18 | Export/Import + STT분석 |
| Sprint 7 (1주) | T-19, T-20 | AI 분석 + 구간 편집 |

---

## 6. 기능 동일성 회귀 테스트 체크리스트 (25개)

### 시험 플로우 (TC-01 ~ TC-08)

| ID | 테스트 항목 | 검증 방법 | Pass 기준 |
|----|-----------|-----------|-----------|
| **TC-01** | 난이도 1~2 문제 생성 | 난이도 1 선택 → 문제 세트 확인 | 12문항, Q1=Self-Intro, 선택×2+돌발×1+RP×1(2문) |
| **TC-02** | 난이도 3~4 문제 생성 | 난이도 3 선택 → 문제 세트 확인 | 15문항, 선택×2+돌발×1+RP×1(3문)+Ad×1 |
| **TC-03** | 난이도 5~6 문제 생성 | 난이도 5 선택 → 문제 세트 확인 | 15문항, 선택×1+돌발×2+RP×1(3문)+Ad×1 |
| **TC-04** | 콤보 중복 방지 | 동일 난이도 시험 3회 반복 | 매회 used_combo_keys에 중복 없음 |
| **TC-05** | Play 1회 제한 | Q 재생 후 Play 재클릭 | 재생 안 됨 (버튼 무시) |
| **TC-06** | 자동 녹음 시작 | Q 재생 완료 후 대기 | Beep → 5초 후 자동 녹음 시작 |
| **TC-07** | 120초 자동 종료 | 녹음 시작 후 2분 대기 | 정확히 120초에 녹음 자동 정지 + WAV 저장 |
| **TC-08** | Home 확인 다이얼로그 | 녹음 중 Home 클릭 | "Exit?" 다이얼로그 표시, No → 복귀, Yes → 홈 |

### 녹음/재생 (TC-09 ~ TC-12)

| ID | 테스트 항목 | 검증 방법 | Pass 기준 |
|----|-----------|-----------|-----------|
| **TC-09** | WAV 녹음 스펙 | 녹음 파일 속성 확인 | 44.1kHz, mono, 16-bit PCM, WAV |
| **TC-10** | 녹음 파일 DB 저장 | 녹음 완료 후 DB 확인 | Test_Results.user_audio_path에 절대경로 존재 |
| **TC-11** | User Audio 재생 | ReviewScreen에서 ▶ 클릭 | 녹음한 내용 재생됨 |
| **TC-12** | TTS 폴백 | 오디오 파일 없는 문제 재생 | TTS 음성 생성 + 재생 성공 |

### 학습 기능 (TC-13 ~ TC-19)

| ID | 테스트 항목 | 검증 방법 | Pass 기준 |
|----|-----------|-----------|-----------|
| **TC-13** | 필터 연쇄 | Set → Type → Title 변경 | 하위 Dropdown 올바르게 갱신 |
| **TC-14** | 학습완료 카운트 | 학습완료 3회 클릭 | study_count=3, last_modified 갱신 |
| **TC-15** | study_count 감쇄 | study_count=5, last_modified=14일 전 설정 후 앱 재시작 | study_count=3 (14÷7=2 감쇄) |
| **TC-16** | 즐겨찾기 토글 | 📌 클릭 2회 | 1회: is_favorite=1, 2회: is_favorite=0 |
| **TC-17** | 스크립트 편집 저장 | A Script 편집 → Save → 화면 이탈 → 복귀 | 저장된 텍스트 유지됨 |
| **TC-18** | 그룹 재생 | 5문제 목록 재생 모드 | 5개 순차 재생 완료 |
| **TC-19** | 상태 유지 | 필터/폰트/반복 설정 → 앱 재시작 | SharedPreferences에서 복원됨 |

### 레벨 계산 (TC-20 ~ TC-21)

| ID | 테스트 항목 | 검증 방법 | Pass 기준 |
|----|-----------|-----------|-----------|
| **TC-20** | 레벨 계산 정확도 | 알려진 study_count 세트 입력 | Python과 동일 레벨 값 |
| **TC-21** | 게이지 % 정확도 | 레벨 경계값 테스트 | Python과 동일 게이지 % |

### Export/Import (TC-22 ~ TC-24)

| ID | 테스트 항목 | 검증 방법 | Pass 기준 |
|----|-----------|-----------|-----------|
| **TC-22** | 라운드트립 | Export → Import → 비교 | 7테이블 모든 행·컬럼 일치 |
| **TC-23** | 중복 title 처리 | 같은 파일 2회 Import | ov_ prefix 적용, 데이터 유실 없음 |
| **TC-24** | Import 실패 롤백 | 의도적 오류 파일 Import | 트랜잭션 롤백, 기존 데이터 보존 |

### 기타 (TC-25)

| ID | 테스트 항목 | 검증 방법 | Pass 기준 |
|----|-----------|-----------|-----------|
| **TC-25** | 전체 E2E | Start→Survey→Assessment→BeginTest→Test(15Q)→Review→Study→Home | 전 화면 크래시 없이 완주, 모든 DB 반영 확인 |

---

## 부록 A: Android 기술 스택 권장

| 영역 | 추천 기술 | 이유 |
|------|----------|------|
| 언어 | Kotlin | 공식 추천, 코루틴 지원 |
| UI | XML Layout + Fragment | 1차 MVP (Compose는 2차) |
| DB | Room (SQLite 래퍼) 또는 직접 SQLiteOpenHelper | Room 추천 (타입 안전) |
| 비동기 | Kotlin Coroutines | QThread 대체 |
| 오디오 녹음 | AudioRecord | 저수준 PCM 제어 (sounddevice 대체) |
| 오디오 재생 | MediaPlayer | pygame.mixer 대체 |
| TTS | android.speech.tts.TextToSpeech | pyttsx3 대체 (오프라인) |
| STT | Google Speech API (REST) | SpeechRecognition 대체 |
| 네트워크 | Retrofit + OkHttp | API 호출 (GPT-4, Gemini) |
| 설정 저장 | SharedPreferences | Config.ini 대체 |
| 파일 선택 | Storage Access Framework (SAF) | Import/Export 파일 선택 |
| Excel | Apache POI (poi-ooxml) | pandas openpyxl 대체 |
| JSON | kotlinx.serialization 또는 Gson | Export/Import |
| 의존성 주입 | Hilt (선택) | 테스트 용이성 |
| minSdk | 26 (Android 8.0) | AudioRecord API 안정성 |

## 부록 B: 퍼미션 목록

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

> Android 10+ (Scoped Storage): `READ_EXTERNAL_STORAGE` 대신 SAF 사용
> `RECORD_AUDIO`는 런타임 퍼미션 필수 (앱 시작 시 또는 최초 녹음 시)

## 부록 C: 파일 매핑 요약

| Python 파일 | Android 대응 |
|-------------|-------------|
| Main.py | MainActivity.kt |
| core/config.py | AppConfig.kt (object) + SharedPreferences |
| core/database.py | DatabaseHelper.kt (Room @Database 또는 SQLiteOpenHelper) |
| core/helpers.py | QuestionGenerator.kt + Worker → Coroutine |
| core/analysis_tools.py | AnalysisTools.kt (STT/TTS/API 래퍼) |
| ui/main_window.py | MainActivity.kt + NavController |
| ui/test_screen.py | TestFragment.kt |
| ui/study_screen.py | StudyFragment.kt |
| ui/review_screen.py | ReviewFragment.kt |
| ui/speech_screen.py | SpeechFragment.kt |
