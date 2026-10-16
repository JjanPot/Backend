# 짠팟(Jjanpot) 백엔드 개발기 - 팀 절약 챌린지 앱

## 프로젝트 소개

짠팟은 팀 단위 절약 챌린지 앱입니다. 친구, 커플, 가족 등 2~8명이 팀을 이루어 일주일간 절약 목표를 설정하고, 매일 소비 인증을 통해 함께 절약하는 서비스입니다.

## 기술 스택

- **Backend**: Java 17, Spring Boot 3.x, JPA/Hibernate, QueryDSL
- **DB**: MySQL, Flyway (마이그레이션)
- **인증**: JWT, 소셜 로그인 (카카오/구글/애플)
- **인프라**: AWS EC2, RDS, S3, FCM (푸시 알림)
- **배포**: GitHub Actions CI/CD + Docker Hub + 블루그린 무중단 배포

## 내가 담당한 주요 기능

---

### 1. 챌린지 생성 및 팀 참여 (SAT-9)

프로젝트의 핵심 기능인 챌린지 생성 API를 구현했다. 챌린지 생성 시 팀 생성, 초대코드 발급, 카테고리 설정, 주차 생성이 하나의 트랜잭션으로 처리된다.

**초대코드 설계**: 6자리 랜덤 코드로, 혼동 가능한 문자(O/0/I/1/L)를 제외한 32가지 문자셋을 사용했다. `SecureRandom`으로 생성하고 중복 시 재생성하는 방식이다.

**인원수별 최소 목표 금액 정책**: `ChallengeMinGoalPolicy` 테이블로 관리하여, 팀 인원에 따라 적절한 최소 목표 금액을 검증한다.

---

### 2. 절약 인증 CRUD API (SAT-18)

챌린지의 핵심인 절약 인증 기능 전체를 구현했다. 팀원들이 매일 소비 내역을 인증하고, 기준 금액 대비 절약 금액을 산출하는 로직이다.

**인증 생성**: 카테고리별 기준 금액과 실제 소비 금액을 비교하여 절약 금액을 자동 계산한다. 하루 최대 3회 제한을 두었고, 인증 시 S3 이미지 업로드를 연동했다. 절약 금액은 `ChallengeWeek.weekSavedAmount`에 실시간 반영되며, 동시 인증 시 데이터 정합성을 위해 비관적 락을 적용했다.

```java
// 일일 인증 횟수 조회 - 배타적 상한(<)으로 경계값 이슈 해결
@Query("SELECT COUNT(c) FROM Certification c "
    + "WHERE c.user = :user AND c.challenge = :challenge "
    + "AND c.spentAt >= :start AND c.spentAt < :end")
int countByUserAndChallengeAndSpentAtRange(...);
```

**인증 수정/삭제**: 수정 시 기존 이미지는 트랜잭션 커밋 후 S3에서 삭제하고, 새 이미지 업로드 실패 시 롤백하여 고아 파일이 남지 않도록 처리했다. 삭제 시에는 연관된 좋아요 데이터를 먼저 삭제한 뒤 인증을 삭제한다.

**인증 피드 조회**: 챌린지 내 전체 인증을 최신순으로 조회하며, `isMe` 필드로 본인 게시글 여부를 구분하여 프론트에서 수정/삭제 버튼 노출 여부를 판단할 수 있게 했다.

**팀/개인 절약 현황 통계**: 홈화면에서 팀 인증평균, 참여율, 연속활동일과 개인 인증횟수, 참여율, 연속활동일을 조회하는 API를 구현했다. 연속활동일 계산은 기준일(오늘 또는 종료일)부터 역산하여 연속 인증일을 카운트하는 방식이다.

---

### 3. 동시성 문제 해결 - 비관적 락 (SAT-29)

코드 리뷰에서 TOCTOU(Time-of-Check to Time-of-Use) 동시성 문제를 지적받았다. 챌린지 생성과 초대코드 참여 시 "활성 챌린지가 있는지 조회 -> 없으면 생성"하는 과정에서, 동일 사용자의 병렬 요청이 둘 다 통과할 수 있는 문제였다.

```java
// 비관적 락으로 User row를 잠가 동일 사용자 요청을 직렬화
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.userId = :userId")
Optional<User> findByIdForUpdate(@Param("userId") Long userId);
```

`SELECT ... FOR UPDATE`로 User row를 잠가서 검증~저장 구간 전체를 직렬화했다. 낙관적 락 대신 비관적 락을 선택한 이유는, 충돌 시점이 커밋 이후(이미 데이터 저장 완료)라 롤백 처리가 복잡해지기 때문이다.

---

### 4. Docker UTC 환경 대응 - 시간대 이슈

배포 서버(Docker)에서 `LocalDateTime.now()`가 UTC 기준으로 동작해 스케줄러가 의도한 시간에 실행되지 않는 문제가 있었다. JVM 기본 시간대를 KST로 설정하고, 스케줄러에서 명시적으로 `ZoneId.of("Asia/Seoul")`를 사용하여 해결했다.

---

### 5. ClassCastException 수정 - java.sql.Date vs LocalDate

배포 서버에서 `java.sql.Date cannot be cast to java.time.LocalDate` 에러가 발생했다. JPQL의 `CAST(... AS date)` 결과가 DB 드라이버에 따라 `java.sql.Date`로 반환되는데, 코드에서 `List<LocalDate>`로 직접 받으려 해서 생긴 문제였다.

```java
// java.sql.Date와 java.time.LocalDate는 상속 관계가 없어 캐스팅 불가
// 반환 타입을 Object로 받고 명시적 변환으로 해결
.map(dateValue -> {
    if (dateValue instanceof LocalDate ld) return ld;
    return ((java.sql.Date) dateValue).toLocalDate();
})
```

---

### 6. 로그아웃 및 회원 탈퇴

**로그아웃**: RefreshToken 삭제 + FCM 디바이스 비활성화로 토큰 재발급과 푸시 알림을 동시에 차단했다.

**회원 탈퇴**: 진행 중인 챌린지가 있으면 탈퇴를 차단하고, FK 제약조건 순서에 맞게 8개 연관 테이블 데이터를 삭제 후 유저를 완전 삭제한다. 삭제 순서가 중요한데, 자식 테이블(좋아요 -> 인증 -> 결과 -> 알림 -> 팀 멤버십 -> 디바이스 -> 약관 -> 토큰)부터 부모 테이블(유저) 순으로 처리한다.

---

### 7. 개발용 카카오 로그인 테스트 페이지 (SAT-31)

팀원(알림 담당)이 FCM 푸시 알림 테스트를 위해 FCM 토큰이 등록된 유저가 필요했다. 앱 없이도 웹에서 카카오 로그인 + FCM 토큰 등록을 한 번에 할 수 있는 HTML 페이지를 만들었다.

처음에는 카카오 JS SDK의 팝업 방식으로 구현했으나 브라우저 팝업 차단에 걸려 리다이렉트 방식으로 변경했다. 이후 코드 리뷰에서 `client_secret`이 프론트엔드 JavaScript에 노출되는 보안 취약점을 지적받아, 인가코드 -> 토큰 교환 로직을 백엔드 엔드포인트(`POST /api/auth/v1/login/kakao/code`)로 이동시켰다.

---

### 8. S3 Presigned URL 발급 API (SAT-34)

iOS 앱 개발자 요청으로 이미지 업로드 방식을 개선했다. 기존에는 앱 -> 백엔드 -> S3로 이미지가 서버를 경유했지만, Presigned URL 방식으로 변경하여 앱이 S3에 직접 업로드하도록 했다.

```
1. 앱 -> GET /api/images/v1/presigned-url -> uploadUrl, imageUrl 응답
2. 앱 -> uploadUrl로 S3에 직접 PUT 업로드
3. 앱 -> 프로필 생성 API에 imageUrl 전달
```

서버 부하 감소와 업로드 속도 개선이라는 이점이 있다. 프로필 생성 API도 `MultipartFile` -> `profileImageUrl`(String)으로 변경했다.

---

### 9. 챌린지 결과 조회 API (SAT-33)

챌린지 종료 후 결과 리포트를 보여주는 API를 구현했다. 매일 자정 스케줄러가 종료된 챌린지의 팀/개인 결과를 계산하여 DB에 저장하고, 결과 조회 API는 이 데이터를 단순 조회하여 반환한다.

**스케줄러 수동 실행 API**: 테스트 시 자정까지 기다릴 수 없어 `POST /api/auth/v1/run-scheduler`를 만들어 즉시 스케줄러를 실행할 수 있게 했다. 이 과정에서 중복 실행으로 결과 데이터가 2건 생성되는 문제가 발생했고, 결과 존재 여부를 먼저 확인하는 방어 코드를 추가했다.

**팀 성공 조건**: "전원 개인 최소 금액 충족 AND 팀 공동 목표 금액 충족"이라는 엄격한 조건으로, 한 명이라도 개인 목표를 달성하지 못하면 팀 전체가 실패한다.

---

## 배포 과정에서 만난 이슈들

- **GitHub Push Protection**: FCM 서비스 계정 키(`jjanpot-fcm.json`)가 커밋에 포함되어 푸시가 차단됨 -> `.gitignore` 추가 + Git 히스토리에서 제거(force push)
- **Docker 환경변수**: 카카오 `client_secret` 등 민감 정보를 EC2의 `.env` 파일로 관리. `docker restart`는 새 환경변수를 반영하지 않으므로 `deploy.sh`로 컨테이너를 재생성해야 한다는 것을 배움
- **Flyway 체크섬 불일치**: 이미 적용된 마이그레이션 파일이 수정되어 발생. `repair-on-migrate: true` 설정으로 해결

---

## 회고

이번 프로젝트에서 가장 인상 깊었던 점은 **실제 배포 환경과 로컬 환경의 차이**에서 발생하는 문제들이었다. UTC vs KST 시간대 이슈, Docker 환경변수 관리, java.sql.Date vs LocalDate 타입 불일치 등 로컬에서는 재현되지 않는 버그들을 경험하며, 배포 환경을 고려한 개발의 중요성을 체감했다.

또한 코드 리뷰를 통해 TOCTOU 동시성 문제나 client_secret 노출 같은 보안/안정성 이슈를 사전에 발견하고 수정할 수 있었다. 혼자 개발할 때는 놓치기 쉬운 부분들이 팀 리뷰를 통해 잡히는 경험이 좋았다.
