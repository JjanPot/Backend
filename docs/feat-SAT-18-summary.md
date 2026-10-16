# feat/SAT-18 작업 내용 정리

> 브랜치: `feat/SAT-18` → `develop`
> 작업일: 2026-03-29 ~ 2026-03-30

---

## 작업 개요

인증(Certification) CRUD 기능 구현 및 S3 이미지 업로드 연동, 챌린지 팀원 절약 현황 조회 API 신설, 팀/개인 절약 통계 API 구현, 홈 화면 개인 절약 금액 추가, 온보딩 약관 동의 API 추가, Swagger 명세 보강.

---

## 1. 인증 CRUD API

### 변경 내용
- `POST /api/certifications/v1` 인증 생성 (기존 `Void` 반환 → `CreateCertificationResponse` 반환, 201 Created)
- `PUT /api/certifications/v1/{certificationId}` 인증 수정 신규 구현
- `DELETE /api/certifications/v1/{certificationId}` 인증 삭제 신규 구현
- `GET /api/certifications/v1/challenge/{challengeId}` 인증 피드 조회 (기존)

### 주요 로직
- 수정/삭제 시 `ChallengeWeek.weekSavedAmount` 자동 보정 (기존 절약금액 차감 → 새 금액 누적)
- 본인 인증만 수정/삭제 가능 (소유권 검증)
- 삭제 시 연관 좋아요(`CertificationLike`) 일괄 삭제
- 진행 중(ONGOING) 챌린지에서만 수정/삭제 가능

### 신규/변경 파일
- `CreateCertificationResponse.java` (신규) - 인증 생성/수정 응답 DTO
- `Certification.java` - `update()` 메서드 추가
- `CertificationService.java` - `updateCertification()`, `deleteCertification()` 추가, `createCertification()` 반환 타입 변경
- `CertificationController.java` - PUT, DELETE 엔드포인트 추가
- `CertificationControllerDocs.java` - Swagger 명세 추가
- `CertificationLikeRepository.java` - `deleteByCertification()` 추가
- `ErrorCode.java` - `CERTIFICATION_NOT_OWNER` 추가

---

## 2. 챌린지 팀원 절약 현황 조회 API (신규)

### 변경 내용
- `GET /api/challenges/v1/{id}/members` 신규 API
- 챌린지에 참여 중인 팀원별 닉네임, 프로필 이미지, 개인 절약 금액 조회
- `isMe` 필드로 본인 여부 구분
- 절약 금액은 `Certification` 테이블 실시간 SUM 쿼리 방식

### 신규/변경 파일
- `ChallengeMembersResponse.java` (신규) - 팀원 절약 현황 응답 DTO
- `CertificationRepository.java` - `sumSavedAmountPerUserByChallenge()` SUM 쿼리 추가
- `ChallengeService.java` - `getChallengeMembers()` 추가
- `ChallengeController.java` - `GET /{id}/members` 엔드포인트 추가
- `ChallengeControllerDocs.java` - Swagger 명세 추가

---

## 3. 팀/개인 절약 현황 통계 API 구현

### 변경 내용
- `GET /api/challenges/v1/{id}/stats` 기존 TODO 상태에서 로직 구현 완료
- 팀 절약 현황: 인증평균(정수, 버림), 참여율(%, 버림), 연속활동일(스트릭)
- 개인 절약 현황: 인증횟수, 참여율(%, 버림), 연속활동일(스트릭)

### 계산 로직
| 구분 | 항목 | 계산 방식 |
|---|---|---|
| 팀 | 인증평균 | 전체 인증 수 / 팀원 수 (정수, 버림) |
| 팀 | 참여율 | 인증 1회 이상 팀원 수 / 전체 팀원 수 x 100 (버림) |
| 팀 | 연속활동 | 모든 팀원이 인증한 연속 일수 (최근 기준 역산) |
| 개인 | 인증횟수 | 본인 주간 총 인증 횟수 |
| 개인 | 참여율 | 인증한 날 수 / 경과일 수 x 100 (버림) |
| 개인 | 연속활동 | 본인 연속 인증일 (최근 기준 역산) |

### 신규/변경 파일
- `ChallengeStatsResponse.java` - `avgCertificationCount` 타입 `double` → `int` 변경
- `CertificationRepository.java` - 통계 쿼리 3개 추가 (`countCertPerUserByChallenge`, `findDistinctCertDatesByUser`, `countDistinctUsersByDateForChallenge`)
- `ChallengeService.java` - `getChallengeStats()` 로직 구현, streak 계산 헬퍼 메서드 추가

---

## 4. 홈 화면 개인 절약 금액 추가

### 변경 내용
- `GET /api/challenges/v1/current` ONGOING 응답에 `personalWeekSavedAmount` 필드 추가
- `Certification` 테이블에서 로그인 유저의 절약 금액 SUM 조회

### 신규/변경 파일
- `CurrentChallengeResponse.java` - `OngoingInfo`에 `personalWeekSavedAmount(int)` 필드 추가, 팩토리 메서드 시그니처 변경
- `ChallengeService.java` - `getCurrentChallenge()`에서 개인 절약 금액 SUM 조회 로직 추가
- `ChallengeControllerDocs.java` - Swagger 문서에 `personalWeekSavedAmount` 설명 추가

---

## 5. DTO 타입 정리

### 변경 내용
- 금액 관련 엔티티/DTO 필드 `Long` → `int` 통일 (`Certification.spendAmount`, `savedAmount`)
- Request DTO: nullable 필드는 래퍼 타입 유지 (`spentAmount: Integer`, `categoryId: Long`)
- Response DTO: 무조건 null이 아닌 필드만 원시형 사용

---

## 6. 인증 서비스 단위 테스트

### 변경 내용
- `CertificationServiceTest.java` (신규) - 총 19개 테스트 케이스

| 구분 | 테스트 수 | 주요 검증 |
|---|---|---|
| 인증 생성 | 8개 | SPEND/NO_SPEND 계산, 일일 제한, 카테고리 검증, 기간 검증, 소비금액 누락 |
| 인증 수정 | 5개 | 금액 재계산, 주차 절약금액 보정, 타입 변경, 소유권 검증 |
| 인증 삭제 | 4개 | 절약금액 차감, 좋아요 삭제, 소유권 검증 |
| 피드 조회 | 2개 | 좋아요 수 포함 응답, 팀원 권한 검증 |

---

## 7. 인증 이미지 S3 업로드 연동

> 커밋: `25e9ca6` (2026-03-30)

### 변경 내용
- 인증 생성/수정 API를 `multipart/form-data` 방식으로 변경
- 이미지 파일을 서버에서 S3로 직접 업로드 후 URL을 DB에 저장
- 생성: 이미지 첨부 시 S3 업로드, 미첨부 시 `null`
- 수정: 새 이미지 첨부 시 기존 S3 이미지 삭제 → 새 이미지 업로드, 미첨부 시 기존 유지
- 삭제: 인증 삭제 시 S3 이미지 함께 삭제
- 이미지 형식 검증: JPEG, PNG, WEBP만 허용
- Multipart 크기 제한: 파일 최대 10MB, 요청 최대 20MB

### 신규/변경 파일
- `CertificationController.java` - `@RequestBody` → `@RequestPart("request")` + `@RequestPart("image")` 변경
- `CertificationControllerDocs.java` - Swagger 명세에 이미지 파라미터 추가
- `CreateCertificationRequest.java` - `imageUrl` 필드 제거 (서버가 직접 업로드)
- `CertificationService.java` - `FileUploader` 주입, `uploadImage()` private 메서드 추가, 생성/수정/삭제에 S3 처리 로직 추가
- `FileUploader.java` - `deleteImage(String imageUrl)` 메서드 추가
- `S3Uploader.java` - `deleteImage()` 구현 (URL에서 key 추출 후 S3 삭제)
- `ErrorCode.java` - `IMAGE_INVALID_FORMAT`, `IMAGE_UPLOAD_FAILED` 추가
- `application.yml` - `spring.servlet.multipart` 설정 추가
- `CertificationServiceTest.java` - `FileUploader` mock 추가, 시그니처 변경 반영

### S3 키 구조
```
images/certification/{UUID}.{확장자}
예: images/certification/67fe91dc-966a-4131-9ffa-1278d9d9ead3.png
```

### Postman 테스트 방법
- Body → `form-data` 선택
- `request` (Text, Content-Type: `application/json`) — JSON 본문
- `image` (File) — 이미지 파일 선택 (선택사항)

---

## 8. 챌린지 스케줄러 timezone 명시

### 변경 내용
- `ChallengeScheduler.java`의 `@Scheduled` cron에 `zone = "Asia/Seoul"` 추가
- EC2 서버 시간대와 관계없이 한국 자정(KST 00:00)에 스케줄러 실행 보장

### 변경 파일
- `ChallengeScheduler.java` - `transitionWaitingToOngoing()`, `transitionOngoingToFinished()` 모두 zone 추가

---

## 9. 온보딩 약관 동의 API 추가 및 Swagger 명세 보강

> 커밋: `0831777` (2026-03-30)

### 변경 내용
- `POST /api/users/v1/onboarding/agreement` 약관 동의 API 엔드포인트 신규 추가
- `UserControllerDocs.java` (신규) - User 온보딩 API Swagger 명세 인터페이스 생성
- `UserController.java` - `implements UserControllerDocs` 적용
- 온보딩 관련 Request/Response DTO에 `@Schema` 어노테이션 추가
- `ProfileCreateRequest.java` - `@NotBlank`, `@NotNull`, `@Size(max=10)` Bean Validation 추가
- `UserAgreementRequest.java` - 필수/선택 구분, 설명 `@Schema` 추가
- `CertificationFeedResponse.java` - `imageUrl` example을 S3 URL 형식으로 변경

### 신규/변경 파일
- `UserControllerDocs.java` (신규) - 약관 동의, 프로필 생성, 초대코드 참여 Swagger 명세
- `UserController.java` - `agreeToTerms()` 엔드포인트 추가, Docs 인터페이스 적용
- `ProfileCreateRequest.java` - `@Schema`, Bean Validation 추가
- `ProfileCreateResponse.java` - `@Schema` 추가
- `UserAgreementRequest.java` - `@Schema` 추가
- `CertificationFeedResponse.java` - imageUrl example 변경

---

## 10. 코드 리뷰 반영 — S3 트랜잭션 안전성, streak 계산, JPQL, 예외 처리 개선

> 2026-03-30

### S3 트랜잭션 안전성 개선
- S3 업로드/삭제를 DB 트랜잭션과 분리하여 데이터 불일치 방지
- `deleteImageAfterCommit()` — 기존 이미지 삭제는 트랜잭션 **커밋 후** 실행. 롤백 시 원본 이미지 보존
- `deleteImageOnRollback()` — 새로 업로드한 이미지는 트랜잭션 **롤백 시** 정리. 커밋 성공 시 유지
- `TransactionSynchronizationManager.registerSynchronization()` 활용

| 동작 | Before | After |
|------|--------|-------|
| 생성 롤백 시 | S3에 고아 파일 남음 | 롤백 시 업로드된 이미지 정리 |
| 수정 시 기존 이미지 | 트랜잭션 안에서 즉시 삭제 → 롤백 시 복구 불가 | 커밋 후에만 삭제 |
| 삭제 시 이미지 | 트랜잭션 안에서 즉시 삭제 → 롤백 시 참조 어긋남 | 커밋 후에만 삭제 |

### 인증 수정 시 일일 3회 제한 검증 추가
- 수정 시 `spentAt` 날짜가 변경된 경우 이동 대상 날짜의 인증 횟수 검증 추가
- 같은 날짜 내 수정은 검증 스킵 (불필요한 쿼리 방지)

### streak 연속활동일 계산 수정
- `calculateStreakFromEnd()` → `calculateStreakFromDate()` 변경
- 기준일(오늘 또는 챌린지 종료일)에 인증이 없으면 즉시 0 반환
- Before: 마지막 인증일 기준 역산 → 과거 연속 구간이 streak로 과대 집계
- After: effectiveEnd 기준 역산 → 오늘 인증 없으면 streak = 0

### weekSavedAmount 비관적 잠금 (Pessimistic Lock) 적용
- 여러 팀원이 동시에 인증 생성/수정/삭제 시 `ChallengeWeek.weekSavedAmount`가 덮어씌워지는 Lost Update 문제 방지
- `ChallengeWeekRepository`에 `findByChallengeAndWeekNumberForUpdate()` 추가 (`@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`)
- 인증 생성/수정/삭제에서 `weekSavedAmount` 변경 시 잠금 조회로 순차 처리

| 상황 | Before | After |
|------|--------|-------|
| A(+5,000), B(+3,000) 동시 인증 | 마지막 커밋이 덮어씀 → 13,000 (5,000 유실) | FOR UPDATE로 순차 처리 → 18,000 ✅ |

### 일일 인증 횟수 BETWEEN 경계값 수정
- `countByUserAndChallengeAndSpentAtBetween` (BETWEEN, 양쪽 포함) → `countByUserAndChallengeAndSpentAtRange` (`>= start AND < end`, 상한 배타적) 변경
- 자정(00:00:00) 인증이 양일에 중복 카운트되는 오프바이원 문제 해결

### JPQL CAST 수정
- `CAST(c.spentAt AS LocalDate)` → `CAST(c.spentAt AS date)` (5곳)
- `LocalDate`는 Java 타입이지 Hibernate 매핑 타입이 아니므로 쿼리 파싱 실패 가능

### 예외 처리 통일 및 개선
- `StorageException` 삭제 → `BusinessException(ErrorCode.XXX)`으로 통일
- `S3Uploader`: upload는 `IMAGE_UPLOAD_FAILED`, delete는 `IMAGE_DELETE_FAILED` 사용
- `GlobalExceptionHandler`에 `MaxUploadSizeExceededException` 핸들러 추가 (파일 크기 초과 시 500 → 400 응답)
- `ErrorCode` 추가: `IMAGE_SIZE_EXCEEDED`, `IMAGE_DELETE_FAILED`

### 변경 파일
- `CertificationService.java` — S3 트랜잭션 분리, 수정 시 일일 제한 검증 추가, 비관적 잠금 조회 적용
- `ChallengeService.java` — streak 계산 로직 수정
- `ChallengeWeekRepository.java` — `findByChallengeAndWeekNumberForUpdate()` 추가
- `CertificationRepository.java` — JPQL CAST 수정, BETWEEN → 배타적 상한 쿼리 변경
- `S3Uploader.java` — `StorageException` → `BusinessException` 변경
- `StorageException.java` — 삭제
- `GlobalExceptionHandler.java` — `MaxUploadSizeExceededException` 핸들러 추가
- `ErrorCode.java` — `IMAGE_SIZE_EXCEEDED`, `IMAGE_DELETE_FAILED` 추가

---

## API 엔드포인트 요약

| Method | URL | 설명 | 상태 |
|---|---|---|---|
| POST | `/api/certifications/v1` | 인증 생성 (multipart/form-data) | S3 이미지 업로드 연동 |
| PUT | `/api/certifications/v1/{id}` | 인증 수정 (multipart/form-data) | S3 이미지 교체 연동 |
| DELETE | `/api/certifications/v1/{id}` | 인증 삭제 | S3 이미지 삭제 연동 |
| GET | `/api/certifications/v1/challenge/{challengeId}` | 인증 피드 조회 | 기존 |
| GET | `/api/challenges/v1/{id}/members` | 챌린지 팀원 절약 현황 | 신규 |
| GET | `/api/challenges/v1/{id}/stats` | 팀/개인 절약 통계 | 구현 완료 |
| GET | `/api/challenges/v1/current` | 현재 챌린지 (홈) | 개인 절약 금액 추가 |
| POST | `/api/users/v1/onboarding/agreement` | 약관 동의 | 신규 |
| POST | `/api/users/v1/onboarding/profile` | 프로필 생성 | Swagger 명세 추가 |
| POST | `/api/users/v1/onboarding/invite-code` | 초대코드 참여 | Swagger 명세 추가 |
