# TRANFILE — 거래 마스터 VSAM KSDS 초기 적재 잡

- **유형**: JCL
- **한 줄 요약**: 거래 마스터 VSAM KSDS(`TRANSACT`)와 처리타임스탬프 기준 보조 인덱스(AIX)를 신규 정의하고, 일일거래 평파일에서 데이터를 적재하는 일회성 초기화 잡

---

## 기능 설명

`TRANFILE` 잡은 CardDemo 온라인/배치 프로그램들이 공유하는 거래 마스터 VSAM KSDS 파일(`AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS`)을 처음부터 새로 구성한다.

전체 처리 순서는 다음 여섯 단계로 구성된다.

1. CICS 리전이 해당 파일을 잡고 있으면 파일 I/O가 불가능하므로, 먼저 CICS 콘솔 명령(`CEMT SET FILE ... CLO`)으로 `TRANSACT`와 교차참조 AIX 경로(`CXACAIX`)를 닫는다(라인 22-27).
2. 기존 KSDS 클러스터와 AIX 오브젝트가 있으면 `IDCAMS DELETE`로 제거한다(라인 32-40). `IF MAXCC LE 08 THEN SET MAXCC = 0`은 "오브젝트 없음(RC=8)" 조건을 정상으로 처리하는 표준 IDCAMS 관용구로, 파일이 존재하지 않아도 잡이 실패하지 않게 한다.
3. 새 KSDS 클러스터를 `IDCAMS DEFINE CLUSTER`로 정의한다(라인 46-62). 키 16바이트, 고정 레코드 350바이트.
4. 초기 데이터를 일일거래 평파일(`DALYTRAN.PS.INIT`)에서 `IDCAMS REPRO`로 복사한다(라인 67-74).
5. 처리타임스탬프(오프셋 304, 길이 26) 기준 AIX를 정의하고(라인 79-91), PATH로 베이스 클러스터와 연결하며(라인 96-101), `BLDINDEX`로 인덱스를 실제 구축한다(라인 106-111).
6. 모든 작업이 완료되면 CICS 콘솔 명령으로 `TRANSACT`와 `CXACAIX`를 다시 연다(라인 116-121).

Java 관점으로 비유하면, 이 잡 전체는 "데이터베이스 스키마 DROP → CREATE TABLE → CREATE INDEX → INSERT(벌크 로드) → 커넥션 풀 재개" 흐름에 해당한다.

---

## 스텝 구성

| 스텝명    | EXEC PGM/PROC | 역할 |
|-----------|---------------|------|
| `CLCIFIL` | `PGM=SDSF`    | CICS 리전(`CICSAWSA`)에 MODIFY 명령을 보내 `TRANSACT`, `CXACAIX` 파일을 CLOSE. SDSF의 ISFIN DD에 `/F` 오퍼레이터 명령을 인라인으로 공급하는 방식(라인 22-27) |
| `STEP05`  | `PGM=IDCAMS`  | 기존 KSDS 클러스터(`...TRANSACT.VSAM.KSDS`)와 AIX(`...TRANSACT.VSAM.AIX`)를 DELETE. RC≤8은 정상으로 리셋(라인 32-40) |
| `STEP10`  | `PGM=IDCAMS`  | 새 KSDS 클러스터 DEFINE. CYLINDERS(1 5), KEYS(16 0), RECORDSIZE(350 350), SHAREOPTIONS(2 3), ERASE, INDEXED(라인 46-62) |
| `STEP15`  | `PGM=IDCAMS`  | REPRO: `DALYTRAN.PS.INIT`(평파일) → `TRANSACT.VSAM.KSDS`(VSAM). DD명 TRANSACT/TRANVSAM을 INFILE/OUTFILE로 참조(라인 67-74) |
| `STEP20`  | `PGM=IDCAMS`  | AIX DEFINE. KEYS(26 304)=오프셋 304에서 26바이트(처리타임스탬프), NONUNIQUEKEY, UPGRADE, CYLINDERS(5 1)(라인 79-91) |
| `STEP25`  | `PGM=IDCAMS`  | PATH DEFINE: AIX(`...AIX`)를 베이스 클러스터와 연결하는 논리 경로 생성(라인 96-101) |
| `STEP30`  | `PGM=IDCAMS`  | BLDINDEX: 베이스 KSDS의 기존 레코드를 스캔해 AIX 클러스터에 인덱스 엔트리를 실제로 채움(라인 106-111) |
| `OPCIFIL` | `PGM=SDSF`    | CICS 리전에 MODIFY 명령을 보내 `TRANSACT`, `CXACAIX` 파일을 OPEN(라인 116-121) |

---

## 의존성

### COPY (PROC/INCLUDE)
없음. 모든 스텝이 인라인 SYSIN과 시스템 유틸리티(IDCAMS, SDSF)만 사용하며, 외부 PROC이나 INCLUDE 멤버를 참조하지 않는다.

### 호출 프로그램 (EXEC PGM)
- `IDCAMS` — IBM 제공 VSAM 관리 유틸리티. DEFINE/DELETE/REPRO/BLDINDEX 명령 처리(STEP05~STEP30)
- `SDSF` — System Display and Search Facility. ISFIN DD의 `/F CICSAWSA,...` 오퍼레이터 콘솔 명령을 CICS 리전으로 중계(CLCIFIL, OPCIFIL)

### 데이터셋/파일/DB 테이블

| 데이터셋 이름 | 유형 | 역할 |
|---|---|---|
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | VSAM KSDS | 거래 마스터(기본 클러스터). 키 16바이트, 레코드 350바이트. 신규 생성 대상 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS.DATA` | VSAM DATA | KSDS 데이터 컴포넌트 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS.INDEX` | VSAM INDEX | KSDS 기본 키 인덱스 컴포넌트 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX` | VSAM KSDS (AIX) | 처리타임스탬프(오프셋 304, 26B) 기준 보조 인덱스. NONUNIQUEKEY(동일 타임스탬프 복수 레코드 허용) |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.DATA` | VSAM DATA | AIX 데이터 컴포넌트 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.INDEX` | VSAM INDEX | AIX 인덱스 컴포넌트 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH` | VSAM PATH | AIX와 베이스 KSDS를 연결하는 논리 경로. CICS에서 AIX 경로명(`CXACAIX`)으로 파일 OPEN 시 이 PATH를 통해 접근 |
| `AWS.M2.CARDDEMO.DALYTRAN.PS.INIT` | 순차(PS) | 초기 적재용 일일거래 평파일(소스). STEP15 REPRO의 입력. 레코드 구조는 copybook `CVTRA06Y`의 `DALYTRAN-RECORD`(350바이트) |
| 볼륨 `AWSHJ1` | DASD | KSDS와 AIX가 정의되는 실제 디스크 볼륨(라인 51, 88) |

레코드 레이아웃 참고:
- 베이스 KSDS의 350바이트 레코드 → `app/cpy/CVTRA05Y.cpy`의 `TRAN-RECORD`. 에이전트 메모리 `copybook_record_layouts.md` 참조
- KEYS(16 0): 오프셋 0에서 16바이트가 기본 키(`TRAN-ID` PIC X(16))
- AIX KEYS(26 304): 오프셋 304에서 26바이트가 처리타임스탬프 필드(추측: `CVTRA05Y`의 타임스탬프 관련 필드. 정확한 필드명은 `CVTRA05Y` copybook 확인 필요)

### 선행/후행 잡

| 구분 | 잡/트랜잭션 | 설명 |
|---|---|---|
| 선행 잡 | CLOSEFIL (참고: `remote_refresh.sh`) | 정식 운영에서는 CLOSEFIL 잡이 `TRANSACT` 포함 전체 파일을 닫는다. 이 잡 자체의 CLCIFIL이 해당 역할을 내장하고 있어 독립 실행 가능 |
| 선행 데이터 | `DALYTRAN.PS.INIT` 생성 잡 | 초기 데이터 평파일이 먼저 존재해야 STEP15 REPRO가 성공 |
| 후행 잡 | POSTTRAN (`CBTRN02C`) | 초기 적재 후 야간 배치의 거래 게시 단계. `TRANSACT` KSDS를 출력 파일로 사용 |
| 후행 잡 | INTCALC (`CBACT04C`) | POSTTRAN 완료 후 이자 계산 배치. `TRANSACT`에 이자 거래 레코드를 추가 기록 |
| 후행 CICS | `TRANSACT`, `CXACAIX` 파일 OPEN | 이 잡의 OPCIFIL 스텝이 담당. 이후 온라인 프로그램(예: `COTRN01C`, `COTRN02C`)이 VSAM에 접근 가능 |

---

## Java/현대화 노트

### 1. SDSF를 통한 CICS 파일 열기/닫기 패턴

`CLCIFIL`/`OPCIFIL`이 SDSF의 ISFIN DD로 오퍼레이터 명령을 보내는 방식은 CICS 리전을 외부에서 원격 제어하는 메인프레임 관용구다. Java/Spring Boot 기반 현대화 환경에서는 이에 해당하는 개념이 없다. 대응 방법:

```java
// 현대화 관점: 데이터베이스 커넥션 풀 일시 중단 + 마이그레이션 + 재개
DataSource dataSource = ...;
HikariDataSource hikari = (HikariDataSource) dataSource;
hikari.suspendPool(); // CLCIFIL 대응
try {
    performBulkLoad();  // STEP05~STEP30 대응
} finally {
    hikari.resumePool(); // OPCIFIL 대응
}
```

### 2. IDCAMS DEFINE CLUSTER → DDL

KSDS 정의의 각 속성은 RDBMS DDL과 아래와 같이 대응된다.

| IDCAMS 파라미터 | 의미 | Java/RDBMS 대응 |
|---|---|---|
| `KEYS(16 0)` | 오프셋 0, 길이 16B 기본 키 | `PRIMARY KEY (tran_id VARCHAR(16))` |
| `RECORDSIZE(350 350)` | 고정 350바이트 레코드 | 정규화된 테이블 컬럼으로 분해 |
| `SHAREOPTIONS(2 3)` | 멀티-잡 공유 옵션(같은 볼륨 내 복수 잡 동시 접근 허용, 단 배타 제한) | 트랜잭션 격리 수준 설정 |
| `ERASE` | 삭제 시 데이터 영역을 0x00으로 덮어씀 | 보안 삭제 정책(PCI-DSS 폐기 요건 참고) |
| `CYLINDERS(1 5)` | 1실린더 기본 + 5실린더 증분 | 초기 용량 + 자동 증가(Auto Extend) |

### 3. VSAM KSDS vs RDBMS 테이블

VSAM KSDS는 키로 정렬된 B-트리 구조의 레코드 스토어로, 인덱스 없는 RDBMS 테이블 + 기본 키 인덱스에 해당한다. REPRO는 RDBMS의 `LOAD DATA INFILE`(MySQL) 또는 `COPY FROM`(PostgreSQL)에 대응한다.

### 4. AIX(보조 인덱스) → 비기본 키 인덱스

```sql
-- DEFINE ALTERNATEINDEX + BLDINDEX의 SQL 대응
CREATE INDEX idx_transact_timestamp
  ON transact (proc_timestamp);  -- KEYS(26 304): 오프셋 304의 26바이트 타임스탬프

-- NONUNIQUEKEY → 비유니크 인덱스(기본 CREATE INDEX 동작과 동일)
-- UPGRADE → 베이스 레코드 변경 시 AIX 자동 동기화(실시간 인덱스 갱신)
```

`NONUNIQUEKEY`가 중요하다. 같은 타임스탬프에 여러 거래 레코드가 존재할 수 있음을 의미한다(예: 배치로 일괄 생성된 이자 거래). Java/JPA의 `@Index(unique = false)` 에 해당.

### 5. 350바이트 고정 레코드와 타임스탬프 오프셋

AIX 키가 오프셋 304에서 시작한다는 것은 `CVTRA05Y` copybook의 350바이트 레이아웃에서 타임스탬프 필드가 정확히 그 위치에 있음을 의미한다. 현대화 시 레코드를 정규화된 테이블로 변환할 때, 오프셋 기반 접근이 아닌 명시적 컬럼 매핑을 사용해야 한다. `CVTRA05Y.cpy`를 확인해 타임스탬프 필드명과 정확한 오프셋을 검증할 것.

### 6. IF MAXCC LE 08 패턴

```jcl
DELETE AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS CLUSTER
IF MAXCC LE 08 THEN SET MAXCC = 0
```

이는 "없어도 OK"를 표현하는 IDCAMS 관용구다. Java로는 다음과 같다.

```java
try {
    dropTable("TRANSACT");
} catch (TableNotFoundException e) {
    // 테이블이 없어도 정상 — 무시
    log.info("Table TRANSACT does not exist, proceeding with creation.");
}
```

현대화된 마이그레이션 스크립트에서는 Flyway/Liquibase의 `IF EXISTS` 구문이 정확히 이 역할을 한다.

```sql
DROP TABLE IF EXISTS transact;
DROP INDEX IF EXISTS idx_transact_timestamp;
```

### 7. CICS 파일 이름 `CXACAIX`에 대하여

OPCIFIL/CLCIFIL이 `TRANSACT`와 함께 `CXACAIX`도 열고 닫는다. `CXACAIX`는 CICS CSD에 정의된 FILE 리소스명으로, AIX PATH(`AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH`)를 가리키는 논리 파일명이다(추측: CSD `app/csd/` 내 CARDDEMO 그룹의 CXACAIX FILE 정의 참조 필요). 온라인 프로그램이 타임스탬프로 거래를 조회할 때 이 AIX 경로를 통해 접근한다.

### 8. 현대화 단계별 권고

| 단계 | 권고 사항 |
|---|---|
| 데이터 추출 | CBEXPORT 잡 또는 REPRO로 KSDS를 평파일로 추출 후 ETL 적용 |
| 스키마 설계 | `CVTRA05Y`의 350바이트 레이아웃을 컬럼으로 분해. COMP-3 필드는 `BigDecimal`로 매핑 |
| 인덱스 전략 | KEYS(16 0) → `PRIMARY KEY`, AIX KEYS(26 304) → `CREATE INDEX ON proc_timestamp` |
| 벌크 로드 | Spring Batch `JdbcBatchItemWriter` + chunk 처리로 REPRO 대체 |
| CICS 파일 제어 | 운영 환경의 Rolling Deployment / Blue-Green 배포 전략으로 대체 |
