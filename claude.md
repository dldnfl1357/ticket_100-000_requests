# Reservation

## 프로젝트 개요

고성능/고가용 티켓 예매 서버 만들기 (100,000 TPS + 에러 0% + 응답 시간 3초 이내)

## 기술 스택 & 아키텍처

### 핵심 프레임워크

-   **Spring Boot 3.1.3** - 메인 프레임워크 (Spring MVC 기반)
-   **Java 17** - OpenJDK 17 LTS
-   **Gradle 8.x** - 빌드 자동화 도구

### 데이터베이스 & 영속성 계층

-   **MySQL 8.0** - 메인 관계형 데이터베이스
-   **Spring Data JPA 3.1.x** - 데이터 액세스 추상화
-   **Hibernate 6.2.x** - JPA 구현체
-   **QueryDSL 5.0.x** - 타입 안전한 동적 쿼리
-   **Flyway 9.x** - 스키마 마이그레이션 관리
-   **HikariCP** - 커넥션 풀 관리

### Kafka

-   **Kafka** - Kafka 연동 (As Consumer)

### Redis

-   **Redis** - Redis 연동

### 테스트 & 문서화

-   **JUnit 5 + Mockito** - 단위/통합 테스트
-   **Spring REST Docs** - 테스트 기반 API 문서 자동화
-   **AsciiDoctor** - 문서 생성 엔진

## 애플리케이션 아키텍처

### 레이어드 아키텍처 구조

```
src/main/java/reservation_100000_tps/
├── api/                    # Presentation Layer (REST Controllers)
├── service/               # Business Logic Layer
├── domain/               # Domain/Data Access Layer
├── repository/           # Data Access Layer
├── dto/                  # 데이터 전송 객체
├── enums/               # 도메인 열거형
├── configure/           # 설정 및 인프라 (Kafka, Redis 설정 등)
└── common/              # 공통 유틸리티
```

## 핵심 도메인 기능

### 1. 예약 (Reservation)

-   **티켓 예매**: 한정된 수량의 티켓 예매(성능, 동시성)

## 개발 표준 및 가이드라인

### 코딩 규약

-   **네이밍 컨벤션**: Java 표준 camelCase (변수/메서드), PascalCase (클래스)
-   **패키지 구조**: `reservation_100000_tps.{layer}.{domain}` 계층별 구조
-   **메서드 길이**: 최대 50라인 이내 권장
-   **클래스 응집도**: Single Responsibility Principle 준수
-   **주석**: JavaDoc 형식으로 public API 문서화

### API 개발 표준

-   **RESTful 설계**: HTTP 메서드별 명확한 의미 부여
-   **URL 패턴**: `/api/v1/{resource}` 형식 준수
-   **응답 형식**: 일관된 JSON 구조 (success, data, message)
-   **에러 처리**: HTTP 상태코드 + 커스텀 에러코드 조합
-   **페이지네이션**: `page`, `size`, `sort` 파라미터 표준화

### 데이터베이스 정책

-   **테이블 네이밍**: snake_case (예: member_organization)
-   **컬럼 네이밍**: snake_case + 타입 명시 (예: created_at, is_active)
-   **인덱스 정책**: 조회 성능 최적화를 위한 복합 인덱스 활용
-   **마이그레이션**: Flyway를 통한 스키마 버전 관리
-   **트랜잭션**: `@Transactional(readOnly=true)` 기본 적용

### 보안 정책

-   **패스워드 정책**: 최소 8자 이상, 특수문자 포함
-   **JWT 토큰**: 15분 AccessToken + 7일 RefreshToken
-   **API 인증**: Bearer Token 방식
-   **민감정보**: AWS Secrets Manager 중앙 관리
-   **HTTPS 강제**: Production 환경 필수

### 테스트 전략

-   **단위 테스트**: 각 서비스 메서드별 최소 80% 커버리지
-   **통합 테스트**: @SpringBootTest + TestContainers
-   **API 테스트**: MockMvc + Spring REST Docs 자동 문서화
-   **테스트 데이터**: @Sql 스크립트 또는 @TestConfiguration

## 개발 환경 설정

### 시스템 요구사항

-   **Java**: OpenJDK 17+ (Eclipse Temurin 권장)
-   **Gradle**: 8.0+ (Wrapper 사용 권장)
-   **IDE**: IntelliJ IDEA 2023.2+ 또는 Eclipse 2023-06+
-   **Database**: MySQL 8.0+ (로컬 개발용: Docker Compose 제공)
