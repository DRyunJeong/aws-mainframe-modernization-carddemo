# REPTFILE — 거래 리포트 파일 GDG 기반 정의

- **유형**: JCL (일회성 환경 설정 잡)
- **한 줄 요약**: IDCAMS를 사용하여 거래 상세 리포트 출력에 사용할 GDG(Generation Data Group) 기반 카탈로그 엔트리 `AWS.M2.CARDDEMO.TRANREPT`를 정의한다.

---

## 기능 설명

REPTFILE 잡은 애플리케이션 배치 실행 환경을 준비하는 **일회성 초기화 잡**이다. 실제 비즈니스 로직을 수행하지 않고, 거래 리포트를 저장할 GDG(Generation Data Group) 기반 구조를 VSAM/카탈로그에 등록하는 역할만 한다.

GDG는 메인프레임에서 동일한 논리적 이름 아래 여러 세대(generation)의 순차 데이터셋을 쌓아두는 패턴이다. `TRANREPT(+1)`처럼 상대 참조를 사용하면 가장 최신 세대를 가리키며, 새 세대를 CATLG(카탈로그 등록)할 때 자동으로 이전 세대를 하나씩 밀어낸다. LIMIT(10)이므로 최대 10세대를 유지하고 11번째 생성 시 가장 오래된 세대가 자동 삭제된다.

Java/현대 시스템에서 대응하는 개념은 **파일 롤링 전략**(예: Logback/Log4j의 `RollingFileAppender`, 또는 S3 버킷에 날짜별 파티션 키로 리포트를 저장하고 Lifecycle 정책으로 오래된 세대를 삭제)이다.

잡 헤더 주석(`DEF GDG FOR REPORT FILE`)과 스텝 주석(`DELETE TRANSACATION MASTER VSAM FILE IF ONE ALREADY EXISTS`)에 오해의 소지가 있다. 주석은 "삭제"라고 되어 있으나 실제 `SYSIN`에는 `DEFINE GENERATIONDATAGROUP` 명령만 있고 `DELETE` 명령은 없다. 이는 JCL 복사·재편집 과정에서 주석이 업데이트되지 않은 것으로 추정된다 (추측).

---

## 스텝 구성

| 스텝명  | EXEC PGM/PROC | 역할 |
|---------|---------------|------|
| STEP05  | `PGM=IDCAMS`  | AMS(Access Method Services) 유틸리티를 사용하여 GDG 기반 `AWS.M2.CARDDEMO.TRANREPT`를 LIMIT=10으로 카탈로그에 정의한다. |

**STEP05 상세**

| DD명       | 용도 |
|------------|------|
| `SYSPRINT` | IDCAMS 실행 로그 → `SYSOUT=*` (스풀 출력) |
| `SYSIN`    | IDCAMS 제어 명령 (인라인 `DD *`) |

`SYSIN` 인라인 제어문 (라인 25-28):

```
DEFINE GENERATIONDATAGROUP -
(NAME(AWS.M2.CARDDEMO.TRANREPT) -
 LIMIT(10) -
)
```

- `NAME`: GDG 기반 이름. 이후 모든 리포트 세대는 이 이름 아래 `(0)`, `(-1)`, `(-2)` ... `(-9)` 형태로 참조된다.
- `LIMIT(10)`: 최대 보존 세대 수. SCRATCH/NOSCRATCH 키워드가 명시되지 않았으므로 시스템 기본값이 적용된다(추측: NOSCRATCH — 한계 초과 시 카탈로그 엔트리만 해제하고 물리 데이터셋은 남김. 환경에 따라 다를 수 있음).
- 이 정의는 **멱등(idempotent)하지 않다**. 이미 동일 이름의 GDG 기반이 존재하면 IDCAMS는 RC=12로 실패한다. 재실행이 필요한 경우 먼저 `DELETE (AWS.M2.CARDDEMO.TRANREPT) GDG` 명령을 선행해야 한다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 (JCLLIB/PROC 미참조)

- **호출 프로그램 (EXEC PGM)**: `IDCAMS` — IBM 제공 표준 AMS 유틸리티. 사용자 작성 프로그램 없음.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.TRANREPT` — 이 잡이 **생성**하는 GDG 기반 엔트리. 물리 데이터셋이 아니라 카탈로그 메타데이터만 생성됨.
  - 실제 세대 데이터셋(예: `AWS.M2.CARDDEMO.TRANREPT.G0001V00`)은 TRANREPT 잡의 STEP10R에서 `DSN=AWS.M2.CARDDEMO.TRANREPT(+1)`로 NEW/CATLG될 때 비로소 생성된다. LRECL=133, RECFM=FB (133바이트 고정 블록, 인쇄 파일 표준).

- **선행/후행 잡**:
  - **선행**: 없음. 이 잡은 환경 초기화 잡이므로 최초 1회만 실행하면 된다. 야간 배치 루틴과 무관하다.
  - **후행**: `TRANREPT.jcl` — REPTFILE이 GDG 기반을 정의한 뒤에야 TRANREPT 잡이 `TRANREPT(+1)` 세대 데이터셋을 생성할 수 있다. TRANREPT 잡은 CBTRN03C(거래 상세 리포트 생성 배치)를 실행하며, 출력 `TRANREPT(+1)` 세대에 133바이트 고정 블록 리포트를 기록한다.

---

## Java/현대화 노트

### 1. GDG → S3 파티셔닝 또는 파일 롤링

메인프레임 GDG는 Java/클라우드 환경에 직접 대응하는 개념이 없다. 현대화 시 두 가지 대안을 고려한다.

```java
// 방안 A: S3 세대 키 패턴 (AWS 환경 권장)
// s3://bucket/reports/TRANREPT/2022-07-19/report.txt
// → Lifecycle policy로 90일 이후 자동 삭제 (LIMIT=10과 동일한 보존 전략)

// 방안 B: 로컬 파일 롤링 (Spring Batch ItemWriter 패턴)
String reportPath = String.format(
    "/output/TRANREPT_%s.txt",
    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
);
```

`LIMIT(10)`의 Java 대응은 보존 정책(Retention Policy)이다. S3라면 버킷 Lifecycle Rule로, 로컬이라면 `Files.list()`로 오래된 파일을 정렬 후 10개 초과분 삭제하는 로직이 필요하다.

### 2. IDCAMS DEFINE → 부트스트랩/마이그레이션 스크립트

IDCAMS는 Java에서의 DDL(CREATE TABLE) 또는 S3 버킷 생성 스크립트에 해당한다. 현대화 후에는 Flyway/Liquibase 마이그레이션 스크립트 또는 Terraform/CDK 인프라 코드로 일회성 초기화를 표현한다.

### 3. 멱등성 처리

원본 JCL은 이미 GDG 기반이 존재할 경우 실패한다. Java 배포 파이프라인에서는 이에 해당하는 "이미 존재하면 무시" 패턴을 명시적으로 처리해야 한다.

```java
// S3 예시: 버킷이 이미 존재하면 무시
try {
    s3Client.createBucket(CreateBucketRequest.builder()
        .bucket("carddemo-tranrept")
        .build());
} catch (BucketAlreadyOwnedByYouException e) {
    log.info("Report output bucket already exists, skipping creation.");
}
```

### 4. JCL 주석 불일치 (주의)

STEP05의 주석(`DELETE TRANSACATION MASTER VSAM FILE IF ONE ALREADY EXISTS`)은 실제 동작과 다르다. 실제로는 DELETE가 아닌 DEFINE을 수행한다. 현대화 작업 시 이 주석만 보고 "삭제 로직 포함"으로 오해하지 않도록 주의가 필요하다. 실제 소스 라인 25-28을 항상 직접 확인해야 한다.

### 5. TRANREPT 잡과의 관계 요약

```
REPTFILE (최초 1회)
  └─ IDCAMS DEFINE GDG: AWS.M2.CARDDEMO.TRANREPT LIMIT(10)
       │
       ▼ (환경 준비 완료)
TRANREPT (야간 배치 or 수동 실행)
  ├─ STEP05R: REPROC PROC → TRANSACT VSAM → TRANSACT.BKUP(+1) 언로드
  ├─ STEP05R: DFSORT → TRANSACT.BKUP(+1) 날짜 필터링+카드번호 정렬 → TRANSACT.DALY(+1)
  └─ STEP10R: PGM=CBTRN03C → TRANSACT.DALY(+1) + CARDXREF + TRANTYPE + TRANCATG
                             → TRANREPT(+1) [GDG 새 세대 생성, LRECL=133]
```

CBTRN03C의 상세 동작(4파일 조인, control-break 합계, CVTRA07Y 레이아웃)은 별도 메모리 `transaction-report-cbtrn03c`를 참조한다.
