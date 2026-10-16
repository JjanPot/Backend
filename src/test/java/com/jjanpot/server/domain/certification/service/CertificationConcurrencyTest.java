package com.jjanpot.server.domain.certification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.jjanpot.server.domain.category.entity.Category;
import com.jjanpot.server.domain.category.entity.CategoryName;
import com.jjanpot.server.domain.category.repository.CategoryRepository;
import com.jjanpot.server.domain.certification.dto.request.CreateCertificationRequest;
import com.jjanpot.server.domain.certification.entity.Certification;
import com.jjanpot.server.domain.certification.entity.SpendType;
import com.jjanpot.server.domain.certification.repository.CertificationRepository;
import com.jjanpot.server.domain.challenge.entity.Challenge;
import com.jjanpot.server.domain.challenge.entity.ChallengeCategory;
import com.jjanpot.server.domain.challenge.entity.ChallengeStatus;
import com.jjanpot.server.domain.challenge.entity.ChallengeWeek;
import com.jjanpot.server.domain.challenge.repository.ChallengeCategoryRepository;
import com.jjanpot.server.domain.challenge.repository.ChallengeRepository;
import com.jjanpot.server.domain.challenge.repository.ChallengeWeekRepository;
import com.jjanpot.server.domain.team.entity.Team;
import com.jjanpot.server.domain.team.entity.TeamMembers;
import com.jjanpot.server.domain.team.entity.TeamType;
import com.jjanpot.server.domain.team.repository.TeamMembersRepository;
import com.jjanpot.server.domain.team.repository.TeamRepository;
import com.jjanpot.server.domain.user.entity.Provider;
import com.jjanpot.server.domain.user.entity.User;
import com.jjanpot.server.domain.user.repository.UserRepository;

/**
 * 팀원 동시 인증 시 팀 누적 절약 금액(ChallengeWeek.weekSavedAmount) 갱신 손실 검증.
 * 팀원 N명이 같은 챌린지에 1인당 K건씩 동시에 인증하고, 누적값이 인증 기록 합계와 일치하는지 비교한다.
 * - 8명 x 3건: 실제 정책 최대치 (팀 정원 8명, 하루 인증 3회)
 * - 100명 x 1건: 경합 강도를 높인 스트레스 조건
 * MySQL 검증: CONCURRENCY_DB_URL, SPRING_DATASOURCE_USERNAME/PASSWORD, SPRING_JPA_DATABASE_PLATFORM 환경변수로 지정
 */
@ActiveProfiles("test")
@SpringBootTest(properties =
	"spring.datasource.url=${CONCURRENCY_DB_URL:jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=30000}")
class CertificationConcurrencyTest {

	private static final int THREAD_COUNT = 32;
	private static final int DAILY_CERT_LIMIT = 3;
	private static final int CATEGORY_AMOUNT = 1_000; // NO_SPEND 인증 1건당 절약 금액
	private static final AtomicInteger FIXTURE_SEQ = new AtomicInteger();

	@MockitoBean
	private FirebaseMessaging firebaseMessaging;
	@MockitoBean
	private FirebaseApp firebaseApp;

	@Autowired
	private CertificationService certificationService;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private TeamMembersRepository teamMembersRepository;
	@Autowired
	private ChallengeRepository challengeRepository;
	@Autowired
	private ChallengeWeekRepository challengeWeekRepository;
	@Autowired
	private ChallengeCategoryRepository challengeCategoryRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private CertificationRepository certificationRepository;

	@ParameterizedTest(name = "팀원 {0}명 x {1}건")
	@CsvSource({"8, 3", "100, 1"})
	void withoutLock_lostUpdate(int memberCount, int certsPerMember) throws InterruptedException {
		Fixture f = createFixture(memberCount);

		AtomicInteger failures = runConcurrently(f.members(), certsPerMember, user -> transactionTemplate.executeWithoutResult(s -> {
			// 락 도입 전 경로: 일반 SELECT로 읽고 엔티티 값을 더해 덮어씀
			ChallengeWeek week = challengeWeekRepository.findByChallengeAndWeekNumber(f.challenge(), 1).orElseThrow();
			certificationRepository.save(Certification.create(f.challenge(), user, f.category(), week,
				SpendType.NO_SPEND, 0, CATEGORY_AMOUNT, "", null, f.spentAt()));
			week.addSavedAmount(CATEGORY_AMOUNT);
		}));

		Result r = result(f, failures, memberCount * certsPerMember);
		System.out.printf("[락 미적용][%d명x%d건] %s%n", memberCount, certsPerMember, r);
		// H2: 갱신 손실로 누적값 누락 / MySQL: FK 공유락 -> 배타락 승격 데드락으로 대부분 롤백 + 갱신 손실
		assertThat(r.weekSaved()).isLessThan(memberCount * certsPerMember * CATEGORY_AMOUNT);
	}

	@ParameterizedTest(name = "팀원 {0}명 x {1}건")
	@CsvSource({"8, 3", "100, 1"})
	void withPessimisticLock_noLostUpdate(int memberCount, int certsPerMember) throws InterruptedException {
		Fixture f = createFixture(memberCount);

		AtomicInteger failures = runConcurrently(f.members(), certsPerMember, user -> certificationService.createCertification(
			user.getUserId(),
			new CreateCertificationRequest(f.challenge().getChallengeId(), SpendType.NO_SPEND,
				f.category().getCategoryId(), null, null, f.spentAt()),
			null));

		Result r = result(f, failures, memberCount * certsPerMember);
		System.out.printf("[비관적 락][%d명x%d건] %s%n", memberCount, certsPerMember, r);
		int requests = memberCount * certsPerMember;
		assertThat(r.failures()).isZero();
		assertThat(r.certCount()).isEqualTo(requests);
		assertThat(r.weekSaved()).isEqualTo(r.certSum()).isEqualTo(requests * CATEGORY_AMOUNT);
	}

	@ParameterizedTest(name = "팀원 {0}명 x {1}건 동시 요청")
	@CsvSource({"1, 10", "8, 5"})
	void dailyLimit_notExceededUnderConcurrency(int memberCount, int certsPerMember) throws InterruptedException {
		Fixture f = createFixture(memberCount);

		AtomicInteger failures = runConcurrently(f.members(), certsPerMember, user -> certificationService.createCertification(
			user.getUserId(),
			new CreateCertificationRequest(f.challenge().getChallengeId(), SpendType.NO_SPEND,
				f.category().getCategoryId(), null, null, f.spentAt()),
			null));

		Map<Long, Long> perUser = certificationRepository.findAll().stream()
			.filter(c -> c.getChallenge().getChallengeId().equals(f.challenge().getChallengeId()))
			.collect(Collectors.groupingBy(c -> c.getUser().getUserId(), Collectors.counting()));
		long maxPerUser = perUser.values().stream().mapToLong(Long::longValue).max().orElse(0);
		System.out.printf("[일일 제한][%d명x%d건] 거절 %d건, 사용자별 저장 건수 %s, 최대 %d건 (정책 %d건)%n",
			memberCount, certsPerMember, failures.get(), perUser.values(), maxPerUser, DAILY_CERT_LIMIT);

		assertThat(maxPerUser).isLessThanOrEqualTo(DAILY_CERT_LIMIT);
	}

	private AtomicInteger runConcurrently(List<User> members, int certsPerMember, Consumer<User> task)
		throws InterruptedException {
		List<User> users = members.stream()
			.flatMap(u -> Collections.nCopies(certsPerMember, u).stream())
			.toList();
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch ready = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(users.size());
		AtomicInteger failures = new AtomicInteger();

		for (User user : users) {
			executor.submit(() -> {
				try {
					ready.await();
					task.accept(user);
				} catch (Exception e) {
					if (failures.incrementAndGet() == 1) {
						Throwable root = e;
						while (root.getCause() != null) {
							root = root.getCause();
						}
						System.out.printf("[첫 실패 원인] %s: %s%n", root.getClass().getSimpleName(), root.getMessage());
					}
				} finally {
					done.countDown();
				}
			});
		}
		ready.countDown(); // 모든 요청 동시 출발
		done.await(60, TimeUnit.SECONDS);
		executor.shutdown();
		return failures;
	}

	private Result result(Fixture f, AtomicInteger failures, int requests) {
		int weekSaved = challengeWeekRepository.findById(f.weekId()).orElseThrow().getWeekSavedAmount();
		List<Certification> certs = certificationRepository.findAll().stream()
			.filter(c -> c.getChallenge().getChallengeId().equals(f.challenge().getChallengeId()))
			.toList();
		int certSum = certs.stream().mapToInt(Certification::getSavedAmount).sum();
		return new Result(requests, failures.get(), certs.size(), certSum, weekSaved);
	}

	private Fixture createFixture(int memberCount) {
		String prefix = "CONC" + FIXTURE_SEQ.incrementAndGet();
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		Team team = teamRepository.save(Team.of(prefix, TeamType.FRIEND, memberCount));
		Challenge challenge = challengeRepository.save(Challenge.builder()
			.title("동시성 테스트").goalAmount(0).minPersonalGoalAmount(0)
			.status(ChallengeStatus.ONGOING)
			.startDate(now.minusDays(1)).endDate(now.plusDays(6))
			.team(team).build());
		ChallengeWeek week = challengeWeekRepository.save(
			ChallengeWeek.firstWeek(challenge, challenge.getStartDate(), challenge.getEndDate()));
		Category category = categoryRepository.save(Category.builder()
			.name(CategoryName.CAFE_DESSERT).defaultAmount((long)CATEGORY_AMOUNT).sortOrder(1).build());
		challengeCategoryRepository.save(ChallengeCategory.of(challenge, category, CATEGORY_AMOUNT));

		// 정원 검증은 참여 API 책임이므로 팀원을 직접 등록 (100명 시나리오는 정책 상한을 넘는 스트레스 조건)
		List<User> members = IntStream.range(0, memberCount)
			.mapToObj(i -> userRepository.save(User.create(Provider.KAKAO, prefix + "-" + i, "user" + i, null, null)))
			.toList();
		members.forEach(u -> teamMembersRepository.save(TeamMembers.ofMember(team, u)));

		return new Fixture(challenge, week.getWeekId(), category, members, now.minusMinutes(1));
	}

	private record Fixture(Challenge challenge, Long weekId, Category category, List<User> members,
						   LocalDateTime spentAt) {
	}

	private record Result(int requests, int failures, int certCount, int certSum, int weekSaved) {
		@Override
		public String toString() {
			int lost = certSum - weekSaved;
			return String.format("요청 %d건, 실패 %d건, 저장된 인증 %d건(합계 %,d원), 팀 누적 %,d원 → 누락 %,d원(%d건)",
				requests, failures, certCount, certSum, weekSaved, lost, lost / CATEGORY_AMOUNT);
		}
	}
}
