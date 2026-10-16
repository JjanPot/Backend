# feat/SAT-33 작업 내용 정리

> 브랜치: `feat/SAT-33` → `develop`
> 작업일: 2026-04-06

---

## 작업 개요

인증 게시글 신고 기능 구현, 신고 사유 단일 선택 변경, 사용자 차단 시 챌린지 처리(2인 강제 종료 / 3인 이상 프로필 마스킹), Swagger 문서 보강.

---

## 1. 인증 게시글 신고 기능

### 변경 내용
- `POST /api/reports/v1/certification` 게시글 신고 API 신규
- 기존 `report` 테이블에 `target_type`(USER/CERTIFICATION) 컬럼 추가하여 사용자/게시글 신고 통합 관리
- `certification` 테이블에 `is_hidden` 컬럼 추가 — 신고 즉시 비노출 처리
- 인증 피드 조회 시 `is_hidden = true`인 게시글 자동 필터링
- 본인 게시글 신고 방지 (`CERTIFICATION_SELF_REPORT`)
- 동일 챌린지 참여자만 신고 가능

### 신규/변경 파일
- `V19__add_target_type_to_report.sql` (신규) — report 테이블 확장 + certification.is_hidden 추가
- `ReportTargetType.java` (신규) — USER/CERTIFICATION enum
- `CreateCertificationReportRequest.java` (신규) — 게시글 신고 요청 DTO
- `Report.java` — targetType, certification 필드 추가, `ofUser()`/`ofCertification()` 팩토리 메서드
- `ReportService.java` — `reportCertification()` 메서드 추가 + 즉시 비노출 처리
- `ReportController.java` — `/certification` 엔드포인트 추가, 기존 사용자 신고 `/user`로 URL 변경
- `Certification.java` — `isHidden` 필드 + `hide()` 메서드 추가
- `CertificationRepository.java` — 피드 조회 쿼리에 `isHidden = false` 필터 추가
- `ErrorCode.java` — `CERTIFICATION_SELF_REPORT` 추가

### API
| Method | URL | 설명 |
|---|---|---|
| POST | `/api/reports/v1/user` | 사용자 신고 (기존, URL 변경) |
| POST | `/api/reports/v1/certification` | 게시글 신고 (신규) |

---

## 2. 신고 사유 단일 선택으로 변경

### 변경 내용
- 사용자 신고, 게시글 신고 모두 신고 사유를 **복수 선택 → 단일 선택**으로 변경
- `List<ReportReasonRequest> reasons` → `ReportReason reason`

### 변경 파일
- `CreateReportRequest.java` — 단일 reason 필드로 변경
- `CreateCertificationReportRequest.java` — 단일 reason 필드
- `ReportService.java` — `request.reasons().forEach(...)` → `report.addReason(request.reason())`

### 요청 예시
```json
// 사용자 신고
{
  "reportedUserId": 2,
  "challengeId": 1,
  "reason": "HARASSMENT_OR_DEFAMATION"
}

// 게시글 신고
{
  "certificationId": 1,
  "reason": "SPAM_OR_ADVERTISEMENT"
}
```

### 신고 사유 (ReportReason)
| enum 값 | 설명 |
|---|---|
| INAPPROPRIATE_BEHAVIOR | 부적절한 행동/콘텐츠 |
| SPAM_OR_ADVERTISEMENT | 스팸 또는 광고성 |
| FRAUD_OR_FALSE_INFORMATION | 사기 또는 허위 정보 |
| HARASSMENT_OR_DEFAMATION | 괴롭힘 또는 비방 |
| PRIVACY_VIOLATION | 개인정보 침해 |
| ETC | 기타 사유 |

---

## 3. 사용자 차단 시 챌린지 처리

### 변경 내용

**2인 챌린지:**
- 차단 즉시 챌린지 강제 종료 (CANCELLED)
- 결과 리포트 제공하지 않음

**3인 이상 챌린지:**
- 차단한 본인의 화면에서만 차단 유저 프로필 마스킹
  - 닉네임: "차단한 사용자"
  - 프로필 이미지: null
  - `isBlocked: true` 반환
- 절약 금액, 팀 총액, 달성률은 그대로 유지
- 인증 피드에서 차단 유저 게시글 숨김 (기존 구현)
- 다른 팀원에게는 정상 노출

### 변경 파일
- `BlockService.java` — 2인 챌린지 강제 종료 로직 추가
- `BlockRepository.java` — `findBlockedUserIdsByBlockerAndChallenge()` 메서드 추가
- `ChallengeMembersResponse.java` — `isBlocked` 필드 추가
- `ChallengeService.java` — 차단 유저 목록 조회 + 프로필 마스킹 반영

---

## 4. Swagger 문서 보강

### 변경 파일
- `BlockControllerDocs.java` — 2인/3인 이상 챌린지별 차단 후 동작 상세 설명
- `ChallengeControllerDocs.java` — 팀원 현황 API에 차단 유저 마스킹/회색 처리 가이드
- `CertificationControllerDocs.java` — 인증 피드 조회에 차단/신고 비노출 설명
- `ReportControllerDocs.java` — 단일 사유 선택 반영, 게시글 신고 명세 추가
