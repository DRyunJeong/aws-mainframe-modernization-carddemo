# CBACT02C — 카드 파일 순차 읽기 및 출력 배치

- **유형**: 배치 COBOL
- **한 줄 요약**: VSAM KSDS인 카드 마스터 파일(CARDFILE)을 순차로 읽어 전체 레코드를 SYSOUT에 출력하는 단순 진단/덤프용 배치 프로그램.

---

## 기능 설명

CBACT02C는 카드 마스터 VSAM 파일을 처음부터 끝까지 순차로 읽으면서 각 레코드를 `DISPLAY`로 표준 출력(SYSOUT)에 그대로 인쇄한다. 처리 결과를 별도 파일에 쓰거나 가공하지 않으며, 오류 발생 시에는 파일 상태 코드를 출력한 뒤 `CEE3ABD`를 호출해 강제 Abend(비정상 종료)로 빠져나간다.

용도는 CARDFILE의 내용을 배치 JCL 출력 로그에서 눈으로 확인하거나, 파일 무결성을 진단하는 것이다. 실질적인 데이터 변환이나 갱신은 없다.

---

## 입력 / 출력

- **입력**:
  - `CARDFILE` — VSAM KSDS. JCL의 `//CARDFILE DD` DD 명과 연결된다. RECORD KEY는 `FD-CARD-NUM`(PIC X(16), 카드 번호 16자리). ACCESS MODE IS SEQUENTIAL이므로 키 순서대로 전체 파일을 읽는다. (소스 29~33행)
- **출력**:
  - `SYSOUT` — 각 카드 레코드를 `DISPLAY CARD-RECORD`로 표준 출력에 인쇄한다. (소스 78행) 별도의 출력 파일은 없다.
  - 오류 시 — 파일 상태 코드를 `DISPLAY`한 뒤 Abend.

---

## 의존성

- **COPY (카피북)**:
  - `CVACT02Y` — `01 CARD-RECORD` 정의. CARD-NUM(X(16)), CARD-ACCT-ID(9(11)), CARD-CVV-CD(9(03)), CARD-EMBOSSED-NAME(X(50)), CARD-EXPIRAION-DATE(X(10)), CARD-ACTIVE-STATUS(X(01)), FILLER(X(59)) — 총 레코드 길이 150바이트. (소스 45행, `app/cpy/CVACT02Y.cpy`)
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CEE3ABD` — IBM Language Environment 런타임의 강제 Abend 루틴. `CALL 'CEE3ABD' USING ABCODE, TIMING`으로 호출. ABCODE=999, TIMING=0 고정값. (소스 158행)
- **데이터셋/파일/DB 테이블**:
  - `CARDFILE` — VSAM KSDS (카드 마스터). JCL DD명은 `CARDFILE`.
- **트랜잭션 ID 또는 EXEC PGM**:
  - JCL EXEC 문에서 `PGM=CBACT02C`로 직접 기동. CICS 트랜잭션 없음.

---

## 핵심 로직 흐름

```
PROCEDURE DIVISION
│
├─ DISPLAY 'START OF EXECUTION...'
│
├─ PERFORM 0000-CARDFILE-OPEN          ← CARDFILE을 INPUT 모드로 OPEN
│       OPEN INPUT CARDFILE-FILE
│       FILE STATUS '00' → APPL-RESULT = 0 (정상)
│       그 외 → 오류 출력 후 9999-ABEND-PROGRAM
│
├─ PERFORM UNTIL END-OF-FILE = 'Y'    ← 메인 처리 루프
│     IF END-OF-FILE = 'N'
│       PERFORM 1000-CARDFILE-GET-NEXT
│         READ CARDFILE-FILE INTO CARD-RECORD
│         STATUS '00' → APPL-RESULT = 0
│         STATUS '10' → APPL-RESULT = 16 (EOF 도달)
│         그 외 → APPL-RESULT = 12 (오류)
│         APPL-AOK(=0)  → CONTINUE
│         APPL-EOF(=16) → END-OF-FILE = 'Y' (루프 탈출)
│         그 외          → 9910-DISPLAY-IO-STATUS + 9999-ABEND-PROGRAM
│       IF END-OF-FILE = 'N'
│         DISPLAY CARD-RECORD          ← 레코드를 SYSOUT에 출력
│
├─ PERFORM 9000-CARDFILE-CLOSE         ← CARDFILE CLOSE
│       CLOSE CARDFILE-FILE
│       FILE STATUS '00' → APPL-RESULT = 0
│       그 외 → 오류 출력 후 9999-ABEND-PROGRAM
│
├─ DISPLAY 'END OF EXECUTION...'
│
└─ GOBACK                              ← 프로그램 정상 종료
```

### 파일 상태 코드 해석 (9910-DISPLAY-IO-STATUS, 소스 161~174행)

VSAM의 FILE STATUS는 두 바이트로 구성된다.

- 첫 번째 바이트(`IO-STAT1`)가 `'9'`이거나 전체가 숫자가 아닌 경우: 두 번째 바이트를 BINARY 정수로 변환해 4자리로 표시한다. 이는 VSAM의 Extended Status 코드(예: 9/044 = Duplicate Key)를 디코딩하기 위한 것이다.
- 그 외: 2바이트 그대로 4자리로 표시한다.

`TWO-BYTES-BINARY`와 `TWO-BYTES-ALPHA`(REDEFINES) 구조를 이용해 단일 바이트를 BINARY 정수로 변환하는 테크닉을 사용한다 (소스 53~56행).

### APPL-RESULT 레벨 88 조건명

| 값 | 조건명 | 의미 |
|---|---|---|
| 0 | `APPL-AOK` | 정상 |
| 16 | `APPL-EOF` | 파일 끝(EOF) |
| 8 | (없음) | OPEN/CLOSE 초기 오류 대기값 |
| 12 | (없음) | I/O 오류 |

---

## Java/현대화 노트

### 전체 구조 매핑

```java
// CBACT02C 전체를 Java로 표현하면 아래와 같다
public class CardFilePrinter {

    // WORKING-STORAGE의 플래그 변수들
    private boolean endOfFile = false;      // END-OF-FILE PIC X(01) VALUE 'N'
    private int applResult = 0;             // APPL-RESULT PIC S9(9) COMP
    private String cardfileStatus = "00";   // CARDFILE-STATUS

    public static void main(String[] args) {
        new CardFilePrinter().run();
    }

    public void run() {
        System.out.println("START OF EXECUTION OF PROGRAM CBACT02C");
        openCardFile();                          // 0000-CARDFILE-OPEN

        while (!endOfFile) {
            CardRecord rec = getNextCardRecord(); // 1000-CARDFILE-GET-NEXT
            if (!endOfFile) {
                System.out.println(rec);          // DISPLAY CARD-RECORD
            }
        }

        closeCardFile();                         // 9000-CARDFILE-CLOSE
        System.out.println("END OF EXECUTION OF PROGRAM CBACT02C");
    }
}
```

### 주요 COBOL → Java 변환 포인트

**1. VSAM KSDS 순차 접근**

COBOL의 `ACCESS MODE IS SEQUENTIAL`은 Java에서 `BufferedReader` 또는 VSAM 클라이언트 SDK의 순차 커서와 같다. 현대화 시에는 Amazon DynamoDB, RDS, 또는 파일 기반이라면 `java.nio.file.Files.lines()`로 대체한다.

**2. CARD-RECORD 구조체 (CVACT02Y)**

```java
// CARD-RECORD 150바이트 고정 레코드 → Java 레코드 클래스
public record CardRecord(
    String  cardNum,           // CARD-NUM          PIC X(16)   → String, 16자
    long    cardAcctId,        // CARD-ACCT-ID       PIC 9(11)   → long (최대 99,999,999,999)
    int     cardCvvCd,         // CARD-CVV-CD        PIC 9(03)   → int (0~999)
    String  cardEmbossedName,  // CARD-EMBOSSED-NAME PIC X(50)   → String, 50자 (공백 패딩)
    String  cardExpirationDate,// CARD-EXPIRAION-DATE PIC X(10)  → String, 10자 (주의: 오타 그대로)
    String  cardActiveStatus,  // CARD-ACTIVE-STATUS PIC X(01)   → String 또는 enum
    String  filler             // FILLER             PIC X(59)   → 무시
) {}
```

> **주의**: `CARD-EXPIRAION-DATE`는 소스에서 `EXPIRAION`(EXPIRATION 오타)으로 정의되어 있다. (CVACT02Y.cpy 9행) 현대화 시 필드명을 `cardExpirationDate`로 교정하되, 파싱 매핑에서 원본 오프셋을 그대로 유지해야 한다.

**3. REDEFINES 패턴 (소스 53~56행)**

```cobol
01  TWO-BYTES-BINARY  PIC 9(4) BINARY.
01  TWO-BYTES-ALPHA   REDEFINES TWO-BYTES-BINARY.
    05 TWO-BYTES-LEFT  PIC X.
    05 TWO-BYTES-RIGHT PIC X.
```

이 패턴은 메모리의 동일한 2바이트를 `short`(BINARY)와 `byte[2]`(ALPHA)로 동시에 해석한다. Java에는 `REDEFINES` 직접 대응이 없다. 동등 코드:

```java
// VSAM Extended Status 코드 디코딩
byte[] twoBytes = new byte[2];
twoBytes[0] = 0;                         // TWO-BYTES-LEFT = 0
twoBytes[1] = io_stat2_byte;             // TWO-BYTES-RIGHT = IO-STAT2
short binaryVal = ByteBuffer.wrap(twoBytes).getShort(); // TWO-BYTES-BINARY
```

**4. Abend 처리**

`CALL 'CEE3ABD'`는 JVM에서 `System.exit(999)` 또는 커스텀 `BatchAbendException`(unchecked)으로 대체한다. Spring Batch에서는 Step의 `ExitStatus.FAILED`로 매핑하고 Job을 FAILED 상태로 종료시킨다.

**5. DISPLAY 출력**

`DISPLAY CARD-RECORD`는 150바이트 전체를 EBCDIC(메인프레임)에서 그대로 출력한다. Java 현대화 시 주의점:
- 메인프레임 EBCDIC → ASCII 변환이 필요하다.
- `PIC X` 필드는 공백 패딩 포함이므로 `String.trim()` 또는 별도 포맷터 적용 필요.
- `PIC 9(11)` 등 DISPLAY 숫자 필드는 EBCDIC 존수(Zoned Decimal)로 저장되어 있어 직접 `System.out.println`으로는 깨진다.

**6. Spring Batch 매핑 (현대화 권장)**

| COBOL 단락 | Spring Batch 대응 |
|---|---|
| `0000-CARDFILE-OPEN` | `ItemReader.open()` |
| `1000-CARDFILE-GET-NEXT` + `DISPLAY` | `FlatFileItemReader` + `ItemWriter`(콘솔/로그) |
| `9000-CARDFILE-CLOSE` | `ItemReader.close()` |
| `9999-ABEND-PROGRAM` | `StepExecutionListener.afterStep()` + `ExitStatus.FAILED` |

이 프로그램은 로직이 없는 순수 순차 읽기/출력이므로, 현대화 목표가 CARDFILE 내용 확인이라면 배치 프로그램 대신 간단한 SQL `SELECT * FROM CARD` 또는 S3 객체 다운로드로 대체하는 것이 가장 효율적이다.
