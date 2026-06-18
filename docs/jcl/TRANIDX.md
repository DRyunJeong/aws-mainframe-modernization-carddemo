# TRANIDX — 거래 마스터 대체 인덱스(AIX) 정의 잡

- **유형**: JCL
- **한 줄 요약**: VSAM 거래 KSDS(`TRANSACT`)에 처리 타임스탬프 기준 AIX를 생성·경로 연결·빌드하는 야간 배치 후처리 잡

---

## 기능 설명

`TRANIDX`는 VSAM 유틸리티 프로그램 `IDCAMS`를 세 번 연속 실행하여 거래 마스터 KSDS 파일에 **대체 인덱스(Alternate Index, AIX)** 를 구성한다.

기본 KSDS(`AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS`)는 기본 키(Primary Key)로만 순차/랜덤 접근이 가능하다. 이 잡이 완료되면 **처리 타임스탬프(Processed Timestamp)** 를 보조 키로 사용하는 검색 경로가 열린다. CICS 온라인 프로그램(`COTRN00C`/`COTRN01C`)은 이 AIX PATH를 통해 날짜 범위 조회 등 기본 키 이외 접근을 수행할 수 있다.

야간 배치 시퀀스에서 `COMBTRAN`(거래 정렬·병합) 직후, `OPENFIL`(CICS 파일 재오픈) 직전에 위치한다. 배치 처리로 새 거래 레코드가 쌓인 뒤 AIX를 재구성하므로, CICS가 파일을 다시 열기 전에 인덱스가 최신 상태가 된다.

**Java/현대 DB 유추**: RDBMS의 `CREATE INDEX` + 인덱스 테이블 재구성(`REBUILD INDEX`)에 해당한다. VSAM KSDS는 관계형 테이블과 달리 인덱스를 별도 클러스터로 정의·관리해야 하며, `PATH`는 JPA의 `@SecondaryIndex` 혹은 추가 `@Index` 어노테이션처럼 기본 엔티티와 인덱스 구조를 연결하는 논리적 관계 이름이다.

---

## 스텝 구성

| 스텝명  | EXEC PGM/PROC | 역할 |
|---------|---------------|------|
| `STEP20` | `IDCAMS` | AIX 클러스터 정의 (`DEFINE ALTERNATEINDEX`) — DATA 컴포넌트와 INDEX 컴포넌트 이름 지정, 키 오프셋·길이·비고유성 설정 |
| `STEP25` | `IDCAMS` | AIX PATH 정의 (`DEFINE PATH`) — AIX와 베이스 KSDS를 논리적으로 연결하는 경로 오브젝트 생성 |
| `STEP30` | `IDCAMS` | AIX 빌드 (`BLDINDEX`) — 베이스 KSDS 전체를 읽어 AIX 클러스터에 인덱스 엔트리를 실제로 채움 |

### STEP20 상세 — `DEFINE ALTERNATEINDEX`

```
DEFINE ALTERNATEINDEX (
  NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX)
  RELATE(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS)
  KEYS(26 304)           ← 오프셋 304, 길이 26바이트
  NONUNIQUEKEY           ← 같은 타임스탬프를 가진 복수 레코드 허용
  UPGRADE                ← 베이스 KSDS에 레코드 추가/삭제 시 AIX 자동 갱신
  RECORDSIZE(350,350)    ← 평균/최대 레코드 크기(베이스와 동일)
  VOLUMES(AWSHJ1)
  CYLINDERS(5,1)         ← 초기 5실린더, 보조 1실린더씩 확장
)
DATA (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.DATA))
INDEX (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.INDEX))
```

**키 해석**: `KEYS(26 304)` — VSAM AIX 키 정의에서 첫 번째 숫자가 **길이(length)**, 두 번째 숫자가 **오프셋(offset)**이다. 즉, 레코드 바이트 오프셋 304에서 시작하는 26바이트 필드가 보조 키로 사용된다. 거래 레코드 copybook `CVTRA05Y`의 처리 타임스탬프 필드(`TRAN-PROC-TS`, `PIC X(26)`)가 이 위치에 매핑된다(추측 — copybook 확인 권장).

`NONUNIQUEKEY`는 동일 타임스탬프에 복수 거래 레코드가 존재할 수 있음을 선언한다. 이 경우 AIX 엔트리 하나가 복수의 기본 키 포인터를 포함한다. Java `HashMap<Timestamp, List<TranRecord>>` 구조와 유사하다.

`UPGRADE`는 베이스 KSDS가 변경될 때마다 AIX가 자동 동기화됨을 의미한다. `UPGRADE`가 없으면 `NOUPGRADE`로 지정되며 수동 `BLDINDEX` 재실행이 필요하다.

### STEP25 상세 — `DEFINE PATH`

```
DEFINE PATH (
  NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH)
  PATHENTRY(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX)
)
```

PATH는 독립 오브젝트로, 프로그램 코드(COBOL의 `SELECT ... ASSIGN` 또는 JCL의 `DD`)에서 이 PATH 이름을 참조하면 AIX를 통한 검색이 활성화된다. PATH 없이 AIX만 정의하면 프로그램이 AIX 경로로 파일을 열 수 없다.

Java 유추: `@Index` 정의만으로는 부족하고, JPA `EntityManager`가 인덱스를 통한 쿼리를 실행하기 위한 named query(`@NamedQuery`)나 별도 `Repository` 메서드를 추가하는 것과 개념적으로 대응된다.

### STEP30 상세 — `BLDINDEX`

```
BLDINDEX
  INDATASET(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS)
  OUTDATASET(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX)
```

베이스 KSDS의 모든 레코드를 순차적으로 읽어 각 레코드의 보조 키 값과 기본 키 값을 추출해 AIX 클러스터를 채운다. 레코드 수가 많을수록 실행 시간이 길어진다. `WORKFILES` 파라미터를 지정하지 않았으므로 `IDCAMS`는 내부 정렬 워크 공간을 자동으로 할당한다(추측 — 대용량 환경에서는 `WORKFILES` DD를 명시해 정렬 성능을 높이는 것이 일반적).

**실행 선제조건**: STEP20, STEP25가 성공(CC=0)해야 STEP30이 의미 있다. JCL에 명시적 `COND` 파라미터가 없으므로 앞 스텝 실패 시에도 STEP30이 실행된다 — 운영 환경에서는 `COND=(0,NE)` 또는 `IF/THEN/ELSE` 블록 추가를 권장한다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 (인라인 SYSIN만 사용, PROC 참조 없음)

- **호출 프로그램 (EXEC PGM)**: `IDCAMS` — IBM 제공 VSAM 카탈로그 관리 유틸리티. 모든 VSAM 클러스터/AIX/PATH의 생성·삭제·빌드·리스팅을 담당한다. Java 유추: `javax.sql.DataSource`를 통해 DDL(`CREATE INDEX`, `REBUILD INDEX`)을 실행하는 스키마 관리 도구(Flyway/Liquibase의 인덱스 마이그레이션 스크립트와 역할 유사).

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` — 거래 마스터 KSDS (베이스 클러스터, 입력). `POSTTRAN`(CBTRN02C) 및 `INTCALC`(CBACT04C)가 기록한 거래 레코드가 존재해야 함
  - `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX` — 생성되는 AIX 클러스터 (DATA + INDEX 컴포넌트 포함)
  - `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH` — 생성되는 PATH 오브젝트
  - `AWSHJ1` — AIX 클러스터가 할당될 DASD 볼륨 (5실린더 초기 공간)

- **선행/후행 잡**:
  - 선행: `COMBTRAN` — 거래 파일 정렬·병합 후 이 잡 실행 (야간 배치 시퀀스 기준, `batch_and_optional_modules` 메모리 참조)
  - 후행: `OPENFIL` — CICS에 모든 VSAM 파일(AIX PATH 포함) 재오픈. TRANIDX 완료 후에야 CICS가 최신 AIX를 사용 가능

---

## Java/현대화 노트

### 1. AIX → 관계형 인덱스 또는 검색 엔진 필드

VSAM AIX는 RDBMS의 보조 인덱스(Secondary Index)와 직접 대응된다. 거래 테이블로 마이그레이션 시:

```sql
-- MySQL/PostgreSQL 예시
CREATE INDEX idx_tran_proc_ts ON transaction_master (tran_proc_ts);
```

`NONUNIQUEKEY`이므로 `UNIQUE` 제약 없이 생성한다. Hibernate/JPA에서는:

```java
@Entity
@Table(name = "transaction_master",
       indexes = @Index(name = "idx_tran_proc_ts", columnList = "tran_proc_ts"))
public class TransactionMaster { ... }
```

### 2. `BLDINDEX` → Flyway/Liquibase 마이그레이션 또는 배치 인덱스 재구성

`BLDINDEX`는 전체 테이블 스캔 후 인덱스를 채우는 일회성 작업이다. 현대 시스템 대응:

```java
// Spring Batch: 배치 후 인덱스 상태 확인/재구성
@Bean
public Step rebuildIndexStep(DataSource ds) {
    return stepBuilderFactory.get("rebuildIndexStep")
        .tasklet((contribution, chunkContext) -> {
            jdbcTemplate.execute("ANALYZE TABLE transaction_master");
            return RepeatStatus.FINISHED;
        }).build();
}
```

### 3. `UPGRADE` 옵션 → 트리거 또는 인덱스 자동 갱신

VSAM `UPGRADE`는 베이스 파일 변경 시 AIX를 자동 동기화한다. RDBMS는 기본적으로 DML 시 인덱스를 자동 갱신하므로 별도 처리가 필요 없다. 단, VSAM에서 `NOUPGRADE`로 설정된 AIX를 마이그레이션할 때는 DB 인덱스 자동 갱신 여부를 명시적으로 확인해야 한다.

### 4. `NONUNIQUEKEY` + 타임스탬프 키 정밀도 주의

`KEYS(26 304)` 26바이트 타임스탬프는 COBOL 타임스탬프 형식(`YYYY-MM-DD-HH.MM.SS.MMMMMM` 또는 DB2 형식)일 가능성이 높다. Java `java.time.Instant` 또는 `LocalDateTime`으로 매핑 시 **마이크로초 정밀도** 보존 여부를 확인해야 한다. `java.sql.Timestamp`는 나노초 지원, `LocalDateTime`은 나노초 지원이나 직렬화 포맷에 주의가 필요하다.

### 5. COND 파라미터 부재 — 오류 처리 강화 권고

이 JCL은 스텝 간 `COND` 또는 `IF/THEN/ELSE`가 없어 앞 스텝 실패 후에도 다음 스텝이 실행된다. 현대화 시 파이프라인(Spring Batch Job, AWS Step Functions 등)에서는 반드시 단계별 성공 조건을 명시해야 한다:

```java
// Spring Batch 흐름 예시
@Bean
public Job tranIdxJob() {
    return jobBuilderFactory.get("tranIdxJob")
        .start(defineAixStep())
            .on("FAILED").fail()
        .from(defineAixStep())
            .on("*").to(definePathStep())
        .from(definePathStep())
            .on("FAILED").fail()
        .from(definePathStep())
            .on("*").to(buildIndexStep())
        .end()
        .build();
}
```

### 6. 키 오프셋 확인 필수

`KEYS(26 304)` 에서 오프셋 304는 copybook `CVTRA05Y`(거래 레코드 레이아웃)의 실제 필드 위치와 반드시 일치해야 한다. 마이그레이션 시 copybook의 PIC 절 바이트 누적 합산으로 오프셋 304가 `TRAN-PROC-TS` 필드 시작점인지 검증해야 한다. COMP-3 필드가 중간에 있으면 물리 오프셋이 논리 순서와 달라진다.
