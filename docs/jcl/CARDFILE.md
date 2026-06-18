# CARDFILE — 카드 마스터 VSAM 전체 재구축 잡

- **유형**: JCL
- **한 줄 요약**: 기존 카드 VSAM KSDS(`CARDDATA`)와 계정ID 보조인덱스(`CARDAIX`)를 완전 삭제·재정의한 뒤 플랫파일에서 데이터를 적재하고, 보조인덱스를 빌드하여 CICS에 재오픈하는 데이터 리프레시 잡.

---

## 기능 설명

야간 배치 시퀀스(scripts/run_full_batch.sh)에서 "마스터 갱신" 단계를 담당하는 잡이다.
CardDemo의 카드 원장(CARDDATA VSAM KSDS)은 CICS 온라인 트랜잭션과 CB* 배치 프로그램이 공유하는 핵심 파일이다.
이 잡은 해당 파일을 플랫파일 소스(`.PS` 순차파일)로부터 완전히 재구축할 때 사용한다.

처리 흐름은 세 묶음이다.

1. **CICS 파일 닫기** — SDSF를 통해 CEMT 명령을 CICS 리전(`CICSAWSA`)에 전달, `CARDDAT`과 `CARDAIX` 파일을 CLOse 상태로 전환한다. 배치가 VSAM 클러스터를 독점 제어하기 위한 선행 조건이다.
2. **VSAM 재구축** — IDCAMS로 기존 클러스터·보조인덱스를 삭제하고, KSDS 클러스터를 새로 정의한 뒤 플랫파일 REPRO → 보조인덱스 DEFINE → PATH 연결 → BLDINDEX 순으로 처리한다.
3. **CICS 파일 열기** — SDSF로 CEMT OPEn 명령을 전달해 CICS가 재구축된 VSAM 파일을 다시 인식하도록 한다.

CICS 파일명(`CARDDAT`, `CARDAIX`)은 CSD에 등록된 FCT(File Control Table) 항목 이름이며, JCL 내 DSN과 별개임에 유의해야 한다.

---

## 스텝 구성

| 스텝명    | EXEC PGM / 도구 | IDCAMS 명령 / 역할                                                                 |
|-----------|-----------------|------------------------------------------------------------------------------------|
| CLCIFIL   | SDSF            | CICS 리전 `CICSAWSA`에 `/F` 명령으로 `CEMT SET FIL(CARDDAT) CLO` / `CEMT SET FIL(CARDAIX) CLO` 전달 — 배치 전 VSAM 파일 독점 확보 |
| STEP05    | IDCAMS          | `DELETE ... CLUSTER` — 기존 KSDS(`AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS`) 삭제; `IF MAXCC LE 08 THEN SET MAXCC=0`으로 "파일 없음(RC=8)" 오류 무시 후 `DELETE ... ALTERNATEINDEX` — 기존 AIX 삭제 |
| STEP10    | IDCAMS          | `DEFINE CLUSTER` — KSDS 신규 정의: 키 오프셋 0, 키 길이 16, 레코드 고정 150바이트, SHAREOPTIONS(2 3), CYLINDERS(1 5), 볼륨 AWSHJ1 |
| STEP15    | IDCAMS          | `REPRO INFILE(CARDDATA) OUTFILE(CARDVSAM)` — 플랫파일(`.PS`)에서 VSAM KSDS로 레코드 복사(순차→KSDS 벌크 적재) |
| STEP40    | IDCAMS          | `DEFINE ALTERNATEINDEX` — 계정ID 보조인덱스 정의: 오프셋 16 기준 키 길이 11, NONUNIQUEKEY(계정 하나에 카드 여러 장), UPGRADE, CYLINDERS(5 1) |
| STEP50    | IDCAMS          | `DEFINE PATH` — 보조인덱스(`AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX`)와 기반 클러스터를 연결하는 PATH(`...AIX.PATH`) 정의 |
| STEP60    | IDCAMS          | `BLDINDEX` — KSDS 전체를 스캔해 AIX 클러스터를 실제로 채움(인덱스 빌드) |
| OPCIFIL   | SDSF            | CICS 리전에 `CEMT SET FIL(CARDDAT) OPE` / `CEMT SET FIL(CARDAIX) OPE` 전달 — CICS 정상 서비스 복원 |

---

## 의존성

### COPY (PROC/INCLUDE)
없음 — 인라인 JCL로만 구성되며 별도 PROC나 INCLUDE 구문은 사용하지 않는다.

### 호출 프로그램 (EXEC PGM)
- **SDSF** (IBM SDSF 유틸리티): ISPF/TSO 밖에서 CICS 리전에 MVS 콘솔 명령(`/F`)을 전달하는 용도. `ISFIN` DD에 명령을 인라인으로 전달, `ISFOUT`·`CMDOUT`은 SYSOUT으로 수신.
- **IDCAMS** (IBM Access Method Services): VSAM 클러스터 관리 표준 유틸리티 — DELETE, DEFINE CLUSTER, REPRO, DEFINE ALTERNATEINDEX, DEFINE PATH, BLDINDEX 모두 이 단일 프로그램이 처리. `SYSIN` DD에 명령문을 인라인으로 전달, `SYSPRINT`로 실행 결과 수신.

### 데이터셋/파일/DB 테이블

| 역할 | DSN | 유형 | 조직 |
|------|-----|------|------|
| 카드 마스터 VSAM(기반 클러스터) | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` | VSAM KSDS | 키: 오프셋 0, 길이 16 (카드번호) |
| 카드 마스터 데이터 컴포넌트 | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS.DATA` | VSAM KSDS data | 레코드 고정 150B |
| 카드 마스터 인덱스 컴포넌트 | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS.INDEX` | VSAM KSDS index | — |
| 계정ID 보조인덱스 클러스터 | `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX` | VSAM AIX | 오프셋 16, 길이 11 (계정ID); NONUNIQUEKEY |
| AIX 데이터 컴포넌트 | `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.DATA` | VSAM AIX data | — |
| AIX 인덱스 컴포넌트 | `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.INDEX` | VSAM AIX index | — |
| AIX PATH | `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.PATH` | VSAM PATH | AIX → KSDS 연결 |
| 소스 플랫파일(REPRO 입력) | `AWS.M2.CARDDEMO.CARDDATA.PS` | PS (Physical Sequential) | LRECL=150, RECFM=FB (추측) |

레코드 레이아웃은 copybook `app/cpy/CVACT02Y` (`CARD-RECORD`, 150바이트)에 정의된다.
기본 키(오프셋 0, 16바이트)는 카드번호(`CARD-NUM PIC 9(16)`), 보조 키(오프셋 16, 11바이트)는 계정ID(`CARD-ACCT-ID PIC 9(11)`)에 해당한다.
CICS FCT에서 이 파일은 `CARDDAT`(기본 키 접근), `CARDAIX`(계정ID 경유 접근)라는 두 파일명으로 등록되어 있다.

### 선행/후행 잡

| 구분 | 잡/스텝 | 설명 |
|------|---------|------|
| 선행 | 플랫파일 생성 잡 (미식별) | `AWS.M2.CARDDEMO.CARDDATA.PS`가 미리 존재해야 함. 이 잡 내부에는 해당 파일을 만드는 스텝이 없다(추측). |
| 후행 | POSTTRAN (`CBTRN02C`) | 카드 VSAM이 정상 적재된 뒤 일일 거래 전기 배치 실행 |
| 후행 | INTCALC (`CBACT04C`) | 이자 계산 배치 |
| 후행 | TRANIDX 등 | 거래 AIX 정의·CICS 재오픈 잡 군 |

야간 배치 시퀀스(run_full_batch.sh)에서 CARDFILE은 ACCTFILE/XREFFILE/CUSTFILE 등 마스터 갱신 잡 그룹에 속하며 POSTTRAN보다 먼저 실행되어야 한다.

---

## Java/현대화 노트

### SDSF를 통한 CICS 파일 제어

CLCIFIL/OPCIFIL 스텝은 CICS가 VSAM 파일의 열림 상태를 FCT에서 관리하기 때문에 필요하다.
Java/Spring 환경에서는 이에 해당하는 외부 파일 제어 절차가 없다. VSAM 파일을 RDS(예: Aurora PostgreSQL)나 S3로 대체하면 이 스텝 자체가 불필요해진다.
AWS Mainframe Modernization(M2) 서비스를 사용할 경우, 관리형 CICS(Blu Age 런타임) 환경의 파일 상태 관리 API로 대응할 수 있다.

### IDCAMS DELETE + `IF MAXCC LE 08 THEN SET MAXCC=0` 패턴

삭제 대상 파일이 이미 없을 때 IDCAMS RC=8이 발생하는데, 이를 정상(RC=0)으로 덮어써 이후 스텝이 중단되지 않도록 한다.
Java 코드로 치면 "파일이 존재하면 삭제" 패턴이다.

```java
// IDCAMS DELETE + IF MAXCC LE 08 에 해당하는 Java
if (Files.exists(targetPath)) {
    Files.delete(targetPath);
}
```

Flyway/Liquibase의 `IF EXISTS` DROP 구문과 동일한 멱등성(idempotency) 확보 기법이다.

### VSAM KSDS → 관계형 DB / NoSQL 매핑

KSDS(Key-Sequenced Data Set)는 기본 키로 정렬된 B-트리 인덱스 파일이다.

| VSAM 개념 | Java/현대 대응 |
|-----------|---------------|
| KSDS CLUSTER | 테이블(PK 인덱스 포함) 또는 DynamoDB 테이블 |
| 기본 키(KEYS(16 0)) | PRIMARY KEY (card_num CHAR(16)) |
| 보조인덱스(AIX, NONUNIQUEKEY) | 비고유 인덱스(CREATE INDEX) 또는 GSI |
| PATH | 인덱스를 통한 조회 경로 — 별도 객체 불필요 |
| BLDINDEX | 초기 인덱스 빌드 → `CREATE INDEX CONCURRENTLY` 또는 ETL 이후 인덱스 생성 전략 |
| REPRO (PS → KSDS) | CSV/Parquet → DB 벌크 INSERT (`COPY`, `LOAD DATA`) |

### NONUNIQUEKEY 보조인덱스의 의미

한 계정(`CARD-ACCT-ID`, 11바이트)에 여러 카드가 연결될 수 있음을 나타낸다.
온라인 프로그램이 `CARDAIX` 경로로 계정ID를 조회하면 해당 계정의 카드 전체를 순차 탐색(Generic Key 조회)할 수 있다.
Java에서는 `SELECT * FROM card WHERE acct_id = ?` 로 동일한 결과를 얻는다.

### SHAREOPTIONS(2 3)

- 첫 번째 숫자(2): 동일 시스템 내 여러 잡이 동시에 읽기(여러 SHARE)하되, 쓰기는 하나만 가능.
- 두 번째 숫자(3): 다중 시스템(XCF/GRS) 간에는 완전 공유 허용.

Java 환경에서는 DB 커넥션 풀 + 트랜잭션 격리 수준으로 대응하며, 이 JCL에서 CLCIFIL/OPCIFIL로 CICS 파일을 명시적으로 닫고/여는 이유가 바로 이 공유 모드 때문이다. CICS가 파일을 열어 둔 상태에서 배치가 DELETE/DEFINE을 시도하면 오류가 발생한다.

### CYLINDERS(1 5) / CYLINDERS(5 1) 스토리지 할당

IBM DASD(Direct Access Storage Device) 고유 단위로, `(primary secondary)` 형식이다. 주 공간이 가득 차면 보조 공간을 최대 15회까지 자동 확장한다.
AWS M2 관리형 환경이나 zD&T(z Development and Test)에서는 실제 실린더가 아닌 MB/GB 단위로 재정의된다. 마이그레이션 시 예상 데이터 크기(레코드 수 × 150B)를 기준으로 스토리지 크기를 재산정해야 한다.

### ERASE 옵션

클러스터 삭제 시 실제 데이터 영역을 0으로 덮어 쓴다. 카드 번호 등 민감정보를 포함하므로 보안상 필요한 설정이다. Java/클라우드 환경에서는 DB 삭제 후 스토리지 암호화 + 퍼지 정책(S3 Object Lifecycle, Aurora 자동 삭제)으로 대응한다.
