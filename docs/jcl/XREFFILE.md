# XREFFILE — 카드 교차참조(CARDXREF) VSAM 파일 초기 적재 잡

- **유형**: JCL
- **한 줄 요약**: 카드번호↔계정번호↔고객번호 교차참조 VSAM KSDS를 삭제·재정의한 뒤 순차 평면 파일에서 초기 데이터를 적재하고, 계정 ID 기준 Alternate Index(AIX)를 구축하는 데이터 초기화 잡

---

## 기능 설명

XREFFILE 잡은 CardDemo 핵심 VSAM 파일 중 하나인 `CARDXREF` 클러스터의 "삭제 → 재정의 → 데이터 적재 → AIX 구축" 전체 주기를 단일 잡으로 처리한다.

`CARDXREF`는 카드번호(16자리 기본 키)를 기준으로 계정 ID(11자리)와 고객 ID를 연결하는 교차참조 테이블이다. copybook `CVACT03Y`의 `CARD-XREF-RECORD`가 이 레코드 레이아웃을 정의하며, 배치 잡 CBTRN02C(거래 입력)와 CBACT04C(이자 계산) 등이 카드번호로 계정을 역조회할 때 이 파일을 사용한다.

잡이 완료되면 두 가지 접근 경로가 생긴다.

- **기본 클러스터 경로**: 카드번호(KEYS(16 0) → 오프셋 0, 길이 16) 기준 직접 조회
- **AIX 경로**: 계정 ID(KEYS(11,25) → 오프셋 25, 길이 11) 기준 역조회(NONUNIQUEKEY — 한 계정에 복수 카드 가능)

Java 세계에서 비교하면, 기본 클러스터는 `Map<String, CardXrefRecord>` (카드번호 → 레코드)이고, AIX+PATH는 `Map<String, List<CardXrefRecord>>` (계정ID → 카드목록)에 해당하는 보조 인덱스다.

---

## 스텝 구성

| 스텝명 | EXEC PGM | 역할 |
|--------|----------|------|
| STEP05 | IDCAMS | 기존 KSDS 클러스터(`CARDXREF.VSAM.KSDS`)와 AIX(`CARDXREF.VSAM.AIX`)를 삭제. 존재하지 않아 RC=8이 반환되더라도 `IF MAXCC LE 08 THEN SET MAXCC = 0`으로 정상 처리(멱등성 보장) |
| STEP10 | IDCAMS | KSDS 클러스터 신규 정의. 고정 레코드 50바이트, 기본 키 오프셋 0·길이 16, 볼륨 AWSHJ1, 1개 실린더(최초)+5개 보조 할당 |
| STEP15 | IDCAMS | `REPRO INFILE(XREFDATA) OUTFILE(XREFVSAM)` — 순차 평면 파일 `CARDXREF.PS`를 방금 정의한 KSDS에 복사(벌크 적재). XREFDATA DD가 소스, XREFVSAM DD가 대상 |
| STEP20 | IDCAMS | DEFINE ALTERNATEINDEX — 계정 ID(오프셋 25, 길이 11) 기준 AIX 정의. NONUNIQUEKEY(1:N 허용), UPGRADE(기본 클러스터 갱신 시 AIX 자동 동기화), 볼륨 AWSHJ1 |
| STEP25 | IDCAMS | DEFINE PATH — AIX와 기본 클러스터를 연결하는 논리 경로 `CARDXREF.VSAM.AIX.PATH` 정의. PATH를 통해 계정 ID로 KSDS 레코드에 투명하게 접근 가능 |
| STEP30 | IDCAMS | BLDINDEX — 기본 클러스터(`KSDS`)의 전체 레코드를 스캔해 AIX 클러스터(`AIX`)를 실제로 구축. 이 스텝 전까지 AIX는 정의만 있고 내용이 없음 |

> **IDCAMS 전용**: 모든 스텝이 IBM 유틸리티 프로그램 `IDCAMS`(Access Method Services)만 사용한다. COBOL 배치 프로그램은 이 잡에 존재하지 않는다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음. 카탈로그 프로시저를 참조하지 않는다.

- **호출 프로그램 (EXEC PGM)**: `IDCAMS` 단독 — IBM 시스템 유틸리티(VSAM 관리 전담). COBOL 프로그램 호출 없음.

- **데이터셋/파일/DB 테이블**:

  | 데이터셋 논리명 / DSN | 역할 | 방향 |
  |---|---|---|
  | `AWS.M2.CARDDEMO.CARDXREF.PS` (DD: XREFDATA) | 초기 적재용 순차 평면 파일(소스). LRECL=50, RECFM=FB 추정(추측) | INPUT |
  | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` (DD: XREFVSAM) | 카드 교차참조 기본 VSAM KSDS 클러스터. copybook `CVACT03Y`의 `CARD-XREF-RECORD` 레이아웃 | OUTPUT(정의·적재 대상) |
  | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS.DATA` | KSDS 데이터 컴포넌트 | 내부(IDCAMS 자동 관리) |
  | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS.INDEX` | KSDS 인덱스 컴포넌트 | 내부(IDCAMS 자동 관리) |
  | `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX` | 계정 ID 기준 Alternate Index | OUTPUT(정의·구축 대상) |
  | `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.DATA` | AIX 데이터 컴포넌트 | 내부 |
  | `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.INDEX` | AIX 인덱스 컴포넌트 | 내부 |
  | `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH` | AIX와 기본 클러스터를 연결하는 논리 PATH | OUTPUT(정의 대상) |

- **선행/후행 잡**:
  - **선행**: `CLOSEFIL` — CICS 리전이 CARDXREF 파일을 열고 있을 경우 먼저 닫아야 한다. 야간 배치 시퀀스의 마스터 갱신 단계(`remote_refresh.sh`) 첫머리에 CLOSEFIL이 위치한다.
  - **후행**: `TRANIDX` — 야간 배치에서 AIX 재구축이 필요한 다른 파일(예: TRANSACT)의 인덱스도 별도 잡이 담당. 이후 `OPENFIL`로 CICS에 파일을 재오픈해야 온라인 거래가 재개된다.
  - **소비자 배치**: CBTRN02C(POSTTRAN), CBACT04C(INTCALC) — 두 잡이 `CARDXREF.VSAM.KSDS`(랜덤 READ)를 사용하므로 XREFFILE이 완료된 후에 실행되어야 한다.

---

## Java/현대화 노트

### 1. IDCAMS → Java 세계에서의 역할 분리

IDCAMS는 "스키마 정의 + 데이터 적재 + 인덱스 구축"을 하나의 유틸리티가 담당하는 메인프레임 전용 도구다. 현대화 시 이 역할은 다음처럼 분리된다.

| IDCAMS 기능 | Java/현대 등가물 |
|---|---|
| DELETE CLUSTER | `DROP TABLE IF EXISTS cardxref` (RDB) 또는 인덱스/테이블 삭제 마이그레이션 스크립트 |
| DEFINE CLUSTER | `CREATE TABLE cardxref (card_num CHAR(16) PRIMARY KEY, ...)` + Flyway/Liquibase 마이그레이션 |
| REPRO (벌크 복사) | Spring Batch `FlatFileItemReader` → `JdbcBatchItemWriter` 파이프라인, 또는 DB `COPY`/`LOAD` 명령 |
| DEFINE ALTERNATEINDEX | `CREATE INDEX idx_cardxref_acct_id ON cardxref(acct_id)` |
| BLDINDEX | `ANALYZE TABLE cardxref` (통계 갱신) 또는 인덱스 자동 구축(대부분의 RDB는 `CREATE INDEX` 시 즉시 구축) |

### 2. 레코드 레이아웃 (CVACT03Y 기준)

CARDXREF 레코드는 50바이트 고정 길이이며, copybook `app/cpy/CVACT03Y`의 `CARD-XREF-RECORD`가 정의한다. 메모리에 저장된 레이아웃 정보에 따르면 주요 필드는 다음과 같다.

```
01 CARD-XREF-RECORD.
   05 XREF-CARD-NUM   PIC X(16).   -- 기본 키, 오프셋 0, 길이 16
   05 XREF-ACCT-ID    PIC 9(11).   -- AIX 키, 오프셋 25(추측), 길이 11
   05 XREF-CUST-ID    PIC 9(11).   -- 고객 ID
   05 FILLER          PIC X(??).   -- 나머지 패딩 (총 50바이트)
```

STEP20의 `KEYS(11,25)` — 길이 11, 오프셋 25 — 가 XREF-ACCT-ID의 물리적 위치를 확인해 준다.

Java DTO 예시:

```java
public class CardXrefRecord {
    private String cardNumber;    // PIC X(16) → String, 정확히 16자 패딩
    private long   accountId;     // PIC 9(11) → long (11자리는 int 초과)
    private long   customerId;    // PIC 9(11) → long
}
```

### 3. NONUNIQUEKEY — 1:N 관계 주의

AIX의 `NONUNIQUEKEY` 선언은 하나의 계정 ID가 여러 카드번호에 매핑될 수 있음을 의미한다(1 계정 : N 카드). COBOL 프로그램이 계정 ID로 CARDXREF를 조회할 때는 `READ NEXT` 루프로 같은 AIX 키를 가진 레코드를 모두 순회한다. Java로 마이그레이션하면 `List<CardXrefRecord>` 반환이 자연스럽다.

### 4. IF MAXCC LE 08 패턴 — 멱등성(idempotency)

```
DELETE AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS CLUSTER
IF MAXCC LE 08 THEN SET MAXCC = 0
```

파일이 없어서 `DELETE`가 RC=8로 실패해도 잡을 계속 진행하는 멱등성 패턴이다. 현대화 시 `DROP TABLE IF EXISTS` 또는 마이그레이션 프레임워크의 체크섬 기반 idempotent 마이그레이션이 이에 대응한다.

### 5. SHAREOPTIONS(2 3) — 동시 접근 제어

`SHAREOPTIONS(2 3)`: 동일 시스템에서는 여러 잡이 읽기 공유 가능하나 하나만 쓰기 가능(2), 크로스 시스템(시스톨렉스)에서는 복수 쓰기도 허용(3). 현대화 시 데이터베이스의 트랜잭션 격리 수준(READ COMMITTED 등)으로 대체된다.

### 6. EBCDIC 인코딩 주의

소스 평면 파일 `CARDXREF.PS`는 EBCDIC 인코딩이다(`app/data/EBCDIC/` 디렉터리 참조). 마이그레이션 시 ASCII 변환이 필요하며, PIC X 필드는 단순 인코딩 변환으로 처리되지만, COMP/COMP-3 필드가 레코드 내에 존재한다면 바이너리 변환 로직이 별도로 필요하다.

### 7. 볼륨 'AWSHJ1'

`VOLUMES(AWSHJ1)`은 메인프레임 DASD 볼륨 시리얼 번호다. 현대화 환경(AWS M2/UniKix)에서는 이 파라미터가 무시되거나 환경 전용 볼륨으로 대체된다. Java/클라우드 환경에서는 스토리지 위치 개념이 파일 시스템 경로 또는 S3 버킷 ARN으로 전환된다.
