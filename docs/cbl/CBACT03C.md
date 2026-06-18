# CBACT03C — 카드-계좌 교차참조 파일 순차 출력 배치

- **유형**: 배치 COBOL
- **한 줄 요약**: VSAM KSDS 카드-계좌 교차참조 파일(XREFFILE)을 처음부터 끝까지 순차 읽어 각 레코드를 DISPLAY(sysout)로 출력하는 단순 덤프 유틸리티.

---

## 기능 설명

CBACT03C는 CardDemo 카드-계좌 교차참조 VSAM 파일(`XREFFILE`)을 순차(Sequential) 방식으로 전체 스캔하여 레코드마다 표준 출력(JES SYSOUT)에 출력한다. 집계·변환·업데이트 로직은 전혀 없으며 파일 내용을 그대로 출력하는 진단/감사용 덤프 프로그램이다.

VSAM KSDS 파일이지만 `ACCESS MODE IS SEQUENTIAL`로 열어 순방향으로 전체 레코드를 읽는다는 점이 특징이다. 파일 열기·읽기·닫기 단계마다 `FILE STATUS`를 체크하고, 오류 발생 시 IBM LE 런타임 서비스 `CEE3ABD`를 CALL하여 강제 ABEND(코드 999)시킨다.

> 주목: `1000-XREFFILE-GET-NEXT` 단락에 `DISPLAY CARD-XREF-RECORD`가 두 번 나타난다(라인 96, 이후 메인 루프 라인 78). 즉 `STATUS='00'`인 레코드는 실제로 두 번 출력된다. 이는 원본 코드의 버그 또는 의도적 중복이며, 현대화 시 반드시 단일 출력으로 정리해야 한다.

---

## 입력 / 출력

- **입력**:
  - `XREFFILE` — VSAM KSDS, 레코드 길이 50바이트. DD명 `XREFFILE`. 프라이머리 키: 카드 번호(`XREF-CARD-NUM`, 16바이트).
- **출력**:
  - JES SYSOUT(표준 출력) — 각 `CARD-XREF-RECORD`를 `DISPLAY`로 출력. 파일 쓰기 없음.

---

## 의존성

- **COPY (카피북)**:
  - `CVACT03Y` — `CARD-XREF-RECORD` 01레벨 레이아웃 정의 (레코드 길이 50바이트).

- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CEE3ABD` — IBM Language Environment 비정상 종료 서비스. `ABCODE=999`, `TIMING=0`으로 CALL(라인 158). Java 대응: `throw new RuntimeException("ABEND 999")`에 해당.

- **데이터셋/파일/DB 테이블**:
  - `XREFFILE` (VSAM KSDS) — JCL DD명 `XREFFILE`로 연결.

- **트랜잭션 ID 또는 EXEC PGM**:
  - JCL `EXEC PGM=CBACT03C`로 실행. CICS 트랜잭션 없음.

---

## 핵심 로직 흐름

```
PROCEDURE DIVISION
│
├─ DISPLAY 'START OF EXECUTION OF PROGRAM CBACT03C'
│
├─ PERFORM 0000-XREFFILE-OPEN          ← VSAM 파일 OPEN INPUT
│    OPEN INPUT XREFFILE-FILE
│    STATUS='00' → APPL-RESULT=0 (APPL-AOK)
│    그 외          → APPL-RESULT=12 → 9910-DISPLAY-IO-STATUS → 9999-ABEND-PROGRAM
│
├─ PERFORM UNTIL END-OF-FILE = 'Y'     ← EOF 플래그 루프
│    IF END-OF-FILE = 'N'
│      PERFORM 1000-XREFFILE-GET-NEXT
│        READ XREFFILE-FILE INTO CARD-XREF-RECORD
│        STATUS='00' → APPL-RESULT=0, DISPLAY CARD-XREF-RECORD  ← (1차 출력)
│        STATUS='10' → APPL-RESULT=16 (APPL-EOF)
│        그 외        → APPL-RESULT=12 → ABEND
│        APPL-EOF    → END-OF-FILE='Y'
│      IF END-OF-FILE = 'N'
│        DISPLAY CARD-XREF-RECORD                               ← (2차 출력, 버그)
│    END-IF
│
├─ PERFORM 9000-XREFFILE-CLOSE         ← CLOSE XREFFILE-FILE
│    STATUS='00' → 정상 종료
│    그 외        → ABEND
│
├─ DISPLAY 'END OF EXECUTION OF PROGRAM CBACT03C'
└─ GOBACK
```

### 단락별 설명

| 단락 | 역할 |
|---|---|
| `0000-XREFFILE-OPEN` | `OPEN INPUT` 실행. STATUS 확인 후 오류 시 ABEND. |
| `1000-XREFFILE-GET-NEXT` | `READ ... INTO CARD-XREF-RECORD`. STATUS `'00'`=정상, `'10'`=EOF, 그 외=오류. |
| `9000-XREFFILE-CLOSE` | `CLOSE` 실행. STATUS 확인 후 오류 시 ABEND. 산술 관용구(`ADD 8 TO ZERO`, `SUBTRACT … FROM …`)로 APPL-RESULT를 초기화/검증하는 레거시 스타일. |
| `9910-DISPLAY-IO-STATUS` | 2바이트 FILE STATUS를 사람이 읽기 쉬운 4자리로 변환 출력. STATUS 첫 자리가 `'9'`이거나 비숫자면 두 번째 바이트를 이진수(`TWO-BYTES-BINARY`)로 재해석한다. |
| `9999-ABEND-PROGRAM` | `CEE3ABD` CALL로 배치 ABEND. |

### FILE STATUS 코드 해석

| STATUS 값 | 의미 |
|---|---|
| `'00'` | 정상 |
| `'10'` | EOF (End-of-File) |
| `'9x'` | 파일시스템 레벨 오류 (x는 VSAM/OS 이진 서브코드) |
| 그 외 | 일반 I/O 오류 |

### REDEFINES 구조 (`TWO-BYTES-BINARY` / `TWO-BYTES-ALPHA`)

```cobol
01  TWO-BYTES-BINARY   PIC 9(4) BINARY.
01  TWO-BYTES-ALPHA    REDEFINES TWO-BYTES-BINARY.
    05  TWO-BYTES-LEFT  PIC X.
    05  TWO-BYTES-RIGHT PIC X.
```

같은 2바이트 메모리를 정수(BINARY)와 문자(ALPHA) 두 가지 뷰로 접근하는 C의 `union`과 동일한 구조다. `9910-DISPLAY-IO-STATUS`에서 STATUS 두 번째 바이트를 `TWO-BYTES-RIGHT`에 넣고 `TWO-BYTES-BINARY` 값을 숫자로 읽어 VSAM 서브코드를 정수로 출력한다.

---

## Java/현대화 노트

### 1. 전체 구조 — Java 대응

```java
// CBACT03C 전체 골격에 대응하는 Java 의사코드
public class CardXrefDump {

    public static void main(String[] args) throws IOException {
        System.out.println("START OF EXECUTION OF PROGRAM CBACT03C");
        try (var reader = openXrefFile()) {          // 0000-XREFFILE-OPEN
            CardXrefRecord rec;
            while ((rec = reader.readNext()) != null) { // 1000-XREFFILE-GET-NEXT
                System.out.println(rec);               // DISPLAY
            }
        }                                             // 9000-XREFFILE-CLOSE (try-with-resources)
        System.out.println("END OF EXECUTION OF PROGRAM CBACT03C");
    }
}
```

### 2. CVACT03Y 레코드 레이아웃 → Java DTO

```cobol
01 CARD-XREF-RECORD.
   05  XREF-CARD-NUM   PIC X(16).   -- 카드번호 (16자리 고정길이 문자)
   05  XREF-CUST-ID    PIC 9(09).   -- 고객ID (9자리 정수)
   05  XREF-ACCT-ID    PIC 9(11).   -- 계좌ID (11자리 정수)
   05  FILLER          PIC X(14).   -- 패딩 (14바이트)
```

```java
public record CardXrefRecord(
    String cardNum,   // PIC X(16)  → String, 항상 16바이트 고정 (EBCDIC→ASCII 변환 필요)
    long   custId,    // PIC 9(09)  → long (최대 9자리 십진수)
    long   acctId     // PIC 9(11)  → long (최대 11자리 십진수, int 범위 초과)
    // FILLER 14바이트는 파싱 시 skip
) {}
```

> `XREF-CUST-ID PIC 9(09)`는 최대 999,999,999이므로 `int`(최대 약 21억)에 들어가지만, `XREF-ACCT-ID PIC 9(11)`은 최대 99,999,999,999으로 `int` 범위를 초과하므로 반드시 `long`을 사용해야 한다.

### 3. VSAM KSDS 순차 읽기 → Java

VSAM KSDS를 Java에서 대체할 때 일반적인 선택지:

| VSAM 역할 | Java/현대 대응 |
|---|---|
| KSDS 순차 읽기 | `BufferedReader` (고정 레코드) 또는 JPA `findAll()` |
| 키 기반 RANDOM 접근 | `HashMap<String, CardXrefRecord>` 또는 RDB 인덱스 |
| 레코드 고정 길이 50바이트 | `RandomAccessFile.read(byte[50])` 또는 `DataInputStream` |

### 4. DISPLAY 중복 출력 버그

라인 96(`1000-XREFFILE-GET-NEXT` 내부)과 라인 78(메인 루프)에서 `DISPLAY CARD-XREF-RECORD`가 중복 실행된다. STATUS `'00'`인 모든 레코드가 두 번 출력된다. Java 변환 시 단일 `System.out.println(rec)` 하나만 유지한다.

### 5. ABEND 패턴 → 예외 처리

```cobol
CALL 'CEE3ABD' USING ABCODE, TIMING.  -- OS 레벨 강제 종료, RC=999
```

```java
// Java 대응: 복구 불가 오류는 checked exception 대신 RuntimeException
throw new UncheckedIOException("XREFFILE I/O error, status=" + status, e);
// 또는 배치 프레임워크(Spring Batch)에서 ExitStatus.FAILED로 종료
```

### 6. 9910-DISPLAY-IO-STATUS의 REDEFINES 트릭

VSAM의 STATUS 두 번째 바이트가 `'9'`로 시작하는 경우 이진 서브코드이므로 문자 그대로 해석하면 안 된다. Java에서는:

```java
byte statusByte2 = rawStatus[1];
int vsam_subcode = Byte.toUnsignedInt(statusByte2); // REDEFINES+BINARY 해석과 동일
```

### 7. `GOBACK` vs `STOP RUN`

`GOBACK`은 호출 계층을 고려한 종료로, 메인 프로그램에서는 `STOP RUN`과 동일하게 동작하고 서브루틴에서는 호출자로 복귀한다. Java의 `return`(서브루틴) 또는 `System.exit(0)`(메인)에 해당한다.

### 8. 9000-XREFFILE-CLOSE의 산술 관용구

```cobol
ADD 8 TO ZERO GIVING APPL-RESULT.          -- MOVE 8 TO APPL-RESULT와 동일
SUBTRACT APPL-RESULT FROM APPL-RESULT.     -- MOVE 0 TO APPL-RESULT와 동일
ADD 12 TO ZERO GIVING APPL-RESULT.         -- MOVE 12 TO APPL-RESULT와 동일
```

오래된 COBOL 관용 표현으로, 기능은 단순한 MOVE와 완전히 동일하다. Java 변환 시 그냥 정수 대입으로 처리한다.
