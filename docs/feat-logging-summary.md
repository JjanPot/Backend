# 운영 로그 추가 작업 내용 정리

> 작업일: 2026-04-06

---

## 작업 개요

핵심 비즈니스 이벤트에 대한 운영 로그를 추가하여, 배포 환경에서 사용자 행동 추적 및 문제 발생 시 원인 분석이 가능하도록 개선.

---

## 추가된 로그 목록

### 신고/차단 (운영 추적 필수)

| 서비스 | 로그 | 포맷 |
|---|---|---|
| ReportService | 사용자 신고 | `[사용자 신고] reporterId={}, reportedId={}, challengeId={}, reason={}` |
| ReportService | 게시글 신고 | `[게시글 신고] reporterId={}, certificationId={}, certOwnerId={}, challengeId={}, reason={}, 비노출처리=true` |
| BlockService | 사용자 차단 | `[사용자 차단] blockerId={}, blockedId={}, challengeId={}` |
| BlockService | 2인 챌린지 강제 종료 | `[2인 챌린지 강제 종료] challengeId={}, blockerId={}, blockedId={}` |

### 인증 CRUD (데이터 변경 추적)

| 서비스 | 로그 | 포맷 |
|---|---|---|
| CertificationService | 인증 생성 | `[인증 생성] userId={}, challengeId={}, spendType={}, savedAmount={}` |
| CertificationService | 인증 수정 | `[인증 수정] userId={}, certificationId={}, newSavedAmount={}` |
| CertificationService | 인증 삭제 | `[인증 삭제] userId={}, certificationId={}, challengeId={}` |

### 챌린지 생명주기 (비즈니스 이벤트)

| 서비스 | 로그 | 포맷 |
|---|---|---|
| ChallengeService | 챌린지 생성 | `[챌린지 생성] userId={}, challengeId={}, teamId={}, goalAmount={}` |
| ChallengeService | 챌린지 취소 | `[챌린지 취소] userId={}, challengeId={}` |

### 유저 생명주기 (중요 이벤트)

| 서비스 | 로그 | 포맷 |
|---|---|---|
| UserService | 초대코드 참여 | `[초대코드 참여] userId={}, teamId={}, challengeId={}, inviteCode={}` |
| UserService | 회원 탈퇴 | `[회원 탈퇴] userId={}` |

---

## 변경 파일

| 파일 | 변경 |
|---|---|
| `ReportService.java` | `@Slf4j` 추가, 사용자/게시글 신고 로그 |
| `BlockService.java` | `@Slf4j` 추가, 차단 + 2인 강제 종료 로그 |
| `CertificationService.java` | 인증 생성/수정/삭제 로그 |
| `ChallengeService.java` | 챌린지 생성/취소 로그 |
| `UserService.java` | 초대코드 참여/회원 탈퇴 로그 |

---

## 기존 로깅 인프라 (변경 없음)

| 구성 | 설명 |
|---|---|
| `GlobalExceptionHandler` | 모든 예외를 레벨별로 로깅 (warn: 4xx, error: 5xx) |
| `TraceIdAspect` | 모든 요청에 traceId를 MDC에 세팅 → 로그에 자동 포함 |
| `ServiceExceptionLoggingAspect` | BusinessException 외 예상치 못한 서비스 예외 자동 로깅 |
| `ChallengeScheduler` | 스케줄러 실행 건수 및 상태 전환 로깅 |

---

## 로그 확인 방법

```bash
# EC2에서 실시간 로그 확인
docker logs -f jjanpot-8081

# 특정 이벤트 필터링
docker logs jjanpot-8081 2>&1 | grep "사용자 신고"
docker logs jjanpot-8081 2>&1 | grep "게시글 신고"
docker logs jjanpot-8081 2>&1 | grep "사용자 차단"
docker logs jjanpot-8081 2>&1 | grep "회원 탈퇴"

# traceId로 특정 요청 추적
docker logs jjanpot-8081 2>&1 | grep "traceId값"
```
