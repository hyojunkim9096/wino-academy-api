# 🔌 API 가이드 (API Guide)

> **Base URL**: `/api`
> **Content-Type**: `application/json`
> **Character Set**: `UTF-8`

WINO Academy API는 RESTful 원칙을 따르며, 철저한 보안 검증과 일관된 응답 포맷을 제공합니다.
본 문서는 프론트엔드 개발 및 외부 시스템 연동을 위한 **핵심 API 명세**입니다.

---

## 1. 인증 및 공통 규약 (Authentication & Common)

### 1.1. 인증 (Authentication)
모든 관리자 API 요청은 **JWT Access Token**을 HTTP Header에 포함해야 합니다.

```http
Authorization: Bearer <YOUR_ACCESS_TOKEN>

---

### 📂 2. `docs/functional-spec.md` (기능 명세서 - 전체)

```markdown
# 📝 핵심 기능 명세서 (Key Functional Specifications)

본 문서는 **WINO Academy Solution**의 주요 비즈니스 로직과 이를 구현하기 위해 적용된 기술적 해결 전략(Technical Strategy)을 상세히 기술합니다.

---

## 1. 학사 관리 (Academic Management)

### 1.1. 정규학기 유일성 보장 (Unique Active Regular Semester)
* **Requirement**:
    * 학부(초/중/고) 별로 '활성(Active)' 상태인 '정규학기(Regular)'는 시스템 내에 **단 하나만 존재**해야 합니다.
    * (단, 방학 특강 등 '시험대비(Exam Prep)' 학기는 중복이 가능합니다.)
* **Solution**:
    * `SemesterService.upsert` 트랜잭션 내에서 저장 직전 DB 조회를 수행합니다.
    * 해당 학부에 이미 활성 정규학기가 존재할 경우 `IllegalStateException`을 발생시켜 트랜잭션을 롤백하고 데이터 무결성을 강제합니다.

### 1.2. 학기 마감 및 데이터 스냅샷 (Snapshot & Restore)
* **Challenge**:
    * 학기가 종료되면 학생들의 반 배정과 시간표를 초기화해야 하지만, 과거 이력은 조회 및 복구가 가능해야 합니다.
* **Implementation Strategy**:
    1.  **Snapshot**: 현재의 `Course`, `Subject`, `Timeslot` 데이터를 `_hist` (이력) 테이블에 `SNAP` 이벤트 타입으로 일괄 복사합니다. 이때 MySQL의 `INSERT INTO ... SELECT ...` 구문을 사용하여 성능을 최적화합니다.
    2.  **Close**: 메인 테이블의 연관관계(배정, 강사 등)를 `NULL` 처리하거나 삭제하여 다음 학기 배정을 준비합니다.
    3.  **Restore**: 운영 실수 시, 특정 시점의 스냅샷 데이터를 메인 테이블로 역방향 복사(Rollback)하는 기능을 제공합니다. 안전을 위해 복원 직전 현재 상태를 한 번 더 스냅샷 찍는 옵션(`preSnapshot`)을 기본으로 제공합니다.

---

## 2. 수납 및 정산 (Billing System)

### 2.1. 대량 청구 생성 (Batch Billing)
* **Requirement**:
    * 매월 지정된 날짜에 수천 명의 전체 재원생을 대상으로 수강료 청구서를 일괄 생성해야 합니다.
* **Tech Strategy**:
    * **Preview (Dry-run)**: 실제 DB 쓰기 전, 대상자와 예상 청구 금액을 미리 계산하여 UI에 표시함으로써 운영자의 실수를 방지합니다.
    * **Chunk Processing**: 전체 데이터를 1000건 단위 Chunk로 나누어 처리합니다. 각 Chunk는 `REQUIRES_NEW` 트랜잭션으로 분리되어, 한 건의 데이터 오류가 전체 배치를 롤백시키지 않도록 격리합니다.
    * **Optimization**: JPA의 N+1 문제를 회피하기 위해 Native Query를 활용하여 조회 성능을 극대화하고, `EXISTS` 서브쿼리로 중복 생성을 원천 차단합니다.

### 2.2. 멱등성 보장 (Idempotency)
* **Challenge**:
    * 네트워크 지연이나 사용자의 더블 클릭으로 인한 중복 결제 및 청구 생성 방지.
* **Solution**:
    * 클라이언트가 요청 시 고유한 `X-Idempotency-Key` 헤더를 생성하여 전송합니다.
    * 서버는 `IdempotencyService`를 통해 해당 키를 메모리(또는 Redis)에 캐싱하고, 일정 시간 내 동일 키 요청을 즉시 거부합니다.
    * 최후의 보루로 DB 레벨에서 `(student_id, bill_month, price_id)` 복합 유니크 인덱스를 통해 데이터 중복을 방어합니다.

---

## 3. 보안 및 시스템 (Security & System)

### 3.1. 이중 세션 제어 (Dual Session Control)
* **Concept**: Stateless한 JWT의 한계(즉시 만료 불가)를 보완하기 위해 DB 기반의 Stateful 세션을 결합한 하이브리드 방식입니다.
* **Flow**:
    1. 로그인 시 JWT 발급과 동시에 DB `admin_user_session` 테이블에 레코드를 생성합니다.
    2. 모든 API 요청 시 `SessionValidationFilter`가 JWT 서명 검증 후 DB 세션의 상태(`active`)를 확인합니다.
    3. 중복 로그인 발생 시 이전 세션을 DB에서 `REVOKED` 처리하며, 해당 토큰을 가진 클라이언트는 즉시 튕김(Kick-out) 처리됩니다.

### 3.2. 동적 권한 관리 (Dynamic RBAC)
* **Feature**: 소스 코드 수정 및 서버 재배포 없이 운영 중에 API 접근 권한을 변경할 수 있습니다.
* **Implementation**: `SecurityConfig`가 애플리케이션 기동 시 또는 관리자의 리프레시 요청 시 `security_rule` 테이블을 로드하여 Spring Security의 `RequestMatcher`를 런타임에 재구성합니다.

---

## 4. 외부 데이터 연동 (External Sync)

### 4.1. 학교 데이터 동기화 (SchoolInfo API)
* **Source**: 공공데이터포털 학교알리미 OpenAPI
* **Robustness**:
    * 외부 API 장애나 타임아웃에 대비해 **비동기 스케줄러**와 **수동 트리거** 모드를 모두 지원합니다.
    * **Stream Parsing**: 대용량 JSON 데이터를 메모리에 모두 로드하지 않고 스트림으로 처리하여 OOM(Out of Memory)을 방지합니다.
    * **Fault Tolerance**: `Retry`(재시도) 및 `Throttling`(요청 제한) 정책을 적용하여 외부 서비스 부하를 조절하고 성공률을 높입니다.