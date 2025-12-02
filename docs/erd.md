# 📊 데이터베이스 설계서 (Database Schema & Specification)

## 1. 개요
본 문서는 **WINO Academy Solution**의 데이터베이스 스키마 상세 명세서입니다.
시스템은 **MySQL 8.0**을 기반으로 하며, 데이터 무결성과 이력 추적(Audit)을 위해 주요 테이블은 정규화되어 있고 `_hist` 테이블과 트리거를 동반합니다.

---

## 2. ER Diagram (Overview)

> **참고**: 주요 도메인 간의 핵심 관계도입니다. 실선은 식별 관계(Identifying), 점선은 비식별 관계(Non-identifying)를 의미합니다.

```mermaid
erDiagram
    %% --- 1. 시스템 및 권한 (System) ---
    ADMIN_USER_INFO ||--o{ TEAM_MEMBER : "belongs to"
    TEAM_GROUP ||--|{ TEAM_MEMBER : "has"
    ADMIN_USER_INFO ||--o{ ADMIN_USER_SESSION : "has session"
    
    %% --- 2. 회원 및 구성원 (Member) ---
    END_USER ||--|| STUDENT : "account map"
    END_USER ||--|| GUARDIAN : "account map"
    STUDENT ||--o{ STUDENT_GUARDIAN_LINK : "family"
    GUARDIAN ||--o{ STUDENT_GUARDIAN_LINK : "family"
    STUDENT }|--|{ STUDENT_SIBLING : "sibling"

    %% --- 3. 학사 관리 (Course & Academic) ---
    SEMESTER ||--o{ CLASS_MASTER : "defines term"
    CLASS_MASTER ||--|{ CLASS_SUBJECT : "contains"
    SUBJECT ||--o{ CLASS_SUBJECT : "refers to"
    CLASS_SUBJECT ||--o{ CLASS_TIMESLOT : "scheduled at"
    ROOM_MASTER ||--o{ CLASS_TIMESLOT : "located in"
    
    %% --- 4. 수강 및 배정 (Enrollment) ---
    STUDENT ||--o{ STUDENT_CLASS_ENROLLMENT : "enrolls"
    CLASS_MASTER ||--o{ STUDENT_CLASS_ENROLLMENT : "has student"
    STUDENT_CLASS_ENROLLMENT ||--o{ STUDENT_ENROLL_TIMESLOT : "attends"
    CLASS_TIMESLOT ||--o{ STUDENT_ENROLL_TIMESLOT : "mapped"

    %% --- 5. 수납 및 청구 (Finance) ---
    TUITION_CATEGORY ||--o{ TUITION_PRICE : "categorizes"
    TUITION_PRICE ||--o{ STUDENT_TUITION : "assigned to"
    STUDENT ||--o{ STUDENT_TUITION : "has tuition"
    STUDENT_TUITION ||--o{ STUDENT_INVOICE : "generates"
    STUDENT_INVOICE ||--o{ STUDENT_PAYMENT : "paid by"