# OPIc Android — 세션 핸드오프

> 작성일: 2026-02-25
> 빌드 상태: **BUILD SUCCESSFUL** (경고만 존재, 오류 없음)

---

## 1. 완료된 작업 (6개 기능 전부 구현)

### Feature 1 — 오디오 시스템: 다운로드 방식 → SAF 로컬 폴더 방식 ✅
- `util/AudioFileResolver.kt` 신규 생성 (`@Singleton`)
  - `saveFolder(uri)`: SAF로 선택한 폴더 URI를 `SharedPreferences("opic_prefs", "audio_folder_uri")`에 저장
  - `buildCache()`: `DocumentFile.listFiles()`로 전체 파일명→URI 맵 캐싱 (O(n) 스캔 1회로 끝냄)
  - `resolve(filename)`: 확장자 없으면 `.mp3→.wav→.m4a` 순 탐색
- `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` — "오디오 파일 폴더 선택" UI 추가
- `data/repository/QuestionRepository.kt` — `AudioPackDownloader` 제거, `AudioFileResolver` 연동
- `di/AppModule.kt` — `OkHttpClient` 제거, `FlashcardDao` 바인딩 추가, `MIGRATION_1_2` 등록
- `gradle/libs.versions.toml` + `app/build.gradle.kts` — `androidx.documentfile:1.0.1` 의존성 추가

### Feature 2 — 학습 화면: 내 녹음 재생 버튼 ✅
- `util/AudioRecorderManager.kt` — `startRecording(outputDir, outputFileName?)` 파라미터 추가
- `ui/study/StudyDetailScreen.kt` — 녹음 시 `recordings/{questionId}.m4a` 고정 경로 저장, 완료 후 "내 답변 듣기" `OutlinedButton` 표시
- `ui/study/StudyViewModel.kt` — `@ApplicationContext` 주입, `recordingPath` StateFlow, `loadQuestion()` 시 기존 파일 존재 여부 체크

### Feature 3 — 학습 목록: 주제(Set) 필터 추가 ✅
- `data/local/dao/QuestionDao.kt` — `getAllSets()`, `getFilteredQuestions(type?, set?)` 쿼리 추가
- `ui/study/StudyViewModel.kt` — `FilterState` 데이터 클래스 + `_combinedFilter = combine(_filterState, _typeFilter, _setFilter)` (5-flow 한계 우회)
- `ui/study/StudyListScreen.kt` — Row 1: 전체/즐겨찾기/유형 FilterChip, Row 2: 주제 FilterChip (가로 스크롤 `LazyRow`)

### Feature 4 — 시험 주제 선택 (기존 설문 대체) ✅
- `ui/test/TopicSelectionState.kt` — `@Singleton class TopicSelectionState` (두 ViewModel 간 상태 공유)
- `ui/test/TopicSelectionScreen.kt` + `TopicSelectionViewModel.kt` 신규 생성
  - `Question.set` 고유값 체크박스 다중선택, "전체선택/해제", "선택 완료 (N개 주제)" 버튼
- `domain/usecase/GenerateTestUseCase.kt` — `invoke(difficulty, selectedTopics)` 파라미터 추가, 주제 필터 적용
- `ui/test/TestViewModel.kt` — `TopicSelectionState` 주입, `startTest()` 시 선택 주제 전달
- `MainActivity.kt` — `survey` 라우트 제거, `topic_selection` 라우트 추가

### Feature 5 — 복습 화면: 오디오 재생 버튼 ✅
- `ui/review/ReviewDetailScreen.kt` — `ReviewItemCard` 내 "문제 듣기" (`VolumeUp`) + "내 답변" (`Headphones`) + "모범 답안" (`PlayArrow`) 버튼 추가

### Feature 6 — 암기장 신규 기능 ✅
- **DB 스키마 v1→v2** (`OPicDatabase.kt`):
  - `AudioPacks` 테이블 제거
  - `Flashcards` 테이블 추가 (card_id, card_type, front, back, example, category, difficulty)
  - `FlashcardProgress` 테이블 추가 (SM-2 필드: ease_factor, interval_days, repetitions, next_due, last_reviewed)
  - MIGRATION_1_2: DROP/CREATE + 시드 데이터 **30개 단어 + 20개 문장** INSERT
- `data/local/entity/FlashcardEntity.kt`, `FlashcardProgressEntity.kt` 신규
- `data/local/dao/FlashcardDao.kt` 신규 — `getTodayCards` (LEFT JOIN, due 우선→신규 채움, LIMIT)
- `domain/usecase/SM2UseCase.kt` 신규 — O(quality=5): EF+0.1, interval×EF / X: reset(interval=1, rep=0, EF-0.2 min 1.3)
- `ui/flashcard/FlashcardViewModel.kt` 신규 — `loadHomeStats`, `loadCards`, `loadAllCards`, `flipCard`, `evaluate`, `resetSession`
- 화면 4개 신규: `FlashcardHomeScreen`, `WordCardScreen` (공유 `FlashcardSessionContent` 포함), `SentenceCardScreen`, `DailyWordScreen`, `DailySentenceScreen`
- `ui/home/HomeScreen.kt` — "오디오 팩" 버튼 → "암기장" 버튼으로 교체
- `MainActivity.kt` — `flashcard_home/words/sentences/daily_word/daily_sentence` 라우트 추가

---

## 2. 시도했으나 실패 / 문제 발생 → 해결한 것

### 빌드 실패: JAVA_HOME 미설정
- **증상**: `gradlew.bat assembleDebug` 실행 시 `JAVA_HOME is not set` 오류
- **해결**: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew.bat assembleDebug`로 빌드

### SettingsScreen.kt 컴파일 에러: TopAppBar import 누락
- **증상**: `TopAppBar` unresolved reference
- **해결**: `import androidx.compose.material3.TopAppBar` 추가

### FlashcardViewModel Flow 패턴 오류
- **증상**: `stateIn().collect()` 패턴이 코루틴 내에서 무한 collect하는 구조
- **해결**: `Flow.first()`로 단일 값 수집 방식으로 교체
  ```kotlin
  val cards = flashcardDao.getTodayCards(userId, type, today, limit).first()
  _session.value = CardSessionState(cards = cards, ...)
  ```

### Kotlin combine 5개 Flow 제한
- **증상**: `combine(flow1, flow2, flow3, flow4, flow5)` 컴파일 에러 (named overload 최대 4개)
- **해결**: `FilterState` 데이터 클래스로 3개 필터 Flow를 먼저 합친 후 3-Flow combine 사용

---

## 3. 완료되지 않은 작업 (다음 에이전트 작업 필요)

### 3-1. 암기장 시드 데이터 부족 ⚠️ 우선순위 높음
- 현재: 30개 단어 + 20개 문장만 `MIGRATION_1_2`에 포함
- 계획: 단어 200개 + 문장 150개
- 작업 위치: `OPicDatabase.kt`의 `MIGRATION_1_2` 내 `execSQL` INSERT 구문 또는 `make_room_db.py`
- 방법: Python 스크립트 `make_room_db.py`에 INSERT 데이터 추가 후 DB 재생성

### 3-2. `make_room_db.py` 미업데이트 ⚠️
- 현재: v1 스키마 기준 Python DB 생성 스크립트
- 필요: `AudioPacks` 테이블 제거, `Flashcards` + `FlashcardProgress` 테이블 추가, 전체 시드 데이터 반영
- 신규 설치 시 Room이 assets DB를 복사 후 `MIGRATION_1_2` 실행하므로 현재도 동작은 하지만, 스크립트가 최신화되지 않으면 향후 혼란 발생

### 3-3. 미사용(dead) 파일 정리
다음 파일들은 컴파일에 영향 없이 프로젝트에 남아 있음:
- `ui/test/SurveyScreen.kt` — `survey` 라우트 제거됐으나 파일 존재 (`SURVEY_PARTS` 참조 TestViewModel에 잔존)
- `data/local/entity/AudioPackEntity.kt` — DB entities에서 제거됐으나 파일 존재
- `ui/audiopack/AudioPackScreen.kt` — stub 상태
- `ui/audiopack/AudioPackViewModel.kt` — stub 상태
- `data/remote/AudioPackDownloader.kt` — stub 상태
- `data/repository/AudioPackRepository.kt` — stub 상태

### 3-4. Deprecation 경고 정리 (낮은 우선순위)
```
Icons.Filled.ArrowBack  → Icons.AutoMirrored.Filled.ArrowBack
Icons.Filled.MenuBook   → Icons.AutoMirrored.Filled.MenuBook
Icons.Filled.VolumeUp   → Icons.AutoMirrored.Filled.VolumeUp
Divider()               → HorizontalDivider()
```
영향 파일: `SettingsScreen.kt`, `DailySentenceScreen.kt`, `DailyWordScreen.kt`, `FlashcardHomeScreen.kt`, `WordCardScreen.kt`, `SentenceCardScreen.kt`, `StudyListScreen.kt`, `ReviewDetailScreen.kt`

### 3-5. 복습 화면 ViewModel AudioPlayer 주입 검토
- 계획서에는 `ReviewViewModel.kt`에 `AudioPlayerManager` 주입 명시
- 현재: `ReviewDetailScreen.kt`에서 오디오 버튼 UI만 추가된 상태 (실제 재생 연결 여부 확인 필요)
- `AudioPlayerManager`가 이미 주입돼 있지 않다면 `ReviewViewModel.kt` 수정 필요

---

## 4. 프로젝트 핵심 정보

| 항목 | 값 |
|------|-----|
| 패키지 | `com.opic.android` |
| 프로젝트 경로 | `C:\PythonProject\Claude_Local\OPIC\OPicAndroid` |
| DB 버전 | **2** (`MIGRATION_1_2` 적용) |
| 빌드 명령 | `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew.bat assembleDebug` |
| DI 프레임워크 | Hilt (`@HiltViewModel`, `@Singleton`, `@ApplicationContext`) |
| DB | Room + KSP |
| UI | Jetpack Compose + Material3 |
| 오디오 재생 | ExoPlayer (`AudioPlayerManager`) |
| 오디오 폴더 | SAF (`OpenDocumentTree`), URI → `SharedPreferences["audio_folder_uri"]` |
| 녹음 저장 경로 | `{filesDir}/recordings/{questionId}.m4a` |
| SM-2 상태 | `FlashcardProgress` 테이블, userId=1 하드코딩 |

---

## 5. 아키텍처 주요 결정 사항

- **주제 선택 상태 공유**: `TopicSelectionScreen` → `TestExecutionScreen` 간 한국어 문자열 리스트를 nav argument로 전달 불가 → `@Singleton class TopicSelectionState`로 해결
- **오디오 파일 캐싱**: `DocumentFile.findFile()` O(n) 문제 → `buildCache()`로 전체 맵 1회 로드
- **하루 학습량 제한**: SM-2 due 카드 우선, 부족하면 신규 카드 채움, 총 10개 LIMIT (FlashcardDao LEFT JOIN 쿼리)
