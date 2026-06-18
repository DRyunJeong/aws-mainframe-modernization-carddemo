# CBIMPORT — 다중 레코드 익스포트 파일 분할 임포트 잡

- **유형**: JCL (단일 스텝 배치 잡)
- **한 줄 요약**: CBEXPORT가 생성한 다중 레코드 타입(C/A/X/T/D) VSAM 익스포트 파일을 읽어 고객·계정·카드XREF·거래·카드 5개 정규화 순차 파일로 분리 기록하고 오류 레코드를 별도 파일에 출력하는 브랜치 마이그레이션 임포트 잡.

---

## 기능 설명

CBIMPORT 잡은 CardDemo의 **브랜치 마이그레이션 시나리오**에서 사용되는 데이터 임포트 잡이다. 자매 잡인 CBEXPORT가 VSAM KSDS 5개 파일(고객·계정·카드XREF·거래·카드)을 하나의 다중 레코드 VSAM KSDS 파일(`AWS.M2.CARDDEMO.EXPORT.DATA`)로 통합 내보낸 결과물을, CBIMPORT가 역방향으로 받아서 레코드 타입 식별자(`EXPORT-REC-TYPE`) 1바이트를 보고 분기하여 각각의 정규화 순차 파일로 다시 분리 저장한다.

처리 흐름은 전형적인 CB\* 배치 골격을 따른다:

1. `1000-INITIALIZE` — 현재일자·시각 조립 후 `1100-OPEN-FILES`에서 파일 7개(입력 1, 출력 5, 에러 1) 오픈. 오픈 실패 시 즉시 `9999-ABEND-PROGRAM`(→`CALL 'CEE3ABD'`).
2. `2000-PROCESS-EXPORT-FILE` — `PERFORM UNTIL WS-EXPORT-EOF` 루프로 EXPORT-INPUT을 순차 읽으며 `2200-PROCESS-RECORD-BY-TYPE`을 호출.
3. `2200-PROCESS-RECORD-BY-TYPE` — `EVALUATE EXPORT-REC-TYPE`으로 레코드 타입 디스패치(Java `switch` 패턴):
   - `'C'` → `2300-PROCESS-CUSTOMER-RECORD` → CUSTOUT 기록
   - `'A'` → `2400-PROCESS-ACCOUNT-RECORD` → ACCTOUT 기록
   - `'X'` → `2500-PROCESS-XREF-RECORD` → XREFOUT 기록
   - `'T'` → `2600-PROCESS-TRAN-RECORD` → TRNXOUT 기록
   - `'D'` → `2650-PROCESS-CARD-RECORD` → CARDOUT(JCL에 미정의, 아래 주의 참조)
   - `WHEN OTHER` → `2700-PROCESS-UNKNOWN-RECORD` → ERROUT 기록
4. `3000-VALIDATE-IMPORT` — 현재는 DISPLAY 두 줄만 출력하는 **스텁(stub)**. 실제 유효성 검증 로직 없음(추후 구현 예상).
5. `4000-FINALIZE` — 파일 7개 CLOSE, 통계(읽은 레코드 수, 레코드 타입별 임포트 수, 오류 수) DISPLAY 후 종료.

### 핵심 데이터 구조 — CVEXPORT.cpy

WORKING-STORAGE에 `COPY CVEXPORT`로 포함되는 `EXPORT-RECORD`(500바이트)는 **REDEFINES 기반 유니온** 구조이다.

```
01 EXPORT-RECORD.                            ← 전체 500바이트
   05 EXPORT-REC-TYPE        PIC X(1)        ← 레코드 타입 키 (C/A/X/T/D)
   05 EXPORT-TIMESTAMP       PIC X(26)       ← 타임스탬프
      05 EXPORT-TIMESTAMP-R  REDEFINES ...   ← 날짜(10)+구분자(1)+시각(15) 재해석
   05 EXPORT-SEQUENCE-NUM    PIC 9(9) COMP   ← 4바이트 이진 시퀀스 번호 (VSAM 키: 오프셋 28, 길이 4)
   05 EXPORT-BRANCH-ID       PIC X(4)
   05 EXPORT-REGION-CODE     PIC X(5)
   05 EXPORT-RECORD-DATA     PIC X(460)      ← 페이로드 영역
      05 EXPORT-CUSTOMER-DATA  REDEFINES ... ← 타입 'C' 해석
      05 EXPORT-ACCOUNT-DATA   REDEFINES ... ← 타입 'A' 해석
      05 EXPORT-TRANSACTION-DATA REDEFINES ...← 타입 'T' 해석
      05 EXPORT-CARD-XREF-DATA REDEFINES ... ← 타입 'X' 해석
      05 EXPORT-CARD-DATA      REDEFINES ... ← 타입 'D' 해석
```

Java에서 `REDEFINES`의 직접 대응 개념은 없다. 가장 가까운 표현은 같은 바이트 버퍼를 여러 구조체로 해석하는 패턴으로, Java에서는 `ByteBuffer` + 타입별 파서 클래스 또는 sealed interface + record로 표현한다:

```java
// Java 대응 개념 예시
sealed interface ExportRecord permits CustomerRecord, AccountRecord, XrefRecord,
                                      TransactionRecord, CardRecord { }

record CustomerRecord(long custId, String firstName, ...) implements ExportRecord {}

ExportRecord parse(byte[] raw500) {
    char recType = (char) raw500[0]; // EXPORT-REC-TYPE
    return switch (recType) {
        case 'C' -> parseCustomer(raw500);
        case 'A' -> parseAccount(raw500);
        // ...
        default  -> throw new UnknownRecordTypeException(recType);
    };
}
```

### 출력 파일 레코드 레이아웃

| DD명    | 레코드 길이 | LRECL | 카피북     | 내용                          |
|---------|------------|-------|------------|-------------------------------|
| CUSTOUT | 500바이트  | 500   | CVCUS01Y   | 고객 마스터 레코드              |
| ACCTOUT | 300바이트  | 300   | CVACT01Y   | 계정 마스터 레코드              |
| XREFOUT | 50바이트   | 50    | CVACT03Y   | 카드↔계정↔고객 교차참조         |
| TRNXOUT | 350바이트  | 350   | CVTRA05Y   | 거래 레코드                    |
| ERROUT  | 132바이트  | 132   | (인라인 WS) | 오류 레코드 (타임스탬프\|타입\|시퀀스\|메시지) |

---

## 스텝 구성

| 스텝명  | EXEC PGM/PROC | 역할                                                                 |
|---------|--------------|----------------------------------------------------------------------|
| STEP01  | PGM=CBIMPORT | `AWS.M2.CARDDEMO.EXPORT.DATA`(VSAM KSDS)를 순차 읽어 레코드 타입별로 분리하여 5개 순차 출력 파일에 기록, 오류 레코드를 ERROUT에 기록 |

잡은 단일 스텝으로 구성된다. PROC(카탈로그 프로시저) 사용 없음.

---

## 의존성

- **COPY (PROC/INCLUDE)**:
  - `CVEXPORT` (`app/cpy/CVEXPORT.cpy`) — 500바이트 다중 레코드 REDEFINES 레이아웃. EXPORT-REC-TYPE 타입 식별자, EXPORT-SEQUENCE-NUM(COMP, VSAM 키), 5가지 페이로드 REDEFINES 포함.
  - `CVCUS01Y` (`app/cpy/CVCUS01Y.cpy`) — CUSTOMER-OUTPUT FD 레코드 구조 (LRECL=500).
  - `CVACT01Y` (`app/cpy/CVACT01Y.cpy`) — ACCOUNT-OUTPUT FD 레코드 구조 (LRECL=300).
  - `CVACT03Y` (`app/cpy/CVACT03Y.cpy`) — XREF-OUTPUT FD 레코드 구조 (LRECL=50).
  - `CVTRA05Y` (`app/cpy/CVTRA05Y.cpy`) — TRANSACTION-OUTPUT FD 레코드 구조 (LRECL=350).
  - `CVACT02Y` (`app/cpy/CVACT02Y.cpy`) — CARD-OUTPUT FD 레코드 구조 (LRECL=150). ※ JCL에 CARDOUT DD 정의 누락 주의(아래 참조).

- **호출 프로그램 (EXEC PGM)**:
  - `CBIMPORT` (`app/cbl/CBIMPORT.cbl`) — 로드 모듈은 `AWS.M2.CARDDEMO.LOADLIB`에서 참조 (STEP01 `STEPLIB DD`). `CEE3ABD` — LE(Language Environment) 강제 abend 루틴. 파일 오픈·기록 오류 시 호출.

- **데이터셋/파일/DB 테이블**:

  | DD명    | DSN                                    | DISP          | RECFM | LRECL | 용도              |
  |---------|----------------------------------------|---------------|-------|-------|-------------------|
  | EXPFILE | `AWS.M2.CARDDEMO.EXPORT.DATA`          | SHR           | —     | 500   | 입력: VSAM KSDS 익스포트 파일 (CBEXPORT 생성물) |
  | CUSTOUT | `AWS.M2.CARDDEMO.CUSTDATA.IMPORT`      | NEW,CATLG,DELETE | FB | 500   | 출력: 고객 순차 파일 |
  | ACCTOUT | `AWS.M2.CARDDEMO.ACCTDATA.IMPORT`      | NEW,CATLG,DELETE | FB | 300   | 출력: 계정 순차 파일 |
  | XREFOUT | `AWS.M2.CARDDEMO.CARDXREF.IMPORT`      | NEW,CATLG,DELETE | FB | 50    | 출력: 카드XREF 순차 파일 |
  | TRNXOUT | `AWS.M2.CARDDEMO.TRANSACT.IMPORT`      | NEW,CATLG,DELETE | FB | 350   | 출력: 거래 순차 파일 |
  | ERROUT  | `AWS.M2.CARDDEMO.IMPORT.ERRORS`        | NEW,CATLG,DELETE | FB | 132   | 출력: 오류 레코드 파일 |
  | STEPLIB | `AWS.M2.CARDDEMO.LOADLIB`              | SHR           | —     | —     | 로드 라이브러리 |

  CBIMPORT 프로그램의 FILE-CONTROL에는 `CARD-OUTPUT ASSIGN TO CARDOUT`이 선언되어 있으나, **JCL에 CARDOUT DD 문이 없다** (행 22~69 전체 확인). 프로그램이 `OPEN OUTPUT CARD-OUTPUT`을 수행하면 STATUS가 비정상이 되어 `9999-ABEND-PROGRAM`으로 분기할 가능성이 높다(추측). 실행 전 JCL에 CARDOUT DD 추가 필요.

- **선행/후행 잡**:
  - **선행**: `CBEXPORT` (`app/jcl/CBEXPORT.jcl`) — IDCAMS로 `AWS.M2.CARDDEMO.EXPORT.DATA` VSAM KSDS를 정의(STEP01)하고 CBEXPORT 프로그램으로 5개 VSAM 파일을 통합 익스포트(STEP02). CBIMPORT의 EXPFILE 입력은 이 잡이 생성한 결과물이다.
  - **후행**: 임포트 결과 순차 파일(CUSTOUT/ACCTOUT/XREFOUT/TRNXOUT)을 VSAM KSDS로 적재하는 별도 잡이 필요하다고 예상되나, 현재 CardDemo JCL 디렉토리에 해당 잡은 확인되지 않는다(추측). 배치 시퀀스 상 CBEXPORT → CBIMPORT → VSAM 적재 순서로 운영될 것으로 판단된다.

---

## Java/현대화 노트

### 1. REDEFINES 유니온 → sealed interface + record

CVEXPORT.cpy의 `EXPORT-RECORD-DATA REDEFINES` 5개 오버레이는 하나의 460바이트 영역을 5가지 타입으로 재해석하는 C 언어 `union`에 해당한다. Java에는 직접 대응이 없다.

현대화 시 권장 패턴:

```java
// 레코드 타입 공통 헤더
record ExportHeader(char recType, String timestamp, long seqNum,
                    String branchId, String regionCode) {}

sealed interface ExportPayload permits CustomerPayload, AccountPayload,
                                       XrefPayload, TransactionPayload, CardPayload {}

record CustomerPayload(long custId, String firstName, String lastName, ...) implements ExportPayload {}
record AccountPayload(long acctId, String activeStatus, BigDecimal currBal, ...) implements ExportPayload {}
// ...

record ExportRecord(ExportHeader header, ExportPayload payload) {}
```

### 2. COMP / COMP-3 필드 Java 매핑

| COBOL 필드 | 스토리지 | Java 타입 |
|-----------|----------|-----------|
| `EXPORT-SEQUENCE-NUM PIC 9(9) COMP` | 4바이트 이진 정수 | `int` (부호 없음이면 `long`) |
| `EXP-CUST-ID PIC 9(09) COMP` | 4바이트 이진 정수 | `int` 또는 `long` |
| `EXP-CUST-FICO-CREDIT-SCORE PIC 9(03) COMP-3` | 팩 10진 2바이트 | `short` 또는 `int` |
| `EXP-ACCT-CURR-BAL PIC S9(10)V99 COMP-3` | 팩 10진 7바이트, 소수점 2자리 | `BigDecimal` (scale=2) |
| `EXP-ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 COMP-3` | 팩 10진 7바이트, 소수점 2자리 | `BigDecimal` (scale=2) |
| `EXP-ACCT-CURR-CYC-DEBIT PIC S9(10)V99 COMP` | 8바이트 이진, 소수점 2자리(묵시적) | `BigDecimal` 또는 `long`(x100 정수) |
| `EXP-TRAN-AMT PIC S9(09)V99 COMP-3` | 팩 10진 6바이트, 소수점 2자리 | `BigDecimal` (scale=2) |
| `EXP-TRAN-MERCHANT-ID PIC 9(09) COMP` | 4바이트 이진 | `int` 또는 `long` |
| `EXP-XREF-ACCT-ID PIC 9(11) COMP` | 8바이트 이진 (11자리 → 4바이트 초과) | `long` |
| `EXP-CARD-ACCT-ID PIC 9(11) COMP` | 8바이트 이진 | `long` |
| `EXP-CARD-CVV-CD PIC 9(03) COMP` | 2바이트 이진 | `short` 또는 `int` |

COMP-3(팩 10진)를 Java에서 읽을 때는 IBM Packed Decimal 디코더가 필요하다. Apache Commons Codec 또는 직접 구현 필요:

```java
// COMP-3 PIC S9(10)V99 디코드 예시 (7바이트)
BigDecimal unpackS9_10V99(byte[] packed7) {
    // 마지막 니블: C=양수, D=음수
    // 소수점은 V99 → scale 2 적용
    long raw = PackedDecimalConverter.decode(packed7);
    boolean negative = (packed7[6] & 0x0F) == 0xD;
    return BigDecimal.valueOf(negative ? -raw : raw, 2);
}
```

### 3. Spring Batch 마이그레이션 패턴

CBIMPORT의 구조는 Spring Batch의 **multi-line / multi-record ItemReader** 패턴에 자연스럽게 대응된다:

```
EXPORT-INPUT 순차 읽기
  → EVALUATE EXPORT-REC-TYPE
  → 타입별 WRITE
```

Spring Batch로의 대응:

| COBOL 구조 | Spring Batch 대응 |
|-----------|------------------|
| `PERFORM UNTIL WS-EXPORT-EOF` 메인 루프 | `ItemReader<ExportRecord>` + Step |
| `EVALUATE EXPORT-REC-TYPE` 분기 | `ClassifierCompositeItemWriter` 또는 커스텀 `ItemWriter` 내부 switch |
| CUSTOUT / ACCTOUT / TRNXOUT 등 멀티 출력 | `CompositeItemWriter` + 조건별 라우팅 |
| ERROUT 오류 기록 | `SkipListener.onSkipInWrite()` + 별도 파일 ItemWriter |
| 통계 DISPLAY (4000-FINALIZE) | `StepExecutionListener.afterStep()` → `ExecutionContext` 통계 |
| `9999-ABEND-PROGRAM` (`CALL 'CEE3ABD'`) | `@OnWriteError` / `FatalException` → Job 즉시 실패 |

### 4. JCL CARDOUT DD 누락 문제

CBIMPORT 프로그램(행 63~66)에는 `SELECT CARD-OUTPUT ASSIGN TO CARDOUT`이 선언되어 있고, `1100-OPEN-FILES`(행 232~244)에서 `OPEN OUTPUT CARD-OUTPUT`을 실행한다. 그러나 JCL에 `//CARDOUT DD` 문이 없다. VSAM/순차 파일의 `OPEN OUTPUT` 시 해당 DD가 없으면 FILE STATUS가 `35`(파일 없음)가 되어 `9999-ABEND-PROGRAM`이 호출된다. 현재 JCL 그대로 실행하면 ABEND가 발생할 가능성이 높다(추측). 현대화 시 카드 레코드 출력 파일을 처리 파이프라인에 포함해야 한다.

### 5. 3000-VALIDATE-IMPORT 스텁 처리

CBIMPORT.cbl 행 449~452의 `3000-VALIDATE-IMPORT` 단락은 DISPLAY 두 줄만 있는 **미구현 스텁**이다. 주석(행 7~11)의 "Validate data integrity using checksums"는 체크섬 검증 기능이 의도되었으나 아직 구현되지 않았음을 나타낸다. Java 마이그레이션 시 이 지점에 실제 검증 로직(레코드 수 일치, 체크섬 검증, 참조 무결성 확인 등)을 추가해야 한다.
