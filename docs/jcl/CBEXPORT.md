# CBEXPORT — 고객 데이터 마이그레이션 익스포트 잡

- **유형**: JCL (배치 잡)
- **한 줄 요약**: 5개 VSAM KSDS 파일(고객·계정·카드·카드-계정 크로스레퍼런스·거래)을 순차 읽기하여 레코드 타입이 혼재하는 단일 500바이트 고정 길이 VSAM KSDS 익스포트 파일로 통합 출력하는 브랜치 마이그레이션용 데이터 추출 잡.

---

## 기능 설명

CBEXPORT 잡은 두 스텝으로 구성된다.

**STEP01**에서는 IDCAMS를 이용해 익스포트 대상 VSAM 클러스터(`AWS.M2.CARDDEMO.EXPORT.DATA`)를 재생성한다. 먼저 기존 클러스터를 `DELETE ... PURGE`로 삭제하고(`SET MAXCC = 0`으로 클러스터 미존재 오류를 무시), 키 오프셋 28바이트·길이 4바이트(`KEYS(4 28)`)의 500바이트 고정 크기 INDEXED 클러스터를 새로 정의한다.

**STEP02**에서는 COBOL 프로그램 `CBEXPORT`(app/cbl/CBEXPORT.cbl)가 실행된다. 프로그램은 5개 입력 VSAM 파일을 각각 순차(SEQUENTIAL) 접근으로 열어 전체 레코드를 읽고, copybook `CVEXPORT`에서 정의한 단일 `EXPORT-RECORD` 구조체에 레코드 타입별로 매핑하여 익스포트 파일에 기록한다. 처리 순서는 고객(C) → 계정(A) → 크로스레퍼런스(X) → 거래(T) → 카드(D) 순이다.

각 레코드는 공통 헤더(레코드 타입 1B + 타임스탬프 26B + 시퀀스 번호 4B COMP + 브랜치 ID 4B + 리전 코드 5B = 40바이트)와 페이로드 460바이트(`EXPORT-RECORD-DATA`)로 구성된다. 페이로드는 `CVEXPORT.cpy`의 `REDEFINES` 구조로 5가지 타입이 같은 메모리를 공유하며, 실제 해석은 `EXPORT-REC-TYPE` 값(`C`/`A`/`X`/`T`/`D`)에 따라 결정된다.

처리 완료 후 각 파일별 처리 건수와 총 건수를 SYSOUT에 출력한다.

### EXPORT-RECORD 레코드 타입별 페이로드 요약

| 타입 코드 | 레코드 종류 | 주요 필드 | Java 대응 타입 예시 |
|-----------|-------------|-----------|---------------------|
| `C` | 고객 (Customer) | 고객 ID(PIC 9(9) COMP), 성명, 주소 3줄(OCCURS 3), 전화 2개(OCCURS 2), SSN, DOB, FICO 점수(PIC 9(3) COMP-3) | `CustomerDto` |
| `A` | 계정 (Account) | 계정 ID, 상태, 현잔액(S9(10)V99 COMP-3), 신용한도(S9(10)V99), 개설/만료/재발급일, 사이클 차·대변 | `AccountDto` |
| `X` | 크로스레퍼런스 (Card-Account XREF) | 카드번호 16자리, 고객 ID, 계정 ID(PIC 9(11) COMP) | `CardXrefDto` |
| `T` | 거래 (Transaction) | 거래 ID, 유형·카테고리 코드, 금액(S9(9)V99 COMP-3), 가맹점 정보, 카드번호, 원거래·처리 타임스탬프 | `TransactionDto` |
| `D` | 카드 (Card) | 카드번호, 계정 ID(PIC 9(11) COMP), CVV(PIC 9(3) COMP), 엠보싱명, 만료일, 상태 | `CardDto` |

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|---------------|------|
| STEP01 | `EXEC PGM=IDCAMS` | 익스포트 대상 VSAM 클러스터 재생성 (DELETE PURGE → DEFINE CLUSTER) |
| STEP02 | `EXEC PGM=CBEXPORT` | 5개 VSAM 파일 순차 읽기 후 멀티 레코드 타입 익스포트 파일 생성 |

---

## 의존성

### COPY (PROC/INCLUDE)
- 이 JCL 자체에는 PROC/INCLUDE 참조 없음.
- STEP02의 COBOL 프로그램 내부에서 다음 copybook을 `COPY`로 참조함:
  - `CVEXPORT` (app/cpy/CVEXPORT.cpy) — 익스포트 레코드 레이아웃. 500바이트 고정, REDEFINES로 5개 레코드 타입 공유.
  - `CVCUS01Y` (app/cpy/CVCUS01Y.cpy) — `FD CUSTOMER-INPUT` 레코드 구조 (고객 마스터).
  - `CVACT01Y` (app/cpy/CVACT01Y.cpy) — `FD ACCOUNT-INPUT` 레코드 구조 (계정 마스터).
  - `CVACT03Y` (app/cpy/CVACT03Y.cpy) — `FD XREF-INPUT` 레코드 구조 (카드-계정 크로스레퍼런스).
  - `CVTRA05Y` (app/cpy/CVTRA05Y.cpy) — `FD TRANSACTION-INPUT` 레코드 구조 (거래).
  - `CVACT02Y` (app/cpy/CVACT02Y.cpy) — `FD CARD-INPUT` 레코드 구조 (카드 마스터).

### 호출 프로그램 (EXEC PGM)
- `IDCAMS` (STEP01) — IBM 유틸리티. VSAM 클러스터 정의/삭제 수행.
- `CBEXPORT` (STEP02) — 커스텀 COBOL 배치 프로그램. `STEPLIB DD DSN=AWS.M2.CARDDEMO.LOADLIB`에서 로드.

### 데이터셋/파일/DB 테이블

| DD명 | 데이터셋 이름 | 방향 | 설명 |
|------|---------------|------|------|
| SYSPRINT | SYSOUT=* | 출력 | IDCAMS 및 CBEXPORT 시스템 메시지 |
| SYSIN | 인라인 | 입력 | IDCAMS 제어 카드 (DELETE/DEFINE 명령) |
| CUSTFILE | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | INPUT | 고객 마스터 VSAM KSDS |
| ACCTFILE | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | INPUT | 계정 마스터 VSAM KSDS |
| XREFFILE | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | INPUT | 카드-계정 크로스레퍼런스 VSAM KSDS |
| TRANSACT | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | INPUT | 거래 VSAM KSDS |
| CARDFILE | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` | INPUT | 카드 마스터 VSAM KSDS |
| EXPFILE | `AWS.M2.CARDDEMO.EXPORT.DATA` | OUTPUT | 익스포트 결과 VSAM KSDS (STEP01에서 생성) |
| STEPLIB | `AWS.M2.CARDDEMO.LOADLIB` | INPUT | CBEXPORT 로드 모듈 라이브러리 |

- DB 테이블 의존성: 없음 (기본 앱은 VSAM 전용, DB2/IMS는 선택적 모듈에서만 사용).

### 선행/후행 잡
- **선행 잡**: 별도 공식 선행 잡 정의 없음. 단, CICS가 입력 VSAM 파일을 열고 있으면 배치가 DISP=SHR로 열 때 충돌 가능성이 있으므로, 야간 배치 시퀀스(`CLOSEFIL`)를 통해 CICS 파일을 닫은 뒤 실행하는 것이 안전하다(추측).
- **후행 잡**: 별도 정의 없음. 생성된 `AWS.M2.CARDDEMO.EXPORT.DATA`를 소비하는 CBIMPORT 잡 또는 외부 마이그레이션 도구가 후행에 위치할 것으로 예상됨(추측).
- 야간 정규 배치 시퀀스(CLOSEFIL → POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL)와는 독립적인 별도 마이그레이션 용도 잡임.

---

## Java/현대화 노트

### 1. REDEFINES — 폴리모픽 레코드 구조

`CVEXPORT.cpy`의 `EXPORT-RECORD-DATA`(460바이트)에 `EXPORT-CUSTOMER-DATA`, `EXPORT-ACCOUNT-DATA`, `EXPORT-TRANSACTION-DATA`, `EXPORT-CARD-XREF-DATA`, `EXPORT-CARD-DATA` 5개 구조가 모두 `REDEFINES`로 같은 메모리 위치를 공유한다 (CVEXPORT.cpy 24, 47, 65, 84, 93행). Java에는 직접 대응 개념이 없다. 현대화 시에는 `sealed interface ExportRecordData`와 각 타입별 레코드(`record CustomerData(...)`, `record AccountData(...)` 등)를 정의하고, `EXPORT-REC-TYPE` 값으로 분기하는 팩토리 메서드로 역직렬화하는 패턴이 자연스럽다.

```java
sealed interface ExportRecordData permits CustomerData, AccountData, XrefData, TransactionData, CardData {}

record CustomerData(long custId, String firstName, ...) implements ExportRecordData {}

ExportRecordData parse(byte[] payload, char recType) {
    return switch (recType) {
        case 'C' -> parseCustomer(payload);
        case 'A' -> parseAccount(payload);
        case 'X' -> parseXref(payload);
        case 'T' -> parseTransaction(payload);
        case 'D' -> parseCard(payload);
        default  -> throw new IllegalArgumentException("Unknown record type: " + recType);
    };
}
```

### 2. EXPORT-SEQUENCE-NUM의 스토리지 타입 불일치 주의

`EXPORT-SEQUENCE-NUM`은 `PIC 9(9) COMP`(4바이트 바이너리 정수)로 선언되어 있다(CVEXPORT.cpy 16행). 반면 COBOL 프로그램에서 이 필드가 VSAM 클러스터의 레코드 키로 지정되어 있고(`RECORD KEY IS EXPORT-SEQUENCE-NUM`), STEP01의 IDCAMS DEFINE에서 `KEYS(4 28)`로 정의되었다(CBEXPORT.jcl 32행). COMP 필드는 메인프레임 빅엔디언 바이너리 4바이트로 저장되므로, Java에서 읽을 때 반드시 `ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt()`로 읽어야 한다. 키 오프셋 28은 헤더 공통 필드(EXPORT-REC-TYPE 1B + EXPORT-TIMESTAMP 26B + [패딩없음]) 이후 위치로 계산된다.

### 3. COMP-3 (packed decimal) 금액 필드

`EXP-ACCT-CURR-BAL PIC S9(10)V99 COMP-3` (CVEXPORT.cpy 50행), `EXP-ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 COMP-3` (52행), `EXP-TRAN-AMT PIC S9(9)V99 COMP-3` (71행) 등 금액 필드는 packed decimal 형식이다. 저장 바이트 수는 `ceil((자릿수+1) / 2)`이며, 소수점 2자리(`V99`)는 물리적으로 저장되지 않는다. Java에서는 반드시 `BigDecimal`을 사용해야 하며, 부동소수점(`double`) 변환은 금액 정밀도 손실을 유발하므로 금지한다.

예시: `S9(10)V99 COMP-3` → 6바이트 → Java `BigDecimal.valueOf(rawLong, 2)` (scale=2).

### 4. COMP 숫자 필드

`EXP-CUST-ID PIC 9(9) COMP` (CVEXPORT.cpy 25행), `EXP-TRAN-MERCHANT-ID PIC 9(9) COMP` (72행), `EXP-ACCT-CURR-CYC-DEBIT PIC S9(10)V99 COMP` (57행), `EXP-CARD-ACCT-ID PIC 9(11) COMP` (95행) 등은 바이너리(DISPLAY가 아닌) 정수 저장이다. IBM Enterprise COBOL에서 COMP는 하프워드(1~4자리=2B), 풀워드(5~9자리=4B), 더블워드(10~18자리=8B)로 저장된다. `PIC 9(11) COMP`는 8바이트 빅엔디언으로 읽어야 한다.

### 5. OCCURS — 배열 매핑

고객 레코드의 주소 3줄(`EXP-CUST-ADDR-LINE OCCURS 3 TIMES PIC X(50)`, CVEXPORT.cpy 30행)과 전화번호 2개(`EXP-CUST-PHONE-NUM OCCURS 2 TIMES PIC X(15)`, 35행)는 COBOL에서 1-based 인덱스(`OCCURS` 첨자는 1부터 시작)를 사용한다. CBEXPORT.cbl 286행(`MOVE CUST-ADDR-LINE-1 TO EXP-CUST-ADDR-LINE(1)`)에서도 확인된다. Java로 매핑 시 0-based 배열/리스트로 변환하되, 인덱스 오프셋에 유의한다.

### 6. EBCDIC 문자셋

VSAM 파일의 모든 PIC X 필드는 EBCDIC로 인코딩되어 있다. Java에서 파일을 바이트 스트림으로 읽을 때 `Charset.forName("IBM037")` (또는 `Cp1047`) 로 디코딩해야 올바른 문자열을 얻는다. 금액/날짜/ID 등 PIC 9 DISPLAY 필드도 EBCDIC 숫자 코드포인트(`0xF0`~`0xF9`)로 저장되므로 동일하게 처리한다.

### 7. ABEND 처리

오류 발생 시 `CALL 'CEE3ABD'`(CBEXPORT.cbl 579행)로 LE(Language Environment) abend를 호출한다. JCL 레벨에서 별도 `IF ABEND` 조건 스텝이 없으므로 비정상 종료 시 잡 자체가 abend로 중단된다. Java/Spring Batch 전환 시에는 `@OnReadError`/`@OnWriteError` 리스너 또는 `SkipPolicy`로 대체하고, 치명 오류는 `RuntimeException`을 throw하여 잡을 실패 처리한다.

### 8. Spring Batch 마이그레이션 패턴 제안

CBEXPORT의 구조는 순수한 단방향 읽기-변환-쓰기이므로 Spring Batch의 청크 지향 스텝과 직접 대응된다.

```
Job: exportJob
  Step 1: defineVsamCluster (Tasklet — AWS SDK VSAM 또는 대상 스토리지 생성)
  Step 2: exportCustomers   (FlatFileItemReader<CustomerRecord> → ExportItemProcessor → ExportFileItemWriter)
  Step 3: exportAccounts
  Step 4: exportXrefs
  Step 5: exportTransactions
  Step 6: exportCards
```

5개 파일이 서로 조인 없이 독립적으로 순차 처리되므로, 각 엔티티 타입을 별도 스텝으로 분리하거나, 파티셔닝/멀티스레드 스텝으로 병렬화할 수 있다. 출력 측에서는 타입 코드(`recType`) 기반 폴리모픽 직렬화 대신 JSON Lines 또는 Parquet 같은 현대적 포맷으로 대체하는 것을 권장한다.
