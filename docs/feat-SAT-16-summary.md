# feat/SAT-16 작업 내용 정리

> 브랜치: `feat/SAT-16` → `develop`
> 작업일: 2026-03-21 ~ 2026-03-22

---

## 작업 개요

카테고리 조회 API, 챌린지 취소/상세보기/홈 대시보드 API, 챌린지 종료 스케줄러 구현을 포함한 챌린지 핵심 기능 완성 작업.

---

## 1. 카테고리(절약 항목) 조회 API

### 변경 내용
- `GET /api/categories/v1` 카테고리 목록 + 금액 선택지 조회 API 구현
- `CategoryController`, `CategoryService`, `CategoryResponse`, `CategoryRepository`, `CategoryAmountOptionRepository`, `CategoryControllerDocs` 신규 생성
- `CategoryName` ENUM 기획 변경 반영
  - `SHOPPING` 제거, `FASHION_BEAUTY` 추가 (기존 `FASHION/BEAUTY` → 슬래시 제거)
  - `displayName` 한국어 표기 추가
- `CategoryName`, `ChallengeStatus`, `TeamType`, `SpendType`, `TeamRole` 전체 ENUM에 `displayName` 필드 추가
- Flyway 마이그레이션 데이터(`V2`) 카테고리 항목 수정

### 관련 커밋
- `796d22d` feat: 카테고리(절약항목) 조회 api
- `5602c8e` feat: ENUM 파일에 displayName 추가
- `7f97416` refactor: 카테고리(절약 항목) 기획 수정 반영 및 설명 추가
- `80903c5` refactor: ENUM 파일에 displayName 추가 (SpendType, TeamRole)
- `3dd5b9b` fix: 카테고리(절약 항목) 수정
- `1419422` chore: 카테고리(절약 항목) 수정

---

## 2. 챌린지 핵심 API

### 구현된 API
| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/challenges/v1/{challengeId}/cancel` | 챌린지 취소 (팀장 전용) |
| `GET` | `/api/challenges/v1/{challengeId}` | 챌린지 상세보기 |
| `GET` | `/api/challenges/v1/current` | 홈 화면 챌린지 대시보드 |

### 변경 내용
- **챌린지 취소**: `CANCELLED` 상태 추가 (`V4` 마이그레이션), 팀장 권한 검증
- **상세보기**: 카테고리별 절약 금액, 주차 정보, 팀원 목록 포함 `ChallengeDetailResponse` 구현
- **홈 대시보드**: 챌린지 상태(`WAITING` / `ONGOING` / 없음)에 따라 응답 분기 처리
  - `WAITING`: 시작 전 정보 표시
  - `ONGOING`: 주차별 절약 현황, 팀/개인 달성률 포함
- **챌린지 생성 확장**: 제목(`title`), 설명(`description`) 필드 추가
- **예외처리 추가**: 시작일이 과거 날짜일 때 `INVALID_CHALLENGE_DATE` 에러 반환
- Swagger `@Schema` 설명 전체 추가 (`CreateChallengeRequest`, `ChallengeDetailResponse`, `CurrentChallengeResponse`)
- `ChallengeStatsResponse` DTO 생성 (팀/개인 절약 현황 응답용)
- `ChallengeControllerV1` → `ChallengeController` 네이밍 정리

### 관련 커밋
- `f1ef367` feat: 챌린지 취소, 상세보기, 홈 화면(대시보드) 관련 api
- `190a8ab` feat: 챌린지 DTO에 @Schema 설명 추가
- `9560142` feat: 챌린지 취소 상태 추가
- `f015c50` feat: 챌린지 관련 ErrorCode 추가
- `003493f` feat: 챌린지 생성 시 챌린지 제목, 설명 입력 추가
- `e31376f` feat: 챌린지 시작일 과거 날짜 입력 시 예외처리
- `ba37614` feat: 팀/개인 절약 현황 응답 Dto 파일 생성
- `78ffc39` refactor: 홈 화면 챌린지 대시보드에서 챌린지 상태에 따른 로직 세분화
- `3ba69fc` feat: 팀 이름 표시 제외
- `6e09c72` fix: 인증 생성/삭제 검증 제거
- `a87186a` fix: 컨트롤러 이름 변경 - V1 제거

---

## 3. 챌린지 종료 스케줄러

### 변경 내용
- **`ChallengeScheduler`에 `transitionOngoingToFinished()` 추가**
  - 매일 자정(`0 0 0 * * *`) 실행
  - `end_date`가 지난 `ONGOING` 챌린지를 `COMPLETED` 또는 `FAILED`로 자동 전환
  - 전환 시 개인/팀 결과 레코드 동시 저장
- **개인 결과 산출 (`ChallengeMemberResult`)**
  - `totalSavedAmount`: 개인 누적 절약 금액
  - `totalCertCount`: 개인 총 인증 횟수
  - `isPersonalSuccess`: 개인 최소 목표 금액 충족 여부
  - `isRuleViolated`: 주간 최소 인증 2회 미달 여부
  - `weeklyParticipationRate`: 인증한 날 수 / 7일 × 100
- **팀 결과 산출 (`ChallengeTeamResult`)**
  - `totalSavedAmount`: `ChallengeWeek.weekSavedAmount` (인증 시 실시간 누적)
  - `isTeamSuccess`: 전원 개인 목표 충족 AND 팀 공동 목표 금액 달성
  - `achievementRate`: 팀 목표 달성률 (%)
  - `avgWeeklyCertCount` / `avgWeeklyParticipationRate`: 팀 평균 지표
- **신규 Repository 추가**
  - `CertificationRepository`: `findAllByChallengeAndUser`
  - `ChallengeMemberResultRepository`
  - `ChallengeTeamResultRepository`
- **`ChallengeRepository`** 쿼리 추가: `findAllByStatusAndEndDateBefore`
- **`certification` 테이블 `created_at` 컬럼 인덱스 추가** (SQL + 엔티티 `@Index`)

### 관련 커밋
- `970b05c` feat: 챌린지 종료 스케줄러 및 결과 저장 로직 추가
- `68202ed` feat: certification 테이블의 created_at 컬럼에 인덱스 추가

---

## 4. 기타 수정

- **토큰 설정 yml 이동**: JWT 토큰 수명 및 재발급 기준값을 코드에서 `application.yml`로 분리
- **단위 테스트 추가**: 챌린지 시작일 과거 날짜 예외처리 테스트 포함 (`ChallengeServiceTest`)

### 관련 커밋
- `a7470fb` refactor: 토큰 수명 및 재발급 기준 설정을 yml로 이동 반영

---

## 변경 파일 목록 (주요)

```
domain/category/
  controller/CategoryController.java              (신규)
  controller/docs/CategoryControllerDocs.java     (신규)
  dto/CategoryResponse.java                       (신규)
  repository/CategoryRepository.java              (수정)
  repository/CategoryAmountOptionRepository.java  (신규)
  service/CategoryService.java                    (신규)
  entity/CategoryName.java                        (수정)

domain/challenge/
  controller/ChallengeController.java             (수정)
  controller/docs/ChallengeControllerDocs.java    (수정)
  dto/request/CreateChallengeRequest.java         (수정)
  dto/response/ChallengeDetailResponse.java       (신규)
  dto/response/CurrentChallengeResponse.java      (신규)
  dto/response/ChallengeStatsResponse.java        (신규)
  entity/ChallengeStatus.java                     (수정)
  entity/ChallengeWeek.java                       (수정)
  repository/ChallengeRepository.java             (수정)
  repository/ChallengeMemberResultRepository.java (신규)
  repository/ChallengeTeamResultRepository.java   (신규)
  scheduler/ChallengeScheduler.java               (신규)
  service/ChallengeService.java                   (수정)

domain/certification/
  entity/Certification.java                       (수정 - 인덱스 추가)
  repository/CertificationRepository.java         (신규)

domain/team/
  entity/Team.java                                (수정)
  entity/TeamType.java                            (수정)
  entity/TeamRole.java                            (수정)
  repository/TeamMembersRepository.java           (수정)

domain/certification/
  entity/SpendType.java                           (수정)

global/exception/ErrorCode.java                   (수정)

resources/db/migration/
  V1__init_schema.sql                             (수정 - 인덱스 추가)
  V2__insert_initial_data.sql                     (수정 - 카테고리 데이터)
  V4__add_cancelled_challenge_status.sql          (신규)
```
