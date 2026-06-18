# CBTRN01C — 일일 거래 파일 포스팅 (검증 단계)

- **유형**: 배치 COBOL (QSAM 순차 읽기 + VSAM KSDS 랜덤 룩업)
- **한 줄 요약**: 일일 거래 파일(DALYTRAN)을 순차 읽으며 카드번호→계정 교차참조(XREF)와 계정(ACCOUNT) 파일을 랜덤 조회해 거래 유효성을 검증한다. 현재 구현은 검증·조회 출력에 머물고 실제 거래 기록을 TRANFILE에 쓰지는 않는다(미완성).

---

## 기능 설명

CBTRN01C는 야간 배치 파이프라인의 **첫 번째 단계**에 해당한다(라인 5 주석: "Post the records from daily transaction file").  
프로그램은 6개 파일을 열고, DALYTRAN 파일을 레코드 단위로 순차 읽으며 아래 두 가지 검증 조회를 수행한다.

1. **XREF 조회** (`2000-LOOKUP-XREF`): 거래 레코드의 카드번호(`DALYTRAN-CARD-NUM`)로 XREF 파일을 랜덤 키 조회해 계정ID(`XREF-ACCT-ID`)와 고객ID(`XREF-CUST-ID`)를 확인한다.
2. **계정 조회** (`3000-READ-ACCOUNT`): XREF에서 얻은 계정ID로 ACCOUNT 파일을 랜덤 키 조회해 계정 레코드를 확인한다.

조회 실패 시 DISPLAY 메시지를 출력하고 해당 거래를 스킵한다. 파일 I/O 오류(STATUS ≠ '00'/'10') 시에는 `Z-ABEND-PROGRAM`을 통해 `CEE3ABD`를 호출하여 ABEND(비정상 종료)한다.

> **주목**: TRANSACT-FILE(`TRANFILE`)과 CUSTOMER-FILE(`CUSTFILE`), CARD-FILE(`CARDFILE`)은 열고 닫지만 메인 루프에서 실제로 읽거나 쓰는 코드가 없다(라인 343–359, 307–323, 271–287). 이는 **미완성 스텁**이거나 향후 확장을 위한 예약이다.

---

## 입력 / 출력

- **입력**:
  - `DALYTRAN` (DD명): 일일 거래 순차 파일(QSAM). 레코드 길이 350바이트. `DALYTRAN-RECORD`(CVTRA06Y) 구조.
  - `XREFFILE` (DD명): 카드↔계정 교차참조 VSAM KSDS. 키=`FD-XREF-CARD-NUM` PIC X(16). 레코드 길이 50바이트.
  - `ACCTFILE` (DD명): 계정 마스터 VSAM KSDS. 키=`FD-ACCT-ID` PIC 9(11). 레코드 길이 300바이트.
  - `CUSTFILE` (DD명): 고객 마스터 VSAM KSDS. 키=`FD-CUST-ID` PIC 9(09). 레코드 길이 500바이트. (열기만 하고 읽지 않음)
  - `CARDFILE` (DD명): 카드 마스터 VSAM KSDS. 키=`FD-CARD-NUM` PIC X(16). 레코드 길이 150바이트. (열기만 하고 읽지 않음)
  - `TRANFILE` (DD명): 거래 마스터 VSAM KSDS. 키=`FD-TRANS-ID` PIC X(16). 레코드 길이 350바이트. (열기만 하고 읽지 않음)

- **출력**:
  - SYSOUT(DISPLAY): 처리 시작/종료 메시지, 각 거래 레코드 덤프, XREF/ACCOUNT 조회 결과, 오류/스킵 메시지.
  - 파일 출력 없음 (TRANFILE WRITE 로직 미구현).

---

## 의존성

- **COPY (카피북)**:

  | 카피북 | DD명/파일 | 주요 01레벨명 | 내용 |
  |---|---|---|---|
  | `CVTRA06Y` | DALYTRAN | `DALYTRAN-RECORD` | 일일 거래 레코드 (350바이트) |
  | `CVTRA05Y` | TRANFILE | `TRAN-RECORD` | 거래 마스터 레코드 (350바이트) |
  | `CVACT03Y` | XREFFILE | `CARD-XREF-RECORD` | 카드-계정 교차참조 (50바이트) |
  | `CVACT02Y` | CARDFILE | `CARD-RECORD` | 카드 엔티티 (150바이트) |
  | `CVACT01Y` | ACCTFILE | `ACCOUNT-RECORD` | 계정 엔티티 (300바이트) |
  | `CVCUS01Y` | CUSTFILE | `CUSTOMER-RECORD` | 고객 엔티티 (500바이트) |

- **호출 프로그램 (CALL)**:
  - `CEE3ABD` (라인 473): IBM LE(Language Environment) 표준 ABEND 루틴. `ABCODE`(999)와 `TIMING`(0)을 인자로 전달해 강제 비정상 종료를 일으킨다.

- **데이터셋/파일/DB 테이블**:

  | SELECT 이름 | ASSIGN TO (DD명) | 조직 | 접근 모드 |
  |---|---|---|---|
  | `DALYTRAN-FILE` | `DALYTRAN` | SEQUENTIAL (QSAM) | SEQUENTIAL |
  | `CUSTOMER-FILE` | `CUSTFILE` | INDEXED (VSAM KSDS) | RANDOM |
  | `XREF-FILE` | `XREFFILE` | INDEXED (VSAM KSDS) | RANDOM |
  | `CARD-FILE` | `CARDFILE` | INDEXED (VSAM KSDS) | RANDOM |
  | `ACCOUNT-FILE` | `ACCTFILE` | INDEXED (VSAM KSDS) | RANDOM |
  | `TRANSACT-FILE` | `TRANFILE` | INDEXED (VSAM KSDS) | RANDOM |

- **트랜잭션 ID 또는 EXEC PGM**: JCL `EXEC PGM=CBTRN01C`. CICS 트랜잭션 없음(순수 배치).

---

## 핵심 로직 흐름

```
MAIN-PARA
│
├─ 0000-DALYTRAN-OPEN   DALYTRAN-FILE OPEN INPUT  (QSAM)
├─ 0100-CUSTFILE-OPEN   CUSTOMER-FILE OPEN INPUT  (VSAM)
├─ 0200-XREFFILE-OPEN   XREF-FILE     OPEN INPUT  (VSAM)
├─ 0300-CARDFILE-OPEN   CARD-FILE     OPEN INPUT  (VSAM)
├─ 0400-ACCTFILE-OPEN   ACCOUNT-FILE  OPEN INPUT  (VSAM)
├─ 0500-TRANFILE-OPEN   TRANSACT-FILE OPEN INPUT  (VSAM)  ← 미사용
│
├─ PERFORM UNTIL END-OF-DAILY-TRANS-FILE = 'Y'
│   │
│   ├─ 1000-DALYTRAN-GET-NEXT
│   │     READ DALYTRAN-FILE INTO DALYTRAN-RECORD
│   │     STATUS '00' → APPL-AOK(0)
│   │     STATUS '10' → APPL-EOF(16) → END-OF-DAILY-TRANS-FILE = 'Y'
│   │     그 외      → ABEND
│   │
│   ├─ (EOF 아닌 경우) DISPLAY DALYTRAN-RECORD 내용 출력
│   │
│   ├─ WS-XREF-READ-STATUS = 0
│   │   MOVE DALYTRAN-CARD-NUM TO XREF-CARD-NUM
│   ├─ 2000-LOOKUP-XREF
│   │     READ XREF-FILE KEY IS FD-XREF-CARD-NUM
│   │     INVALID KEY   → WS-XREF-READ-STATUS = 4, DISPLAY 오류
│   │     NOT INVALID   → DISPLAY XREF 내용
│   │
│   └─ (XREF 성공) WS-ACCT-READ-STATUS = 0
│       MOVE XREF-ACCT-ID TO ACCT-ID
│   ├─ 3000-READ-ACCOUNT
│   │     READ ACCOUNT-FILE KEY IS FD-ACCT-ID
│   │     INVALID KEY   → WS-ACCT-READ-STATUS = 4, DISPLAY 오류
│   │     NOT INVALID   → DISPLAY 계정 조회 성공
│   │
│   └─ (XREF 실패) DISPLAY 카드번호 검증 불가 메시지 + 거래ID 스킵
│
├─ 9000-DALYTRAN-CLOSE ~ 9500-TRANFILE-CLOSE  (6개 파일 닫기)
│
└─ GOBACK
```

### 오류 처리 서브루틴

- **`Z-DISPLAY-IO-STATUS`** (라인 476–489): FILE STATUS 2바이트를 해석해 DISPLAY한다.  
  - STATUS-1이 `'9'`이거나 비숫자인 경우: 물리적/드라이버 오류. `IO-STAT2`를 `TWO-BYTES-BINARY`(REDEFINES)를 통해 숫자로 변환하여 4자리 포맷으로 출력한다.  
  - 그 외: STATUS 2바이트를 `IO-STATUS-04`의 3번째 자리부터 복사해 출력한다.
- **`Z-ABEND-PROGRAM`** (라인 469–473): `CEE3ABD(999, 0)` 호출로 프로그램을 ABEND 시킨다. ABCODE 999는 임의 사용자 코드이다.

### REDEFINES 활용 (라인 133–136)

```cobol
01  TWO-BYTES-BINARY   PIC 9(4) BINARY.
01  TWO-BYTES-ALPHA    REDEFINES TWO-BYTES-BINARY.
    05  TWO-BYTES-LEFT   PIC X.
    05  TWO-BYTES-RIGHT  PIC X.
```

`Z-DISPLAY-IO-STATUS`에서 비숫자 STATUS-2 바이트를 이진 정수로 재해석할 때 사용한다. `TWO-BYTES-RIGHT`에 바이트를 넣으면 `TWO-BYTES-BINARY`로 그 이진값을 읽을 수 있다.

---

## Java/현대화 노트

### 1. 전체 구조 대응

```java
// CBTRN01C 전체 흐름의 Java 유사 코드
public class DailyTransactionPostingJob {

    // WORKING-STORAGE SECTION의 플래그/상태 변수 → 인스턴스 필드
    private boolean endOfDailyTransFile = false;  // END-OF-DAILY-TRANS-FILE
    private int wsXrefReadStatus = 0;             // WS-XREF-READ-STATUS
    private int wsAcctReadStatus = 0;             // WS-ACCT-READ-STATUS

    public void run() {
        openAllFiles();                           // 0000~0500-OPEN
        try {
            while (!endOfDailyTransFile) {        // PERFORM UNTIL
                DailyTranRecord rec = readNext(); // 1000-DALYTRAN-GET-NEXT
                if (rec == null) break;
                CardXrefRecord xref = lookupXref(rec.getCardNum()); // 2000
                if (xref != null) {
                    readAccount(xref.getAcctId());  // 3000
                }
            }
        } finally {
            closeAllFiles();                      // 9000~9500-CLOSE
        }
    }
}
```

### 2. 주요 PIC 필드 → Java 타입 매핑

| COBOL 필드 | PIC | Java 타입 | 비고 |
|---|---|---|---|
| `DALYTRAN-ID` | `X(16)` | `String` (len=16) | 고정길이, 우측 공백 패딩 |
| `DALYTRAN-TYPE-CD` | `X(02)` | `String` (len=2) | 거래 유형 코드 |
| `DALYTRAN-CAT-CD` | `9(04)` | `int` | 거래 카테고리 코드 |
| `DALYTRAN-AMT` | `S9(09)V99` | `BigDecimal` | 부호 있는 소수점 2자리. `float`/`double` 사용 금지 |
| `DALYTRAN-MERCHANT-ID` | `9(09)` | `long` | 양수 정수 |
| `DALYTRAN-ORIG-TS` | `X(26)` | `String` → `LocalDateTime` 파싱 | 타임스탬프 문자열 |
| `XREF-CUST-ID` | `9(09)` | `long` | 고객 ID |
| `XREF-ACCT-ID` | `9(11)` | `long` | 계정 ID (11자리) |
| `ACCT-CURR-BAL` | `S9(10)V99` | `BigDecimal` | 잔액. `DISPLAY` 형식(기본), 정밀도 손실 주의 |
| `CUST-SSN` | `9(09)` | `String` | PII. `long`보다 `String` 권장 |

### 3. APPL-RESULT 레벨-88 조건명 → Java enum

```cobol
01  APPL-RESULT    PIC S9(9) COMP.
    88  APPL-AOK   VALUE 0.
    88  APPL-EOF   VALUE 16.
```

```java
// Java 대응
enum AppResult { AOK(0), EOF(16), ERROR(12), INITIAL(8);
    final int code;
    AppResult(int c) { this.code = c; }
}
```

레벨-88 조건명은 COBOL에서 `IF APPL-AOK`처럼 boolean 서술자로 쓰인다. Java에는 직접 대응이 없으므로 `enum` 또는 상수 비교로 치환한다.

### 4. FILE STATUS 2바이트 해석

| STATUS | 의미 | Java 대응 |
|---|---|---|
| `'00'` | 정상 | 정상 반환 |
| `'10'` | EOF | `Optional.empty()` 또는 `null` 반환 |
| `'23'` | VSAM 키 미발견 (INVALID KEY) | `Optional.empty()` |
| `'9x'` | 물리적/드라이버 오류 | `IOException` throw |
| 그 외 | 논리 오류 | 커스텀 예외 |

### 5. GOBACK vs STOP RUN

- `GOBACK` (라인 197): 호출자(JCL/런타임)에게 제어를 반환한다. 서브프로그램으로 CALL됐을 경우 호출자로 복귀하고, 메인 프로그램이면 `STOP RUN`과 동일하게 동작한다. Java의 `return` (void main에서) 또는 `System.exit(0)`에 대응한다.

### 6. CEE3ABD ABEND → Java 예외 처리

```cobol
CALL 'CEE3ABD' USING ABCODE, TIMING.
```

Java 현대화 시 이를 그대로 번역하지 말고, 적절한 예외(`RuntimeException` 또는 커스텀 `BatchAbortException`)를 throw하고 Spring Batch의 `Job` 실패 처리 또는 `ExitStatus.FAILED`로 매핑한다.

### 7. 미완성 스텁 주의사항

CBTRN01C는 파일을 여섯 개 열지만 실제로 읽거나 쓰는 것은 DALYTRAN·XREF·ACCOUNT 세 개뿐이다. **CUSTFILE, CARDFILE, TRANFILE은 열기·닫기만 한다.** Java 마이그레이션 시:

- 이 세 파일의 처리 로직은 CBTRN02C 또는 후속 단계 프로그램에 있을 가능성이 높다(추측).
- TRANFILE WRITE 로직이 없으므로 "포스팅" 기능은 미구현 상태이다. 완전한 포스팅 처리를 위해 CBTRN02C를 함께 분석해야 한다.

### 8. REDEFINES → 메모리 오버레이 주의

`TWO-BYTES-BINARY` / `TWO-BYTES-ALPHA REDEFINES`는 동일 2바이트 메모리를 정수와 문자 두 가지 뷰로 해석하는 C의 `union`과 같다. Java에는 직접 대응이 없다. Java에서 동일한 동작을 구현하려면 `ByteBuffer`를 사용한다:

```java
byte[] statusByte = { io_stat2 };
ByteBuffer buf = ByteBuffer.allocate(2).put((byte)0).put(statusByte[0]);
buf.rewind();
int numericValue = buf.getShort() & 0xFFFF;
```

### 9. 배치 시퀀스 내 위치

메모리 파일 `batch_and_optional_modules.md`에 따르면 CB* 배치 프로그램은 야간 배치 시퀀스로 동작한다. CBTRN01C는 DALYTRAN 파일을 소비하는 첫 단계이며, CBTRN02C가 실제 거래 레코드 처리(TRANFILE WRITE)를 담당하는 것으로 보인다. Spring Batch 현대화 시 두 프로그램을 하나의 `Job`의 연속 `Step`으로 구성하는 것이 자연스럽다.
