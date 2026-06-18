# CVEXPORT — 카드데모 멀티 레코드 익스포트 레이아웃

- **유형**: Copybook
- **한 줄 요약**: 브랜치 이전(branch migration)용 순차 익스포트 파일의 500바이트 고정 레코드를 정의하는 통합 스키마. 단일 레코드 타입 식별자와 REDEFINES를 이용해 고객·계정·거래·카드교차참조·카드 5종 레코드를 하나의 물리 레이아웃으로 수용한다.

---

## 기능 설명

CVEXPORT.cpy는 CardDemo 시스템에서 브랜치(지점) 간 데이터 이전을 위해 사용하는 순차 파일(QSAM, RECFM=FB, LRECL=500)의 레코드 구조를 정의한다. 헤더 영역(40바이트)과 데이터 페이로드 영역(460바이트, `EXPORT-RECORD-DATA`)으로 구분되며, 페이로드 영역에 5개의 `REDEFINES` 그룹을 선언해 레코드 유형에 따라 다른 필드 집합을 같은 물리 메모리 위치에서 해석한다.

Java로 비유하면, `EXPORT-RECORD` 는 다음 봉인 클래스 계층과 동일한 역할을 한다:

```java
// 봉인 추상 클래스 — 헤더 공통 필드 포함
public sealed class ExportRecord
    permits CustomerExportRecord, AccountExportRecord,
            TransactionExportRecord, CardXrefExportRecord, CardExportRecord {

    char   recType;        // EXPORT-REC-TYPE  — '1'~'5' 중 하나
    String timestamp;      // EXPORT-TIMESTAMP (ISO-8601 형태 26자)
    int    sequenceNum;    // EXPORT-SEQUENCE-NUM (COMP = int)
    String branchId;       // EXPORT-BRANCH-ID
    String regionCode;     // EXPORT-REGION-CODE
    byte[] recordData;     // EXPORT-RECORD-DATA 460바이트 — 하위 클래스가 재해석
}
```

REDEFINES는 상속이 아닌 **공용체(union)** 이므로, 런타임에 동일 460바이트를 `EXPORT-CUSTOMER-DATA` 또는 `EXPORT-ACCOUNT-DATA` 등으로 해석하는 결정은 프로그램(CBEXPORT/CBIMPORT)이 `EXPORT-REC-TYPE` 값을 검사한 후 해당 그룹을 참조함으로써 이루어진다.

---

## 필드 레이아웃

### 공통 헤더 (오프셋 0–39, 40바이트)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `EXPORT-REC-TYPE` | `PIC X(1)` | 1 | 레코드 종류 식별자. 프로그램이 EVALUATE로 분기하는 키 |
| `EXPORT-TIMESTAMP` | `PIC X(26)` | 26 | ISO-8601 형태 타임스탬프 전체 문자열 |
| `EXPORT-DATE` *(REDEFINES)* | `PIC X(10)` | 10 | EXPORT-TIMESTAMP 앞 10자 — 날짜 부분 (`YYYY-MM-DD`) |
| `EXPORT-DATE-TIME-SEP` *(REDEFINES)* | `PIC X(1)` | 1 | 날짜·시각 구분자 (보통 `T` 또는 공백) |
| `EXPORT-TIME` *(REDEFINES)* | `PIC X(15)` | 15 | EXPORT-TIMESTAMP 뒤 15자 — 시각+시간대 부분 |
| `EXPORT-SEQUENCE-NUM` | `PIC 9(9) COMP` | 4 | 레코드 순번. COMP = 32비트 2진 정수 → Java `int` |
| `EXPORT-BRANCH-ID` | `PIC X(4)` | 4 | 발원 브랜치 ID |
| `EXPORT-REGION-CODE` | `PIC X(5)` | 5 | 지역 코드 |

> **REDEFINES 주의**: `EXPORT-TIMESTAMP-R`은 `EXPORT-TIMESTAMP`와 **같은 26바이트**를 공유한다. 두 필드가 동시에 유효한 값을 가지는 것이 아니라, 같은 저장소를 다른 이름으로 접근하는 것이다. Java에는 직접 대응 구문이 없다. 현대화 시 `LocalDateTime.parse()` 결과를 별도 필드로 분리하면 된다.

---

### 페이로드 영역 (오프셋 40–499, 460바이트) — REDEFINES 5종

모든 `05 EXPORT-xxx-DATA` 그룹은 `EXPORT-RECORD-DATA`(460바이트)를 REDEFINES한다.

#### 레코드 유형 C — 고객 (`EXPORT-CUSTOMER-DATA`, 460바이트)

출처: `CVCUS01Y.cpy` / CUSTDATA VSAM(LRECL=500)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `EXP-CUST-ID` | `PIC 9(09) COMP` | 4 | 고객 ID. COMP = 32비트 → Java `int` |
| `EXP-CUST-FIRST-NAME` | `PIC X(25)` | 25 | 이름 |
| `EXP-CUST-MIDDLE-NAME` | `PIC X(25)` | 25 | 중간 이름 |
| `EXP-CUST-LAST-NAME` | `PIC X(25)` | 25 | 성 |
| `EXP-CUST-ADDR-LINES` | `OCCURS 3 TIMES` | 150 | 주소 3행. 각 행 `PIC X(50)` → Java `String[3]` 또는 `List<String>` |
| `EXP-CUST-ADDR-STATE-CD` | `PIC X(02)` | 2 | 주(State) 코드 |
| `EXP-CUST-ADDR-COUNTRY-CD` | `PIC X(03)` | 3 | 국가 코드 |
| `EXP-CUST-ADDR-ZIP` | `PIC X(10)` | 10 | 우편번호 |
| `EXP-CUST-PHONE-NUMS` | `OCCURS 2 TIMES` | 30 | 전화번호 2개. 각 `PIC X(15)` → Java `String[2]` |
| `EXP-CUST-SSN` | `PIC 9(09)` | 9 | 사회보장번호. **DISPLAY** (COMP 아님) — 9자리 숫자 문자열로 저장 |
| `EXP-CUST-GOVT-ISSUED-ID` | `PIC X(20)` | 20 | 정부 발행 신분증 번호 |
| `EXP-CUST-DOB-YYYY-MM-DD` | `PIC X(10)` | 10 | 생년월일 (`YYYY-MM-DD`) |
| `EXP-CUST-EFT-ACCOUNT-ID` | `PIC X(10)` | 10 | EFT(전자자금이체) 계좌 ID |
| `EXP-CUST-PRI-CARD-HOLDER-IND` | `PIC X(01)` | 1 | 주 카드 소지자 여부 (`Y`/`N`) |
| `EXP-CUST-FICO-CREDIT-SCORE` | `PIC 9(03) COMP-3` | 2 | FICO 신용점수. COMP-3(팩 십진수) — 3자리이므로 2바이트. Java `int` (0–999) |
| `FILLER` | `PIC X(134)` | 134 | 미사용 패딩 |

> **COMP-3 해설**: `PIC 9(03) COMP-3`은 2바이트에 3개 십진 니블(nibble)로 저장된다. 각 니블이 십진 한 자리를 담으며 마지막 니블은 부호(`C`=양수, `F`=부호없음). IBM Data Studio 또는 Java의 `BigDecimal`/커스텀 언팩 루틴으로 변환 필요.

---

#### 레코드 유형 A — 계정 (`EXPORT-ACCOUNT-DATA`, 460바이트)

출처: `CVACT01Y.cpy` / ACCTDATA VSAM(LRECL=300)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `EXP-ACCT-ID` | `PIC 9(11)` | 11 | 계정 ID. **DISPLAY** (숫자 문자열) — 메모리 11바이트. Java `String` 또는 `long` |
| `EXP-ACCT-ACTIVE-STATUS` | `PIC X(01)` | 1 | 계정 활성 상태 (`Y`/`N`) |
| `EXP-ACCT-CURR-BAL` | `PIC S9(10)V99 COMP-3` | 7 | 현재 잔액. 부호 있는 팩 십진수, 소수점 2자리. Java `BigDecimal`. (12자리 → 7바이트) |
| `EXP-ACCT-CREDIT-LIMIT` | `PIC S9(10)V99` | 13 | 신용 한도. **DISPLAY** 부호포함(SIGN LEADING/TRAILING 기본). 부호 1자 + 숫자 12자 |
| `EXP-ACCT-CASH-CREDIT-LIMIT` | `PIC S9(10)V99 COMP-3` | 7 | 현금서비스 한도. 팩 십진수 → Java `BigDecimal` |
| `EXP-ACCT-OPEN-DATE` | `PIC X(10)` | 10 | 계좌 개설일 (`YYYY-MM-DD`) |
| `EXP-ACCT-EXPIRAION-DATE` | `PIC X(10)` | 10 | 만료일 (오탈자 "EXPIRAION" — 원본 그대로) |
| `EXP-ACCT-REISSUE-DATE` | `PIC X(10)` | 10 | 재발행일 |
| `EXP-ACCT-CURR-CYC-CREDIT` | `PIC S9(10)V99` | 13 | 당기 사이클 신용 합계. DISPLAY |
| `EXP-ACCT-CURR-CYC-DEBIT` | `PIC S9(10)V99 COMP` | 4 | 당기 사이클 차변 합계. **COMP = 2진 정수** — 그러나 소수점(`V99`) 선언이 있어 주의 필요. 실질 정수값을 100으로 나눠야 원화 단위 금액이 됨 (추측: IBM COMP는 소수점 선언을 무시하고 정수 저장, 프로그램이 스케일 책임) |
| `EXP-ACCT-ADDR-ZIP` | `PIC X(10)` | 10 | 주소 우편번호 |
| `EXP-ACCT-GROUP-ID` | `PIC X(10)` | 10 | 계정 그룹 ID |
| `FILLER` | `PIC X(352)` | 352 | 미사용 패딩 |

> **`PIC S9(10)V99 COMP` 경고**: `COMP`(이진 정수)에 `V99`(묵시적 소수점) 조합은 비표준적이다. IBM Enterprise COBOL에서는 이진 필드에 소수점 선언이 허용되지만, 실제 저장 바이트는 정수로 처리되고 스케일(`V99`)은 논리적 약속에 불과하다. Java 변환 시 반드시 정수값을 `new BigDecimal(val).movePointLeft(2)`로 처리해야 한다.

---

#### 레코드 유형 T — 거래 (`EXPORT-TRANSACTION-DATA`, 460바이트)

출처: `CVTRA05Y.cpy` / TRANSACT VSAM(LRECL=350)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `EXP-TRAN-ID` | `PIC X(16)` | 16 | 거래 ID |
| `EXP-TRAN-TYPE-CD` | `PIC X(02)` | 2 | 거래 유형 코드 (참조: CVTRA03Y) |
| `EXP-TRAN-CAT-CD` | `PIC 9(04)` | 4 | 거래 카테고리 코드. DISPLAY |
| `EXP-TRAN-SOURCE` | `PIC X(10)` | 10 | 거래 발원 채널 |
| `EXP-TRAN-DESC` | `PIC X(100)` | 100 | 거래 설명 |
| `EXP-TRAN-AMT` | `PIC S9(09)V99 COMP-3` | 6 | 거래 금액. 부호 있는 팩 십진수, 소수점 2자리 → Java `BigDecimal` |
| `EXP-TRAN-MERCHANT-ID` | `PIC 9(09) COMP` | 4 | 가맹점 ID. COMP → Java `int` |
| `EXP-TRAN-MERCHANT-NAME` | `PIC X(50)` | 50 | 가맹점 이름 |
| `EXP-TRAN-MERCHANT-CITY` | `PIC X(50)` | 50 | 가맹점 소재 도시 |
| `EXP-TRAN-MERCHANT-ZIP` | `PIC X(10)` | 10 | 가맹점 우편번호 |
| `EXP-TRAN-CARD-NUM` | `PIC X(16)` | 16 | 카드 번호 |
| `EXP-TRAN-ORIG-TS` | `PIC X(26)` | 26 | 거래 원래 발생 타임스탬프 |
| `EXP-TRAN-PROC-TS` | `PIC X(26)` | 26 | 거래 처리 완료 타임스탬프 |
| `FILLER` | `PIC X(140)` | 140 | 미사용 패딩 |

---

#### 레코드 유형 X — 카드교차참조 (`EXPORT-CARD-XREF-DATA`, 460바이트)

출처: `CVACT03Y.cpy` / CARDXREF VSAM(LRECL=50)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `EXP-XREF-CARD-NUM` | `PIC X(16)` | 16 | 카드 번호 (KSDS 키) |
| `EXP-XREF-CUST-ID` | `PIC 9(09)` | 9 | 고객 ID. DISPLAY |
| `EXP-XREF-ACCT-ID` | `PIC 9(11) COMP` | 4 | 계정 ID. **COMP** — DISPLAY(11바이트)와 달리 4바이트 이진 저장. Java `int` |
| `FILLER` | `PIC X(427)` | 427 | 미사용 패딩 (CARDXREF 원본 50바이트 대비 여유 매우 큼) |

---

#### 레코드 유형 K — 카드 (`EXPORT-CARD-DATA`, 460바이트)

출처: `CVACT02Y.cpy` / CARDDATA VSAM(LRECL=150)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `EXP-CARD-NUM` | `PIC X(16)` | 16 | 카드 번호 |
| `EXP-CARD-ACCT-ID` | `PIC 9(11) COMP` | 4 | 계정 ID. COMP → Java `int` |
| `EXP-CARD-CVV-CD` | `PIC 9(03) COMP` | 2 | CVV 코드. COMP 3자리 → Java `short` (0–999). **보안 주의: 마이그레이션 시 PCI-DSS 규정으로 평문 저장 불가** |
| `EXP-CARD-EMBOSSED-NAME` | `PIC X(50)` | 50 | 카드 양각 이름 |
| `EXP-CARD-EXPIRAION-DATE` | `PIC X(10)` | 10 | 카드 만료일 (오탈자 "EXPIRAION" — 원본 그대로) |
| `EXP-CARD-ACTIVE-STATUS` | `PIC X(01)` | 1 | 카드 활성 상태 (`Y`/`N`) |
| `FILLER` | `PIC X(373)` | 373 | 미사용 패딩 |

---

### 전체 레코드 구조 요약 (500바이트)

```
오프셋   길이  필드
  0        1   EXPORT-REC-TYPE
  1       26   EXPORT-TIMESTAMP
 27        4   EXPORT-SEQUENCE-NUM (COMP)
 31        4   EXPORT-BRANCH-ID
 35        5   EXPORT-REGION-CODE
 40      460   EXPORT-RECORD-DATA  ← 아래 5개 REDEFINES 그룹 공유
                 └─ EXPORT-CUSTOMER-DATA    (레코드 유형에 따라 택일)
                 └─ EXPORT-ACCOUNT-DATA
                 └─ EXPORT-TRANSACTION-DATA
                 └─ EXPORT-CARD-XREF-DATA
                 └─ EXPORT-CARD-DATA
 총계    500바이트
```

---

## 의존성

- **COPY (중첩 카피북)**: 없음 (이 copybook 자체가 COPY 지시문 없음. 단, 주석으로 5개 원본 copybook 출처를 명시: `CVCUS01Y.cpy`, `CVACT01Y.cpy`, `CVTRA05Y.cpy`, `CVACT03Y.cpy`, `CVACT02Y.cpy`)
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: 이 copybook을 COPY하는 프로그램(CBEXPORT/CBIMPORT)이 참조하는 순차 익스포트 파일. DD명은 프로그램 JCL 및 소스에서 확인 필요
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 1. REDEFINES → 봉인 클래스(Sealed Class) + 패턴 매칭

REDEFINES 구조는 Java 17+ `sealed interface` + `switch` 패턴 매칭으로 자연스럽게 표현된다:

```java
public sealed interface ExportPayload
    permits CustomerPayload, AccountPayload,
            TransactionPayload, CardXrefPayload, CardPayload {}

ExportPayload payload = switch (record.getRecType()) {
    case 'C' -> parseCustomer(record.getRecordData());
    case 'A' -> parseAccount(record.getRecordData());
    case 'T' -> parseTransaction(record.getRecordData());
    case 'X' -> parseCardXref(record.getRecordData());
    case 'K' -> parseCard(record.getRecordData());
    default  -> throw new IllegalArgumentException("Unknown rec type: " + record.getRecType());
};
```

> `EXPORT-REC-TYPE`의 실제 값 코드(`'C'`, `'A'` 등)는 이 copybook에 정의되어 있지 않다. CBEXPORT/CBIMPORT 소스에서 `88` 레벨 조건명 또는 EVALUATE 문을 확인해야 한다 **(추측)**.

### 2. COMP-3 팩 십진수 → BigDecimal

금융 금액 필드(`EXP-ACCT-CURR-BAL`, `EXP-ACCT-CASH-CREDIT-LIMIT`, `EXP-TRAN-AMT` 등)는 COMP-3(BCD 팩)으로 저장된다. Java 마이그레이션 시 절대 `float`/`double`을 쓰면 안 되며, `BigDecimal`을 사용해야 한다:

```java
// COMP-3 언팩 예시 (S9(10)V99 = 12자리 = 7바이트)
public static BigDecimal unpackComp3(byte[] bytes, int scale) {
    StringBuilder digits = new StringBuilder();
    for (int i = 0; i < bytes.length - 1; i++) {
        digits.append((bytes[i] >> 4) & 0x0F);
        digits.append(bytes[i] & 0x0F);
    }
    // 마지막 바이트: 상위 니블 = 마지막 숫자, 하위 니블 = 부호
    byte last = bytes[bytes.length - 1];
    digits.append((last >> 4) & 0x0F);
    boolean negative = (last & 0x0F) == 0x0D; // D = 음수
    BigDecimal result = new BigDecimal(digits.toString()).movePointLeft(scale);
    return negative ? result.negate() : result;
}
```

### 3. OCCURS → Java 컬렉션

`EXP-CUST-ADDR-LINES OCCURS 3 TIMES`와 `EXP-CUST-PHONE-NUMS OCCURS 2 TIMES`는 1-based 인덱스(`(1)`, `(2)`, `(3)`)로 접근한다. Java 마이그레이션 시 0-based `List<String>` 또는 배열로 전환하되, **인덱스 오프셋(1 → 0)** 에 유의해야 한다.

### 4. DISPLAY vs COMP 혼용 — 직렬화 일관성 문제

이 copybook에는 같은 의미의 ID 필드가 DISPLAY(`EXP-XREF-CUST-ID PIC 9(09)` = 9바이트)와 COMP(`EXP-CUST-ID PIC 9(09) COMP` = 4바이트)로 혼용된다. 역직렬화 시 필드별로 인코딩을 다르게 처리해야 한다. 특히 `EXP-ACCT-ID PIC 9(11)` (DISPLAY, 11바이트)와 `EXP-XREF-ACCT-ID PIC 9(11) COMP` (4바이트) 는 같은 논리적 값이지만 저장 형식이 다르므로 **비교 전 정규화**가 필수다.

### 5. EBCDIC → UTF-8 변환

메인프레임 순차 파일은 EBCDIC 인코딩이다. Java `new String(bytes, Charset.forName("IBM037"))` 또는 AWS Mainframe Modernization의 자동 변환 기능을 사용한다. 숫자 DISPLAY 필드도 EBCDIC 숫자 코드로 저장된다.

### 6. 오탈자 `EXPIRAION` 주의

`EXP-ACCT-EXPIRAION-DATE`와 `EXP-CARD-EXPIRAION-DATE` 모두 "EXPIRATION"의 오탈자다 (소스 라인 54, 98). Java 클래스 필드명은 `expirationDate`로 정정하되, COBOL 소스와의 매핑 주석을 남겨야 한다.

### 7. CVV 평문 저장 — PCI-DSS 위반 위험

`EXP-CARD-CVV-CD`가 평문으로 익스포트 파일에 포함된다. AWS로 마이그레이션 시 해당 필드는 AWS Secrets Manager 또는 KMS 암호화로 대체하거나, 익스포트 시 마스킹해야 한다.

### 8. 레코드 유형별 FILLER 크기 불균형

각 레코드 유형의 실제 데이터 크기가 460바이트에 비해 훨씬 작다(카드교차참조: 29바이트 실데이터 + 427바이트 FILLER). 향후 확장을 위한 예약 공간이지만, 배치 처리 I/O 성능을 위해 레코드 길이를 유형별로 가변화하는 것을 고려할 수 있다.

---

*소스: `/Users/dongryunjeong/Documents/development/aws-mainframe-modernization-carddemo/app/cpy/CVEXPORT.cpy`*
*버전: CardDemo_v2.0-44-gb6e9c27-254 (2025-10-16)*
