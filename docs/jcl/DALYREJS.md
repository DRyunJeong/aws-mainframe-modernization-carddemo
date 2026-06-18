# DALYREJS — 일일 거절 거래 GDG 정의 잡

- **유형**: JCL
- **한 줄 요약**: 일일 배치 거래 처리에서 발생하는 거절(reject) 레코드를 누적 보관하기 위한 GDG(Generation Data Group) 베이스를 VSAM 카탈로그에 등록하는 1회성 환경 구성 잡이다.

---

## 기능 설명

`DALYREJS`는 `AWS.M2.CARDDEMO.DALYREJS`라는 이름의 GDG 베이스를 IBM Access Method Services(IDCAMS)로 카탈로그에 정의한다.

GDG는 메인프레임에서 "세대별 파일 그룹"을 의미하는 구조다. 배치가 한 번 실행될 때마다 새 세대 파일(`G0001V00`, `G0002V00`, …)이 GDG 베이스 아래에 자동으로 누적된다. `LIMIT(5)`로 최대 5세대를 유지하며, 6번째 세대가 추가되면 가장 오래된 세대가 자동 스크래치(삭제)된다(`SCRATCH` 옵션).

이 잡 자체는 GDG 베이스(디렉토리 항목)만 생성할 뿐, 실제 데이터셋을 할당하거나 데이터를 기록하지 않는다. 실제 거절 레코드 쓰기는 배치 잡 `POSTTRAN`의 프로그램 `CBTRN02C`가 담당하며, 매 실행마다 `AWS.M2.CARDDEMO.DALYREJS(+1)` 형태로 새 세대를 열어 쓴다.

**Java/Spring Batch 대응 개념**: GDG는 Java에 직접 대응하는 구조가 없다. 가장 근접한 패턴은 "날짜 스탬프 파일명 자동 생성 + 보존 개수 제한 정책(rolling file policy)"이다. 예: Logback `RollingFileAppender`의 `maxHistory(5)` 설정, 또는 배치 실행 때마다 `rejections_YYYYMMDD_HH.dat` 형태 파일을 생성하고 오래된 파일을 주기적으로 삭제하는 유틸리티.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|--------------|------|
| STEP05 | `IDCAMS` | VSAM 카탈로그에 GDG 베이스 `AWS.M2.CARDDEMO.DALYREJS`를 정의 (`LIMIT(5) SCRATCH`) |

> `IDCAMS`(Integrated Data Cluster Access Method Services)는 IBM에서 제공하는 시스템 유틸리티 프로그램으로, VSAM 파일·GDG·카탈로그 관리 명령(DEFINE, DELETE, REPRO 등)을 실행한다. Java의 `DataSource` 스키마 초기화 스크립트(`schema.sql`) 또는 AWS CLI의 `aws s3api create-bucket`과 유사한 1회성 인프라 프로비저닝 역할이다.

IDCAMS SYSIN 제어문 상세 (라인 24–28):

```
DEFINE GENERATIONDATAGROUP
  (NAME(AWS.M2.CARDDEMO.DALYREJS)
   LIMIT(5)
   SCRATCH
  )
```

- `DEFINE GENERATIONDATAGROUP`: GDG 베이스를 카탈로그에 등록한다.
- `NAME(AWS.M2.CARDDEMO.DALYREJS)`: 상위 HLQ(High-Level Qualifier) `AWS.M2.CARDDEMO` 아래에 `DALYREJS` 그룹 이름을 지정한다.
- `LIMIT(5)`: 동시에 카탈로그에 보존할 최대 세대 수를 5로 제한한다.
- `SCRATCH`: 세대 한도를 초과하면 가장 오래된 세대를 카탈로그에서 자동 제거(스크래치)한다. `NOSCRATCH`일 경우 초과 세대를 수동으로 정리해야 한다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — 이 잡은 외부 PROC이나 INCLUDE를 참조하지 않는다.

- **호출 프로그램 (EXEC PGM)**: `IDCAMS` — IBM 표준 시스템 유틸리티. 별도 설치 불필요. DD `SYSPRINT`는 IDCAMS 실행 결과 메시지를 `SYSOUT`(JES 스풀)에 기록하고, `SYSIN`은 인라인 제어문(`/*`로 종료)을 제공한다.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.DALYREJS` — 이 잡이 생성하는 GDG 베이스 이름. 실행 후 실제 세대 파일은 `POSTTRAN` 잡 실행 시마다 `AWS.M2.CARDDEMO.DALYREJS.G000nV00` 형태로 추가된다.

- **선행/후행 잡**:
  - **선행**: 없음(카탈로그 정의 잡이므로 최초 1회만 실행). 단, 동일 GDG 베이스가 이미 존재하면 IDCAMS가 RC=12로 실패하므로, 재정의가 필요할 경우 먼저 `DELETE` 명령을 앞 스텝에 추가해야 한다 (주석 라인 19–20의 "DELETE … IF ONE ALREADY EXISTS" 문구는 이런 의도를 나타내지만, 실제 DELETE 스텝은 이 JCL에 없음 — 주석과 구현이 불일치).
  - **후행**: `POSTTRAN` 잡(프로그램 `CBTRN02C`)이 `AWS.M2.CARDDEMO.DALYREJS(+1)` DD를 열어 거절 레코드를 순차(PS, Physical Sequential) 파일로 기록한다. 에이전트 메모리의 batch_and_optional_modules 기록에 따르면, `CBTRN02C`는 카드→계정 XREF 조회, 신용한도·만료일 검증 실패 시 해당 거래를 DALYREJS에 거절 코드와 함께 기록하고 `RETURN-CODE 4`를 설정한다.

---

## Java/현대화 노트

### 1. GDG = 롤링(Rolling) 파일 정책

GDG를 Java로 현대화할 때 주의점은 "세대 번호(generation number)가 상대 참조(`(0)` = 현재, `(-1)` = 직전, `(+1)` = 신규)로 코드에 등장한다"는 점이다. 단순히 파일명을 날짜로 치환하면 참조 의미가 달라질 수 있다. 권장 패턴:

```java
// Spring Batch ItemWriter 예시: 신규 세대 = 오늘 날짜 파일
String fileName = "rejections_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".dat";
Path rejectFile = Paths.get("/data/dalyrejs/", fileName);

// 오래된 파일 5개 초과분 삭제 (LIMIT(5) SCRATCH 대응)
try (Stream<Path> files = Files.list(rejectFile.getParent())) {
    files.sorted(Comparator.reverseOrder())
         .skip(5)
         .forEach(p -> { try { Files.delete(p); } catch (IOException e) { /* log */ } });
}
```

### 2. GDG 베이스 정의 = DDL/인프라 초기화

IDCAMS `DEFINE GENERATIONDATAGROUP`은 애플리케이션 데이터를 처리하는 것이 아니라 "데이터를 담을 그릇의 메타데이터"를 등록하는 작업이다. Java 마이그레이션 관점에서는 Flyway/Liquibase의 스키마 마이그레이션(`V001__create_rejection_table.sql`)이나 Terraform의 S3 버킷 정의와 동일한 계층이다. 애플리케이션 배포 파이프라인의 인프라 프로비저닝 단계에 위치해야 하며, 매 배치 실행마다 호출할 필요가 없다.

### 3. 주석-코드 불일치 (라인 19–20 vs 실제 스텝)

JCL 라인 19–20의 주석은 "DELETE TRANSACTION MASTER VSAM FILE IF ONE ALREADY EXISTS"라고 되어 있으나, 실제 STEP05는 DELETE 없이 DEFINE만 수행한다. 이미 GDG 베이스가 존재하면 IDCAMS는 `IEF2421` 오류와 함께 RC=12를 반환하고 잡이 실패한다. 재실행 가능한 멱등(idempotent) 잡을 원한다면 다음과 같이 먼저 DELETE 스텝을 추가해야 한다 (추측):

```jcl
//STEP01 EXEC PGM=IDCAMS
//SYSIN  DD  *
  DELETE AWS.M2.CARDDEMO.DALYREJS GENERATIONDATAGROUP
  SET MAXCC=0
/*
```

### 4. 레코드 레이아웃은 이 JCL에 없음

DALYREJS GDG의 실제 레코드 포맷(LRECL, BLKSIZE, 거절 레코드 구조)은 이 JCL에 정의되어 있지 않다. 레코드 레이아웃은 `CBTRN02C`의 `FD DALYREJS` 선언과 관련 copybook에서 확인해야 한다. Java `ItemWriter`로 마이그레이션 시 해당 copybook의 필드 구조를 `RejectionRecord` POJO로 매핑해야 한다.

### 5. 야간 배치 시퀀스에서의 위치

이 잡은 야간 배치 시퀀스(CLOSEFIL → 마스터 갱신 → POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL)에서 직접 실행되지 않는다. 환경 초기 구성 시 1회 실행하는 사전 준비(pre-requisite) 잡이다. 관련 GDG 정의 잡으로 `DEFGDGB.jcl`(다른 GDG 베이스 정의 잡으로 추측)이 동일 패턴으로 존재한다.

---

*버전 정보: CardDemo_v1.0-15-g27d6c6f-68, 2022-07-19 (라인 31)*
