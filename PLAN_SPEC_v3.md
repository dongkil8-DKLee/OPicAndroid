# OPIc Android — PLAN SPEC v3

> 작성일: 2026-02-25
> 인터뷰 10문항 기반 확정 스펙

---

## 인터뷰 결정 사항 요약

| # | 질문 | 결정 |
|---|------|------|
| Q1 | 트랙 재생 구성 | **답변 오디오만 재생** |
| Q2 | 순서/루프 | **순차 재생 + 무한 반복** |
| Q3 | 녹음 충돌 | **재생 중 녹음 버튼 비활성** |
| Q4 | 필터 관계 | **구성 다중선택 + 주제 단일선택, AND 조건** |
| Q5 | 트랙 전환 | **3초 간격 + 오디오 없으면 건너뛰기** |
| Q6 | 속도 적용 | **전체 동일 속도 + 즉시 반영** |
| Q7 | 바 범위 | **Study 화면 + Foreground Service** |
| Q8 | 화면 전환 | **일시정지 → 상세 개별 조작 → 복귀 시 재개** |
| Q9 | 삭제 범위 | **Session + Results 삭제 + Undo 스낵바 5초** |
| Q9-1 | Service 상세 | **화면 꺼져도 재생 + 알림 Play/Pause/Stop** |
| Q9-2 | 삭제 UX | **스와이프 → 삭제 버튼 → 확인 다이얼로그** |
| Q10 | 사용 시나리오 | **책상 액티브 학습 (따라 말하기 + 녹음)** |

---

## 1. 기능 개요

### 1-A. 반복재생 (Repeat Playback)
학습하기에서 필터링된 문제의 **답변 오디오만** 연속 재생.
Foreground Service로 화면 꺼져도 백그라운드 재생 유지.

### 1-B. 하단 플레이 바 (Bottom Play Bar)
학습하기 진입 시 StudyListScreen + StudyDetailScreen 하단 고정 오디오 컨트롤.

### 1-C. 시험 복습 삭제 (Review Delete)
ReviewListScreen에서 좌측 스와이프 → 삭제 버튼 → 확인 다이얼로그 → 삭제 + Undo 5초.

---

## 2. 반복재생 상세 스펙

### 2.1 재생 규칙
| 항목 | 결정 |
|------|------|
| 재생 대상 | **답변 오디오만** (questionAudio 제외) |
| 재생 순서 | **순차 (question_id ASC)** 필터 결과 내 |
| 루프 동작 | **무한 반복** (마지막 → 처음) |
| 트랙 간 간격 | **3초** (Silent delay) |
| 오디오 없는 문제 | **무음 건너뛰기** (알림 없음) |
| 재생 속도 | **전체 플레이리스트 동일 속도**, 변경 시 **즉시 반영** |

### 2.2 필터 시스템
```
전체: 모든 문제의 답변 오디오 반복재생
구성: [Ad, Rp, 돌발, 선택] 다중선택(Multi-select) → 선택된 type의 답변만
주제: 단일선택(Single-select) → 선택된 set의 답변만
조합: 구성 AND 주제 (둘 다 선택 시 교집합)
```
- **예시**: 구성=[Ad, Rp] + 주제=[여행] → 여행 주제의 Ad/Rp 문제 답변만 반복재생
- **전체 선택 시**: 구성/주제 필터 무시, 모든 문제 대상

### 2.3 녹음 충돌 처리
- 반복재생 중 녹음 버튼 **비활성화** (dimmed)
- 비활성 상태 탭 시 토스트: "반복재생을 정지한 후 녹음하세요"
- 반복재생 정지 후에만 녹음 가능

### 2.4 화면 전환 시 동작
| 시나리오 | 동작 |
|----------|------|
| StudyList → StudyDetail 탭 | 반복재생 **일시정지** |
| StudyDetail → 뒤로 (StudyList) | 반복재생 **자동 재개** (이전 위치에서) |
| Study → Home/Settings 등 | 반복재생 **계속** (Service 유지, 바 미표시) |
| 앱 백그라운드 / 화면 off | 반복재생 **계속** |
| 앱 swipe kill | 반복재생 **정지** (서비스 종료) |

---

## 3. Foreground Service 상세 스펙

### 3.1 알림 패널 구성
```
┌─────────────────────────────────────┐
│ 🎵 OPIc 학습                        │
│ [트랙 제목] — 3/15                   │
│                                     │
│     ▶ Play    ⏸ Pause    ⏹ Stop    │
└─────────────────────────────────────┘
```

### 3.2 기술 스택
- **media3 `MediaSessionService`** (ExoPlayer 내장 MediaSession 지원)
- `MediaSession` → 잠금화면/이어폰/블루투스 컨트롤 자동 연동
- Notification channel: `opic_playback`
- Android 13+ `POST_NOTIFICATIONS` 권한 처리
- Android 14+ `FOREGROUND_SERVICE_MEDIA_PLAYBACK` type 선언

### 3.3 알림 버튼
| 버튼 | 동작 |
|------|------|
| Play/Pause | 토글. 일시정지 ↔ 재개 |
| Stop | 반복재생 완전 종료 + 서비스 종료 + 알림 제거 |

### 3.4 수명주기
```
반복재생 시작 → AudioPlaybackService.start()
  → Foreground Notification 표시
  → ExoPlayer에 플레이리스트 로드 & 재생

화면 off / 앱 백그라운드 → 서비스 유지, 재생 계속

Stop 또는 앱 kill → ExoPlayer 해제 → stopForeground() + stopSelf()
```

### 3.5 AudioFocus
- 재생 시작 시 `AUDIOFOCUS_GAIN` 요청
- 다른 앱(음악 등) 재생 시 OPIc 양보 (duck 또는 pause)
- 포커스 복구 시 자동 재개

---

## 4. 하단 플레이 바 상세 스펙

### 4.1 표시 범위
| 화면 | 조건 | 하단 바 |
|------|------|---------|
| StudyListScreen | 반복재생 활성 | **RepeatPlayBar** (재생 컨트롤) |
| StudyListScreen | 반복재생 비활성 | 바 없음 |
| StudyDetailScreen | 반복재생 활성 | **RepeatPlayBar** (일시정지 상태) |
| StudyDetailScreen | 반복재생 비활성 | **기존 BottomAudioBar** (개별 오디오) |
| 기타 (Home, Settings) | 무관 | 바 없음 (Service는 유지) |

### 4.2 StudyListScreen 하단 바 UI (반복재생 활성 시)
```
┌──────────────────────────────────────┐
│ ▶ [현재 트랙 제목]        3/15       │
│ ━━━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━━━━ │
│ 1:23                         3:45    │
│  [0.75x] [1.0x] [1.5x]       ⏹     │
└──────────────────────────────────────┘
```

### 4.3 StudyDetailScreen 하단 바 (반복재생 활성 시)
```
┌──────────────────────────────────────┐
│ ⏸ 반복재생 일시정지 중     [▶ 재개]  │
│ [현재 트랙 제목] — 3/15              │
└──────────────────────────────────────┘
```
- Resume 버튼으로 상세에서도 반복재생 복귀 가능

### 4.4 상태 전환 로직
```
isRepeatActive == true  → RepeatPlayBar 표시
isRepeatActive == false → 기존 BottomAudioBar 표시 (StudyDetail만)
```
- `StudyViewModel.isRepeatActive: StateFlow<Boolean>`로 제어

---

## 5. 시험 복습 삭제 상세 스펙

### 5.1 삭제 UX 흐름
```
1. ReviewListScreen 항목을 좌측 스와이프
2. 빨간 삭제 버튼 노출 (배경 Red + 🗑 아이콘)
3. 삭제 버튼 탭
4. 확인 다이얼로그:
   "이 시험 복습을 삭제하시겠습니까?
    시험 결과와 답변이 모두 삭제됩니다."
   [취소]  [삭제]
5. [삭제] → 항목 collapse 애니메이션
6. Undo 스낵바 5초:
   "복습이 삭제되었습니다."  [되돌리기]
7-a. 5초 경과 → DB 영구 삭제
7-b. [되돌리기] 탭 → UI 복원 + 삭제 취소
```

### 5.2 삭제 데이터 범위
| 삭제 | 유지 |
|------|------|
| TestSession 레코드 | StudyProgress (학습 횟수, 즐겨찾기) |
| TestResult 레코드 (해당 세션 전체) | Question 데이터 |
| 녹음 파일 (있을 경우) | 다른 세션의 데이터 |

### 5.3 Undo 구현
- UI에서 즉시 제거 (optimistic)
- DB 삭제는 5초 **지연**
- Undo 시: UI 리스트에 다시 삽입 + 지연 삭제 취소
- 화면 이탈(ViewModel.onCleared) 시: 보류된 삭제 즉시 실행

### 5.4 기술 구현
- Material3 `SwipeToDismissBox` (endToStart만)
- `AlertDialog` 확인 다이얼로그
- `SnackbarHostState.showSnackbar()` + `SnackbarResult.ActionPerformed`
- `CoroutineScope.launch { delay(5000); actualDelete() }`

---

## 6. 아키텍처 변경

### 6.1 새로운 파일
| 파일 | 역할 |
|------|------|
| `service/AudioPlaybackService.kt` | Foreground Service. 전용 ExoPlayer + MediaSession + Notification |
| `ui/study/RepeatPlaybackState.kt` | 반복재생 상태 (playlist, currentIndex, filter, isPlaying) |
| `ui/components/RepeatPlayBar.kt` | 반복재생 전용 하단 바 컴포저블 |
| `ui/components/SwipeToDeleteRow.kt` | 스와이프 삭제 재사용 컴포저블 |

### 6.2 수정 파일
| 파일 | 변경 |
|------|------|
| `StudyViewModel.kt` | 반복재생 상태 관리, Service 바인딩, 필터→플레이리스트 빌드 |
| `StudyListScreen.kt` | 전체/구성/주제 필터 탭 + 반복재생 시작 버튼 + RepeatPlayBar |
| `StudyDetailScreen.kt` | 반복재생 시 녹음 비활성, RepeatPlayBar(일시정지), 자동 일시정지/재개 |
| `ReviewListScreen.kt` | SwipeToDelete + 확인 다이얼로그 + Undo 스낵바 |
| `ReviewViewModel.kt` | delete + undo 로직 |
| `TestDao.kt` | `deleteSession()`, `deleteResultsBySession()` 쿼리 |
| `TestRepository.kt` | `deleteSession()` 메서드 |
| `AndroidManifest.xml` | FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK 권한 + Service 등록 |
| `app/build.gradle.kts` | `media3-session` 의존성 추가 |
| `gradle/libs.versions.toml` | media3-session 라이브러리 항목 |

### 6.3 ExoPlayer 분리 전략
```
AudioPlayerManager (기존 @Singleton)
  └── 개별 문제 재생 전용 (StudyDetail, Test 등)

AudioPlaybackService (신규 ForegroundService)
  └── 반복재생 전용 ExoPlayer
      - 플레이리스트 관리
      - 3초 간격 자동 전환
      - MediaSession 연동
```
- **장점**: Service 수명주기 분리, 개별/반복 재생 충돌 없음
- **추가 메모리**: ~5MB (ExoPlayer 인스턴스 1개)

### 6.4 상태 흐름도
```
AudioPlaybackService (Foreground)
  ├── ExoPlayer (반복재생 전용)
  ├── MediaSession → Notification Play/Pause/Stop
  └── repeatState: StateFlow<RepeatPlaybackState>
         ↕ (BoundService + Binder)
StudyViewModel
  ├── audioPlayer (기존 @Singleton: 개별 재생)
  ├── bindService() → repeatState 관찰
  ├── startRepeat(filter) → Service 시작
  ├── pauseRepeat() / resumeRepeat() / stopRepeat()
  └── isRepeatActive: StateFlow<Boolean>
         ↓
  StudyListScreen / StudyDetailScreen
    └── RepeatPlayBar / BottomAudioBar 조건부 표시
```

---

## 7. 구현 단계

### Step A: Foreground Service 기반 (핵심)
1. `AndroidManifest.xml` 권한 + Service 등록
2. `media3-session` 의존성 추가
3. `AudioPlaybackService.kt` 생성
   - BoundService 패턴 (Binder로 ViewModel 연동)
   - ExoPlayer + 플레이리스트 순차 재생
   - 3초 간격 (`setRepeatMode` + custom `Player.Listener`)
   - MediaSession + Notification (Play/Pause/Stop)
   - AudioFocus 처리
4. `RepeatPlaybackState.kt` 데이터 클래스

### Step B: StudyViewModel 반복재생 연동
1. Service bind/unbind (lifecycle-aware)
2. `startRepeat(filterState)` → 필터링 → 플레이리스트 빌드 → Service 전달
3. `pauseRepeat()`, `resumeRepeat()`, `stopRepeat()`
4. `isRepeatActive`, `repeatPlaybackState` StateFlow 노출
5. 속도 변경 → Service에 전달

### Step C: RepeatPlayBar 컴포넌트
1. 재생 상태: 트랙 제목 + 인덱스 + 시크바 + 시간
2. Play/Pause/Stop 버튼
3. 속도 칩 (0.75x / 1.0x / 1.5x)
4. 일시정지 모드 (StudyDetail용)

### Step D: StudyListScreen 필터 + 반복재생 UI
1. 전체/구성/주제 탭 (기존 필터 칩 확장)
2. 구성 다중선택 칩 (Ad, Rp, 돌발, 선택)
3. 주제 단일선택 드롭다운/칩
4. 반복재생 시작/정지 버튼
5. RepeatPlayBar 하단 조건부 표시

### Step E: StudyDetailScreen 반복재생 연동
1. 진입 시 `pauseRepeat()` 호출
2. 녹음 버튼 비활성 (isRepeatActive 시)
3. 하단 바: RepeatPlayBar(일시정지) vs BottomAudioBar(개별)
4. 뒤로 가기 시 `resumeRepeat()` 호출

### Step F: 시험 복습 스와이프 삭제
1. `SwipeToDeleteRow.kt` 컴포넌트
2. `ReviewListScreen.kt` 적용
3. 확인 `AlertDialog`
4. `ReviewViewModel.kt` delete + undo 로직
5. `TestDao.kt` 삭제 쿼리
6. `TestRepository.kt` deleteSession
7. Undo 스낵바 (5초)

---

## 8. 검증 기준

### 반복재생
- [ ] 전체 필터: 모든 답변 오디오 순차 재생 확인
- [ ] 구성 다중선택: [Ad, Rp] 선택 시 해당 type만 재생
- [ ] 주제 단일선택: [여행] 선택 시 해당 set만 재생
- [ ] AND 조건: 구성 + 주제 교집합 확인
- [ ] 오디오 없는 문제 자동 건너뛰기
- [ ] 3초 간격 트랙 전환
- [ ] 속도 변경 즉시 반영
- [ ] 무한 반복 (마지막 → 처음 자동 순환)
- [ ] 녹음 버튼 비활성 + 토스트

### Foreground Service
- [ ] 앱 백그라운드에서 재생 유지
- [ ] 화면 꺼져도 재생 유지
- [ ] 알림 패널 Play/Pause 동작
- [ ] 알림 패널 Stop → 서비스 종료 + 알림 제거
- [ ] 알림 탭 → 앱으로 복귀
- [ ] 앱 swipe kill → 재생 정지
- [ ] AudioFocus: 다른 앱 재생 시 양보

### 하단 바
- [ ] StudyList: 반복재생 시 RepeatPlayBar 표시
- [ ] StudyDetail 진입: 자동 일시정지 + 일시정지 바
- [ ] StudyDetail Resume 버튼 동작
- [ ] 뒤로 가기: 자동 재개
- [ ] 반복재생 비활성: 기존 BottomAudioBar 표시

### 복습 삭제
- [ ] 좌측 스와이프 → 빨간 삭제 버튼 노출
- [ ] 삭제 버튼 탭 → 확인 다이얼로그
- [ ] [삭제] 선택 → collapse 애니메이션 + Undo 스낵바
- [ ] 5초 내 [되돌리기] → 항목 복원
- [ ] 5초 경과 → DB 영구 삭제 (Session + Results)
- [ ] StudyProgress 유지 확인

---

## 9. 위험 요소 및 대응

| 위험 | 영향도 | 대응 |
|------|--------|------|
| ExoPlayer 2 인스턴스 메모리 | 낮음 (~5MB) | minSdk 26 기기에서도 충분 |
| Android 14+ Foreground Service 제한 | 높음 | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` type 명시 |
| MediaSession 타 앱 충돌 | 중간 | AudioFocus 요청 + 양보 정책 |
| Service bind/unbind 타이밍 | 중간 | `ServiceConnection` + lifecycle-aware binding |
| Undo 중 화면 전환 | 중간 | `onCleared()`에서 보류 삭제 실행 |
| 스와이프 ↔ 가로 스크롤 충돌 | 낮음 | ReviewList에 가로 스크롤 없음 |
| POST_NOTIFICATIONS 권한 미허용 | 중간 | 알림 없이 재생은 가능, 컨트롤만 제한 |

---

## 10. 사용자 시나리오 (Q10 기반: 책상 액티브 학습)

```
1. 학습하기 진입 → StudyListScreen
2. 구성=[Ad, Rp] 선택 → 반복재생 시작 ▶
3. 답변 오디오 순차 재생 (1.0x, 3초 간격)
4. 하단 바에서 현재 트랙 확인 + 속도 조정
5. 특정 문제에서 따라 말하기 → 해당 문제 탭
6. StudyDetail 진입 → 반복재생 자동 일시정지
7. 해당 문제 답변 개별 재생 → 따라 말하기 → 녹음 → STT 비교
8. 뒤로 가기 → StudyList → 반복재생 자동 재개
9. 화면 끄고 이어폰으로 계속 청취 (Foreground Service)
10. 알림 패널에서 Stop → 학습 종료
```
