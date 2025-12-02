# WINO Academy Solution (Backend API)

> **Enterprise-Grade Academy Management System**
>
> 확장성과 데이터 무결성을 최우선으로 설계된 학원 운영 관리 솔루션(ERP)의 백엔드 API 서버입니다.
> 다중 지점(Branch) 관리, 복잡한 수강료 청구 및 정산, 유연한 시간표 배정 기능을 제공합니다.

---

## 📚 프로젝트 개요

**WINO Academy**는 학원의 복잡한 운영 프로세스를 표준화하고 자동화하기 위해 개발되었습니다. 단순한 CRUD를 넘어, **대규모 데이터 동기화**, **동시성 제어**, **동적 권한 관리** 등 엔터프라이즈 환경에서 필수적인 기술적 요구사항을 충실히 반영했습니다.

### 🎯 핵심 가치
* **Stability**: 정규학기 유일성 보장, 수강료/청구 데이터의 무결성 확보 (Unique Constraints, Atomic Transactions).
* **Scalability**: 도메인 주도 설계(DDD) 기반의 모듈화된 아키텍처 (`course`, `student`, `finance` 등).
* **Flexibility**: 운영 중 재배포 없이 변경 가능한 동적 설정(`AppSetting`) 및 동적 권한 제어(`SecurityRule`).

---

## 🛠 기술 스택 (Tech Stack)

* **Language**: Java 21 (LTS)
* **Framework**: Spring Boot 3.2
* **Database**: MySQL 8.0
* **ORM**: Spring Data JPA + QueryDSL (복잡한 조회) + JDBC Template (배치/Bulk Insert)
* **Security**: Spring Security + JWT (Stateful Session 연동 - 중복 로그인 방지)
* **Build Tool**: Gradle
* **External**: 학교알리미(SchoolInfo) OpenAPI 연동

---

## ✨ 주요 기능 (Key Features)

1.  **🔐 보안 및 권한 (Security & Auth)**
    * **이중 세션 제어**: JWT와 DB 세션을 연동하여 실시간 강제 로그아웃 및 중복 로그인 차단.
    * **동적 RBAC**: 소스 수정 없이 DB 데이터만으로 API 접근 권한 실시간 제어.
    * **Audit Logging**: 모든 관리자 행위(CUD)를 IP, UserAgent, Request Body와 함께 기록.

2.  **💰 수납 및 청구 (Finance)**
    * **멱등성(Idempotency)**: 중복 결제/청구 방지 로직 적용.
    * **배치 처리**: 대량 청구서 생성 시 `REQUIRES_NEW` 트랜잭션 분리 및 Bulk Insert 최적화.
    * **미리보기(Dry-run)**: 청구 실행 전 예상 결과 시뮬레이션 기능.

3.  **📅 학사 및 시간표 (Academic & Timetable)**
    * **Course 구조**: 반(Course) - 과목(Subject) - 시간표(Timeslot)의 계층적 구조.
    * **교차 수강**: 정규 반 외 타 반 수업 수강 지원 및 시간표 충돌 자동 감지.
    * **스냅샷**: 학기 마감 시 데이터 스냅샷 저장 및 특정 시점 복원(Time-travel) 지원.

---

## 📂 문서 (Documentation)

솔루션 납품 및 유지보수를 위한 상세 문서입니다. (`docs/` 폴더)

* [📊 데이터베이스 설계서 (ERD)](docs/erd.md)
* [📝 기능 명세서 (Functional Spec)](docs/functional-spec.md)
* [🔌 API 가이드](docs/api-guide.md)

---

## 🚀 시작하기 (Getting Started)

### Prerequisites
* JDK 21+
* MySQL 8.0+

### Installation
1.  **Clone the repo**
    ```bash
    git clone [https://github.com/your-repo/wino-academy-api.git](https://github.com/your-repo/wino-academy-api.git)
    ```
2.  **Configure Database**
    * `src/main/resources/application-dev.yml` 등에서 DB 접속 정보 설정 (또는 환경변수 주입).
    * **필수 환경 변수**: `JWT_SECRET_KEY`, `AES_SECRET_KEY`, `SPRING_MAIL_PASSWORD` 등.
3.  **Run**
    ```bash
    ./gradlew bootRun
    ```
4.  **Swagger UI**
    * http://localhost:8080/swagger-ui.html

---

## 📞 Contact
* **Developer**: WINO
