# OPIC Android UI 표준 규격서 v1.0

> 원칙: Python(PyQt5) 앱의 **동작 동일** 이식. UI "예쁘게" 바꾸기 금지.
> Material 3 컴포넌트를 사용하되, 색상·아이콘·다이얼로그 패턴은 원본 앱 기준.

---

## 1. 아이콘 세트

### 1.1 전체 아이콘 맵

| ID | 용도 | Material Icon 이름 | Python 원본 | 사용 화면 |
|----|------|-------------------|------------|-----------|
| `ic_play` | 오디오 재생 | `Icons.Filled.PlayArrow` | sound-play.png | Test, Review, Study |
| `ic_stop` | 오디오 정지 | `Icons.Filled.Stop` | stop.png | Test, Review, Study |
| `ic_record` | 녹음 시작 | `Icons.Filled.Mic` | sr.png | Test, Study |
| `ic_record_stop` | 녹음 정지 | `Icons.Filled.Stop` (빨강) | stop.png | Test, Study |
| `ic_save` | DB에 저장 | `Icons.Filled.Save` | (텍스트 "💾") | Review, Study |
| `ic_edit` | 편집 모드 진입 | `Icons.Filled.Edit` | (텍스트 "Edit") | Review, Study |
| `ic_delete` | 문제 삭제 | `Icons.Filled.Delete` | (텍스트 "삭제") | Study |
| `ic_favorite_on` | 즐겨찾기 ON | `Icons.Filled.Favorite` | (텍스트 "📌") | Study |
| `ic_favorite_off` | 즐겨찾기 OFF | `Icons.Outlined.FavoriteBorder` | (텍스트 "📌") | Study |
| `ic_export` | DB 내보내기 | `Icons.Filled.FileUpload` | (텍스트 "📤") | Study |
| `ic_import` | DB 가져오기 | `Icons.Filled.FileDownload` | (텍스트 "📥") | Study |
| `ic_home` | 홈으로 이동 | `Icons.Filled.Home` | (텍스트 "Home") | Test, Review, Study |
| `ic_back` | 이전 화면 | `Icons.AutoMirrored.Filled.ArrowBack` | (텍스트 "< Back") | Survey, Assessment |
| `ic_next` | 다음 단계 | `Icons.AutoMirrored.Filled.ArrowForward` | (텍스트 "Next >") | Survey, Assessment, Test |
| `ic_search` | YouGlish 검색 | `Icons.Filled.Search` | (텍스트 "검색") | Study |
| `ic_translate` | 번역 | `Icons.Filled.Translate` | (텍스트 "번역") | Study |
| `ic_reset` | 필터 초기화 | `Icons.Filled.Refresh` | (텍스트 "💫") | Study |
| `ic_study_done` | 학습 완료 | `Icons.Filled.CheckCircle` | (텍스트 "학습완료") | Study |
| `ic_fullscreen` | 전체 화면 | `Icons.Filled.Fullscreen` | (텍스트 "전체 화면") | Study |
| `ic_theme` | 테마 전환 | `Icons.Filled.DarkMode` / `LightMode` | (텍스트 "■") | Study |
| `ic_group_play` | 그룹 재생 | `Icons.Filled.PlaylistPlay` | (텍스트 "▶ Group Play") | Study |
| `ic_analysis` | 분석 실행 | `Icons.Filled.Analytics` | (텍스트 "분석 실행") | Study |

### 1.2 아이콘 사용 규칙

```
1. Material Icons (벡터) 사용 — PNG 아이콘 금지 (해상도 독립)
2. 다크/라이트 테마 자동 대응 (tint color로 제어)
3. 터치 영역: 최소 48dp × 48dp (Material 가이드라인)
4. 아이콘 크기: 24dp (기본), 36dp (강조), 48dp (Record 버튼)
```

### 1.3 레벨 아바타 (래스터 유지)

| 파일명 | 용도 | 위치 | 규격 |
|--------|------|------|------|
| `a1.png` ~ `a10.png` | 레벨별 캐릭터 | `res/drawable/` | 550×550px → 앱에서 200dp로 표시 |
| `eva.png` | Test/Review 마스코트 | `res/drawable/` | 280×280px → 앱에서 140dp로 표시 |

---

## 2. 색상 체계

### 2.1 Python 원본 → Android 매핑

```kotlin
// ui/theme/OPicColors.kt

object OPicColors {
    // === Primary Actions ===
    val Primary       = Color(0xFFFF5733)  // 주요 버튼 (Next, Start Test, Back)
    val PrimaryText   = Color.White        // 주요 버튼 텍스트

    // === Secondary Actions ===
    val Secondary     = Color(0xFFDDDCE9)  // 보조 버튼 (Study, Review 바로가기)
    val StudyText     = Color.Blue         // Study 버튼 텍스트
    val ReviewText    = Color.Red          // Review 버튼 텍스트

    // === Audio Controls ===
    val PlayButton    = Color(0xFFFF8C69)  // 재생 버튼 배경
    val StopButton    = Color(0xFFFFCDD2)  // 정지 버튼 배경
    val RecordActive  = Color.Red          // 녹음 중 표시

    // === Progress & Timer ===
    val LevelGauge    = Color(0xFF05B8CC)  // 홈 레벨 게이지
    val TimerGreen    = Color(0xFF2ECC71)  // 타이머 >60초
    val TimerOrange   = Color(0xFFF39C12)  // 타이머 30~60초
    val TimerRed      = Color(0xFFE74C3C)  // 타이머 <30초

    // === UI Surface ===
    val LightBg       = Color(0xFFF0F0F0)  // 라이트 모드 배경
    val DarkBg        = Color(0xFF333333)  // 다크 모드 배경
    val DisabledBg    = Color(0xFFD3D3D3)  // 비활성 상태
    val Border        = Color(0xFFCCCCCC)  // 테두리
    val TextOnLight   = Color(0xFF333333)  // 라이트 모드 텍스트
    val TextOnDark    = Color(0xFFF5EEDC)  // 다크 모드 텍스트 (크림)

    // === Question Grid (TestScreen) ===
    val GridCurrent   = Color(0xFF333333)  // 현재 문제 번호 배경
    val GridDefault   = Color(0xFFEEEEEE)  // 기본 문제 번호 배경
    val GridAnswered  = Color(0xFF2ECC71)  // 답변 완료 문제 번호
}
```

### 2.2 타이머 색상 전환 규칙

```
남은 시간 > 60초  → TimerGreen  (#2ECC71) — 텍스트 + 프로그레스 바
30 < 시간 ≤ 60초 → TimerOrange (#F39C12)
시간 ≤ 30초      → TimerRed    (#E74C3C)
```

---

## 3. 버튼 동작 규칙

### 3.1 다이얼로그 분류 체계

모든 사용자 피드백은 **4가지 유형**으로 분류:

| 유형 | Android 구현 | 아이콘 | 용도 |
|------|------------|--------|------|
| **확인(Confirm)** | AlertDialog (Yes/No) | ⚠️ | 되돌릴 수 없는 작업 전 |
| **성공(Success)** | Snackbar 또는 Toast | ✅ | 작업 완료 알림 |
| **경고(Warning)** | AlertDialog (OK) | ⚠️ | 입력 누락, 데이터 없음 |
| **오류(Error)** | AlertDialog (OK) | ❌ | DB 실패, 파일 오류 |

### 3.2 확인 다이얼로그 (Confirm) — 전체 목록

> Python QMessageBox.question() → Android AlertDialog

| 트리거 | 제목 | 메시지 | 버튼 | 기본값 |
|--------|------|--------|------|--------|
| 스크립트 저장 (Review) | "저장 확인" | "수정한 내용을 DB에 저장하시겠습니까?" | 저장 / 취소 | **저장** |
| 스크립트 저장 (Study) | "저장 확인" | "수정한 내용을 DB에 저장하시겠습니까?" | 저장 / 취소 | **취소** |
| 문제 삭제 | "삭제 확인" | "'{title}' 질문을 DB에서 영구적으로 삭제하시겠습니까?\n(모든 관련 데이터가 사라집니다.)" | 삭제 / 취소 | **취소** |
| 시험 중 Home | "시험 중단" | "시험이 진행 중입니다. 나가시겠습니까?" | 나가기 / 계속 | **계속** |
| 재분석 | "확인" | "저장된 분석 결과가 있습니다. 새로 분석하시겠습니까?" | 예 / 아니요 | **아니요** |
| MP3 오류 재생 | "MP3 파일 오류" | "오디오 파일을 재생할 수 없습니다.\n파일이 손상되었을 수 있습니다." | 예 / 아니요 | **아니요** |

### 3.3 성공 메시지 (Success)

| 트리거 | 메시지 | 표시 방법 |
|--------|--------|-----------|
| 스크립트 저장 완료 | "내용이 성공적으로 저장되었습니다." | Snackbar (3초) |
| 새 문제 추가 완료 | "'{title}' 항목이 성공적으로 저장되었습니다." | Snackbar (3초) |
| 문제 삭제 완료 | "'{title}' 질문이 삭제되었습니다." | Snackbar (3초) |
| 내보내기 완료 | "내보내기 완료" + 파일 경로 | AlertDialog (OK) |
| 가져오기 완료 | "가져오기 결과" + 테이블별 행 수 | AlertDialog (OK) |
| 그룹 재생 완료 | "그룹 플레이가 완료되었습니다." | Snackbar (2초) |

### 3.4 경고 메시지 (Warning)

| 트리거 | 제목 | 메시지 |
|--------|------|--------|
| 난이도 미선택 | "난이도 선택" | "먼저 본인의 영어 말하기 수준(1-6)을 선택해야 합니다." |
| 문제 미선택 | "알림" | "먼저 질문을 선택해주세요." |
| 데이터 없음 | "데이터 없음" | "표시할 데이터가 없습니다.\n먼저 목록에서 항목을 선택해주세요." |
| 검색어 없음 | "검색어 없음" | "검색할 단어를 입력해주세요." |
| 번역어 없음 | "번역어 없음" | "번역할 단어나 문장을 입력해주세요." |
| 필수 입력 누락 | "입력 오류" | "제목, 주제, 유형, 콤보, 질문/답변 스크립트는 필수 항목입니다." |
| STT 결과 없음 | "알림" | "표시할 STT 결과가 없습니다." |
| 시험 기록 없음 | "기록 없음" | "저장된 시험 기록이 없습니다." |
| 자기소개 편집 불가 | "수정 불가" | "자기소개 스크립트는 Study 화면에서 수정해주세요." |

### 3.5 오류 메시지 (Error)

| 트리거 | 제목 | 메시지 |
|--------|------|--------|
| DB 저장 실패 | "Database Error" | "DB 저장 실패: {에러 상세}" |
| 내보내기 실패 | "내보내기 오류" | 동적 에러 메시지 |
| 가져오기 실패 | "가져오기 오류" | 동적 에러 메시지 |
| 오디오 파일 없음 | "재생 오류" | "오디오 파일을 찾을 수 없습니다:\n{filename}" |
| 오디오 재생 실패 | "재생 오류" | "오디오 재생 중 오류 발생:\n{error}" |
| TTS 실패 | "TTS 오류" | "음성 파일을 생성하지 못했습니다." |
| 분석 실패 | "분석 실패" | "분석 중 오류가 발생했거나 결과가 없습니다." |
| 백그라운드 작업 오류 | "스레드 오류" | "백그라운드 작업 중 오류 발생:\n{err_msg}" |
| 문제 생성 실패 | "생성 실패" | "'{slot}' 문제 생성에 실패했습니다." |

### 3.6 로딩 표시 규칙

| 상황 | 표시 방법 | 지속 시간 |
|------|-----------|-----------|
| 오디오 재생 중 | Play 버튼 → Stop 아이콘 전환 | 재생 끝날 때까지 |
| 녹음 중 | Record 버튼 → Stop 아이콘 + 마이크 레벨 바 + 카운트다운 | 녹음 끝날 때까지 |
| TTS 생성 중 | 버튼 비활성(disabled) | 생성 완료까지 |
| 분석 실행 중 | CircularProgressIndicator + "분석 중..." | 응답까지 |
| DB Export/Import | CircularProgressIndicator 오버레이 | 완료까지 |
| 문제 세트 생성 중 | 화면 전환 없이 대기 (Python과 동일: 즉시 완료) | <1초 |

### 3.7 버튼 활성/비활성 상태표

#### TestScreen

| 버튼 | 초기 | 재생 중 | Beep/대기 | 녹음 중 | 녹음 완료 |
|------|------|---------|-----------|---------|-----------|
| Play | ✅(1회) | ❌ | ❌ | ❌ | ❌ |
| Record | ❌ | ❌ | ❌→✅ | ✅(Stop) | ❌ |
| Next | ❌ | ❌ | ❌ | ❌ | ✅ |
| Home | ✅ | ✅ | ✅ | ✅(확인) | ✅ |
| 번호[1-15] | ✅ | ❌ | ❌ | ❌ | ✅ |

#### ReviewScreen

| 버튼 | 첫 문제 | 중간 문제 | 마지막 문제 |
|------|---------|-----------|------------|
| < Back | ❌ | ✅ | ✅ |
| Next > | ✅ | ✅ | ❌ |
| Play User | 파일 존재 시 ✅ | 파일 존재 시 ✅ | 파일 존재 시 ✅ |
| Play Q/A | ✅ (항상) | ✅ (항상) | ✅ (항상) |
| Edit | ✅ (Self-Intro 제외) | ✅ | ✅ |

#### StudyScreen

| 버튼 | 문제 미선택 | 문제 선택됨 | 재생 중 | 녹음 중 |
|------|------------|-----------|---------|---------|
| Play Q/A | ❌ | ✅ | ❌ | ❌ |
| Record | ❌ | ✅ | ❌ | ✅(Stop) |
| Edit | ❌ | ✅ | ✅ | ✅ |
| Save | 숨김 | 편집 모드일 때 ✅ | ✅ | ✅ |
| 학습완료 | ❌ | ✅ | ✅ | ✅ |
| 📌 즐겨찾기 | ❌ | ✅ | ✅ | ✅ |
| 삭제 | ❌ | ✅ | ❌ | ❌ |
| Group Play | Title 0개 시 ❌ | ✅ | ❌ | ❌ |

---

## 4. 저장/삭제/내보내기 UX 플로우

### 4.1 스크립트 저장 플로우

```
[사용자가 Edit 클릭]
  │
  ├─ Self Introduction? (ReviewScreen)
  │   └─ YES → AlertDialog "수정 불가: Study 화면에서 수정해주세요." → 중단
  │
  └─ NO →
      ├─ 텍스트 영역: readOnly=false (편집 가능)
      ├─ Edit 버튼 텍스트 → "Hide"
      └─ Save 버튼 표시

[사용자가 텍스트 수정 후 Save 클릭]
  │
  ├─ AlertDialog "수정한 내용을 DB에 저장하시겠습니까?"
  │   ├─ [저장] → DB UPDATE 실행
  │   │   ├─ 성공 → Snackbar "내용이 성공적으로 저장되었습니다."
  │   │   │        → 편집 모드 해제 (readOnly=true, Save 숨김)
  │   │   └─ 실패 → AlertDialog "DB 저장 실패: {에러}"
  │   │
  │   └─ [취소] → 편집 모드 유지 (변경 내용 보존)
```

### 4.2 문제 삭제 플로우

```
[사용자가 삭제 버튼 클릭]
  │
  ├─ 문제 미선택?
  │   └─ YES → AlertDialog "먼저 질문을 선택해주세요." → 중단
  │
  └─ NO →
      AlertDialog "'{title}' 질문을 DB에서 영구적으로 삭제하시겠습니까?
                   (모든 관련 데이터가 사라집니다.)"
        │
        ├─ [삭제] → DB DELETE 실행
        │   ├─ User_Study_Progress 관련 행 삭제
        │   ├─ Questions 행 삭제
        │   ├─ 성공 → Snackbar "'{title}' 질문이 삭제되었습니다."
        │   │        → 필터 드롭다운 갱신
        │   └─ 실패 → AlertDialog "DB Error: {에러}"
        │
        └─ [취소] → 중단 (아무 변경 없음)
```

### 4.3 내보내기(Export) 플로우

```
[사용자가 📤 내보내기 클릭]
  │
  ├─ CircularProgressIndicator 표시
  │
  ├─ 7개 테이블 → JSON (또는 Excel) 생성
  │   파일명: opic_backup_{yyyyMMdd_HHmmss}.json
  │   저장: Downloads 폴더 (SAF 사용)
  │
  ├─ 성공 → AlertDialog "내보내기 완료"
  │          + 파일 경로 표시
  │          + [공유] 버튼 (선택: Intent.ACTION_SEND)
  │
  └─ 실패 → AlertDialog "내보내기 오류: {에러}"
```

### 4.4 가져오기(Import) 플로우 ★초보 안전안

```
[사용자가 📥 가져오기 클릭]
  │
  ├─ SAF 파일 선택 (ACTION_OPEN_DOCUMENT)
  │   MIME: application/json 또는 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  │
  ├─ 파일 유효성 검사
  │   ├─ 실패 → AlertDialog "올바른 OPIC 백업 파일이 아닙니다." → 중단
  │
  ├─ ⚠️ AlertDialog "가져오기 확인"
  │   "현재 데이터가 모두 삭제되고 새 데이터로 대체됩니다.
  │    먼저 현재 데이터를 백업하시겠습니까?"
  │
  │   ├─ [백업 후 가져오기]
  │   │   └─ 자동 Export 실행 → Import 진행
  │   │
  │   ├─ [바로 가져오기]
  │   │   └─ Import 진행
  │   │
  │   └─ [취소] → 중단
  │
  ├─ [Import 진행]
  │   ├─ 자동 안전 백업: opic_pre_import.db 복사 (항상)
  │   ├─ CircularProgressIndicator 표시
  │   ├─ BEGIN TRANSACTION
  │   ├─ 각 테이블: DELETE → INSERT
  │   ├─ Questions 중복 title → "ov_" prefix 처리
  │   │
  │   ├─ 성공 → COMMIT
  │   │   └─ AlertDialog "가져오기 결과"
  │   │      + 테이블별 처리 행 수
  │   │      + 이름 변경 건수
  │   │
  │   └─ 실패 → ROLLBACK
  │       └─ AlertDialog "가져오기 오류: {에러}\n데이터가 복원되었습니다."
```

### 4.5 새 문제 추가 플로우

```
[사용자가 💾 데이터 저장 클릭]
  │
  ├─ 필수 입력 확인 (title, set, type, combo, question_text 또는 answer_script)
  │   └─ 누락 → AlertDialog "제목, 주제, 유형, 콤보, 질문/답변 스크립트는 필수 항목입니다."
  │
  ├─ title 중복 확인
  │   └─ 중복 → "ov_" prefix 자동 부여
  │
  ├─ DB INSERT 실행
  │   ├─ 성공 → Snackbar "'{title}' 항목이 성공적으로 저장되었습니다."
  │   │        → 필터 드롭다운 갱신
  │   └─ 실패 → AlertDialog "DB Error: {에러}"
```

### 4.6 시험 중 Home 플로우

```
[사용자가 Home 클릭 (TestScreen)]
  │
  ├─ 녹음 중?
  │   ├─ YES → AlertDialog "시험이 진행 중입니다. 나가시겠습니까?"
  │   │   ├─ [나가기] → 녹음 중단 → 스레드 정리 → StartPage 이동
  │   │   └─ [계속]  → 다이얼로그 닫기, 녹음 계속
  │   │
  │   └─ NO → StartPage 직접 이동 (popBackStack)
```

---

## 5. 편집 모드(Edit/Save) 토글 패턴

### 5.1 공용 ScriptEditor 컴포넌트 상태

```
┌─────────────────────────────────────────────┐
│ [▶ Play] [■ Stop]  [Edit]          [💾 Save]│
│ ┌─────────────────────────────────────────┐ │
│ │ 스크립트 텍스트 영역                       │ │
│ │ (readOnly=true 기본)                    │ │
│ └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘

상태 A: 읽기 모드 (기본)
  - 텍스트: readOnly=true, 배경 밝은 회색
  - Edit 버튼: 표시 ("Edit")
  - Save 버튼: 숨김

상태 B: 편집 모드
  - 텍스트: readOnly=false, 배경 흰색, 포커스
  - Edit 버튼: 텍스트 → "Hide"
  - Save 버튼: 표시

전환:
  Edit 클릭 → 상태 A→B
  Hide 클릭 → 상태 B→A (변경 내용 유지, 저장 안 함)
  Save 클릭 → 확인 다이얼로그 → 성공 시 상태 B→A
```

### 5.2 스크립트 유형별 저장 대상

| 스크립트 | DB 테이블.컬럼 | 화면 |
|----------|---------------|------|
| Question Script | Questions.question_text | Review, Study |
| Answer Script | Questions.answer_script | Review, Study |
| User Script | Questions.user_script | Study |

---

## 6. 리소스 규격 및 파일명 규칙

### 6.1 앱 아이콘

| 유형 | 크기 | 파일 | 비고 |
|------|------|------|------|
| Launcher (mdpi) | 48×48 | `mipmap-mdpi/ic_launcher.webp` | Adaptive icon |
| Launcher (hdpi) | 72×72 | `mipmap-hdpi/ic_launcher.webp` | |
| Launcher (xhdpi) | 96×96 | `mipmap-xhdpi/ic_launcher.webp` | |
| Launcher (xxhdpi) | 144×144 | `mipmap-xxhdpi/ic_launcher.webp` | |
| Launcher (xxxhdpi) | 192×192 | `mipmap-xxxhdpi/ic_launcher.webp` | |
| Foreground | 108×108 dp (432px) | `ic_launcher_foreground.xml` | Vector drawable |
| Background | 108×108 dp | `ic_launcher_background.xml` | 단색 or Vector |

### 6.2 래스터 이미지 (PNG) — 원본 보존

| Python 파일 | Android 파일 | 위치 | 용도 |
|------------|-------------|------|------|
| `a1.png` ~ `a10.png` | `level_1.png` ~ `level_10.png` | `res/drawable/` | 레벨 아바타 |
| `eva.png` | `eva.png` | `res/drawable/` | Test/Review 마스코트 |
| `OPIC_TOP.png` | `banner_top.png` | `res/drawable/` | 홈 상단 배너 |
| `OPIC_STEP5_1_1.png` | `banner_begin_test.png` | `res/drawable/` | BeginTest 안내 |
| `top-step-1.png` ~ `5.png` | `step_indicator_1.png` ~ `5.png` | `res/drawable/` | 진행 단계 표시 |

### 6.3 오디오 에셋

| 유형 | 위치 | 파일명 패턴 | 예시 |
|------|------|------------|------|
| 질문 음성 | `assets/Sound/` | `{원본파일명}.mp3` | `AL_01_Q_00.mp3` |
| 답변 음성 | `assets/Sound/` | `{원본파일명}.mp3` | `AL_01_A_00.mp3` |
| 비프음 | `res/raw/` | `beep.wav` | 녹음 시작 신호 |
| 사용자 녹음 | `getExternalFilesDir("Recording")/` | `TestRec_{qId}_{timestamp}.wav` | `TestRec_12_20260227_143022.wav` |
| TTS 캐시 | `cacheDir/tts/` | `tts_{sha1}.wav` | `tts_a1b2c3d4.wav` |

### 6.4 파일명 규칙 총정리

```
=== Kotlin 파일 ===
패턴: PascalCase.kt
예시: QuestionGenerator.kt, TestViewModel.kt, AudioPlayer.kt

=== Compose 화면 ===
패턴: {Screen}Screen.kt
예시: StartScreen.kt, TestScreen.kt, StudyScreen.kt

=== ViewModel ===
패턴: {Screen}ViewModel.kt
예시: StartViewModel.kt, TestViewModel.kt

=== Entity ===
패턴: {TableName(단수)}.kt
예시: Question.kt, UserStudyProgress.kt, TestResult.kt

=== DAO ===
패턴: {Domain}Dao.kt
예시: QuestionDao.kt, StudyProgressDao.kt, TestDao.kt

=== Repository ===
패턴: {Domain}Repository.kt
예시: QuestionRepository.kt, TestRepository.kt

=== drawable (래스터) ===
패턴: snake_case.png
예시: level_1.png, eva.png, banner_top.png

=== drawable (벡터) ===
패턴: ic_{용도}.xml
예시: ic_play.xml, ic_record.xml (Material Icons 사용 시 불필요)

=== raw (오디오) ===
패턴: snake_case.wav
예시: beep.wav

=== assets ===
패턴: 원본 유지 (Sound/ 하위)
예시: AL_01_Q_00.mp3 (Python과 동일)
```

### 6.5 dp/sp 규격 정리

| 요소 | 규격 | Python 원본 |
|------|------|------------|
| 레벨 아바타 | 200dp × 200dp | 550×550px |
| EVA 이미지 | 140dp × 140dp | 280×280px |
| 상단 배너 | match_parent × 56dp | height: 100px |
| Record 버튼 | 64dp × 64dp (FAB 스타일) | 280×80px |
| Play/Stop 버튼 | 48dp × 48dp | 16×16px (아이콘) |
| 문제 번호 셀 | 48dp × 48dp | 가변 |
| 최소 터치 영역 | 48dp × 48dp | Material 가이드라인 |
| 본문 텍스트 | 16sp (기본) | 가변(10~34pt) |
| 제목 텍스트 | 20sp | 16px bold |
| 버튼 텍스트 | 14sp | 가변 |
| 마이크 레벨 바 | match_parent × 8dp | 가변 |
| 타이머 텍스트 | 32sp | 가변 |

---

## 7. 화면별 레이아웃 와이어프레임

### 7.1 StartPage

```
┌────────────────────────────┐
│   [banner_top.png]         │  56dp
├────────────────────────────┤
│                            │
│     ┌──────────────┐       │
│     │  level_N.png │       │  200dp × 200dp
│     └──────────────┘       │
│       "Level 5"            │  20sp bold
│   ┌──────────────────┐     │
│   │ ████████░░░░░░░░ │     │  게이지 바 (LevelGauge)
│   └──────────────────┘     │
│                            │
├────────────────────────────┤
│ [Study]   [Next >]  [Review]│  하단 버튼 바
└────────────────────────────┘
```

### 7.2 TestScreen

```
┌────────────────────────────┐
│  "Question 3 of 15"       │  TopAppBar
├─────────────┬──────────────┤
│             │ ┌──┬──┬──┐   │
│  [eva.png]  │ │1 │2 │3▪│   │  번호 그리드
│  140dp      │ │4 │5 │6 │   │  (▪=현재)
│             │ └──┴──┴──┘   │
│ ┌────────┐  │              │
│ │MM:SS   │  │              │  타이머 (32sp)
│ │████░░░ │  │              │  프로그레스 바
│ └────────┘  │              │
│ [▶ Play]    │              │  48dp 버튼
│ [● Record]  │              │  64dp FAB
│ ┌────────┐  │              │
│ │ MicLvl │  │              │  8dp 레벨 바
│ └────────┘  │              │
├─────────────┴──────────────┤
│ [Home]              [Next >]│
└────────────────────────────┘
```

### 7.3 StudyScreen (세로 스크롤)

```
┌────────────────────────────┐
│ 필터 바                     │
│ [Problem▼][Set▼][Type▼]    │  Row of DropdownMenus
│ [Sort▼][Study▼][Size▼]     │
│ [학습완료][📌][🔄][▶Group] │  Action buttons
│ [Title ▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼]  │  Full-width dropdown
├────────────────────────────┤
│ Q Script                   │
│ [▶][■][Edit]      [💾Save] │
│ ┌────────────────────────┐ │
│ │ 질문 텍스트...           │ │
│ └────────────────────────┘ │
├────────────────────────────┤
│ A Script                   │
│ [▶][■][Edit]      [💾Save] │
│ ┌────────────────────────┐ │
│ │ 답변 스크립트 텍스트...    │ │  (큰 영역)
│ └────────────────────────┘ │
├────────────────────────────┤
│ User Audio                 │
│ [▶ Play][■ Stop][● Rec]   │
├────────────────────────────┤
│ User Script                │
│ [▶][■][Edit][STT Load][💾] │
│ ┌────────────────────────┐ │
│ │ 사용자 스크립트...        │ │
│ └────────────────────────┘ │
├────────────────────────────┤
│ 분석                       │
│ [분석 유형 ▼] [분석 실행]    │
│ ┌────────────────────────┐ │
│ │ 분석 결과 (HTML)         │ │
│ └────────────────────────┘ │
├────────────────────────────┤
│ 데이터 관리                  │
│ Set[   ] Type[   ] Combo[  ]│
│ Title[              ]      │
│ [삭제] [💾저장] [📥][📤]   │
├────────────────────────────┤
│ [Home]                     │
└────────────────────────────┘
```

### 7.4 ReviewScreen

```
┌────────────────────────────┐
│  "Question 5 of 15"       │  TopAppBar
├─────────────┬──────────────┤
│             │ Q Script     │
│  [eva.png]  │ [▶][■][Edit][💾]│
│  140dp      │ ┌──────────┐ │
│             │ │ 질문 텍스트│ │
│ User Answer │ └──────────┘ │
│ [▶ Play]    │ A Script     │
│ [■ Stop]    │ [▶][■][Edit][💾]│
│             │ ┌──────────┐ │
│             │ │ 답변 텍스트│ │
│             │ └──────────┘ │
├─────────────┴──────────────┤
│ [1][2][3][4][5]...[15]     │  번호 그리드
├────────────────────────────┤
│ [< Back] [Home]   [Next >] │
└────────────────────────────┘
```

---

## 8. Compose 구현 가이드 (핵심 패턴)

### 8.1 다이얼로그 공용 함수

```kotlin
// ui/components/Dialogs.kt

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "확인",
    dismissText: String = "취소",
    isDestructive: Boolean = false,   // true면 confirmText 빨간색
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
)

@Composable
fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
)

@Composable
fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
)
```

### 8.2 ScriptEditor 공용 컴포넌트

```kotlin
// ui/components/ScriptEditor.kt

@Composable
fun ScriptEditor(
    label: String,                    // "Q Script", "A Script", "User Script"
    text: String,
    isEditing: Boolean,
    onTextChange: (String) -> Unit,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    onEditToggle: () -> Unit,
    onSaveClick: () -> Unit,
    isPlaying: Boolean = false,
    showSttLoad: Boolean = false,     // User Script 전용
    onSttLoadClick: (() -> Unit)? = null
)
```

### 8.3 상태 관리 패턴

```kotlin
// ViewModel에서 UI 상태를 하나의 data class로 관리
// Python의 여러 self.is_* 변수 → 단일 StateFlow

data class TestUiState(
    val currentIndex: Int = 0,
    val totalQuestions: Int = 15,
    val phase: TestPhase = TestPhase.INITIAL,
    val playAllowedOnce: Boolean = true,
    val countdownSeconds: Int = 120,
    val micLevel: Float = 0f,
    val questions: List<QuestionData> = emptyList()
)

enum class TestPhase {
    INITIAL,       // 문제 로드됨, Play만 활성
    PLAYING,       // 음성 재생 중
    BEEP_WAIT,     // Beep 후 5초 대기
    RECORDING,     // 녹음 중
    RECORDED,      // 녹음 완료, Next 활성
    FINISHED       // 마지막 문제 완료 → Review 전환
}
```
